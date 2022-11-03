package main

import (
	"database/sql"
	"encoding/base64"
	"encoding/json"
	_ "expvar"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"strconv"
	"time"

	"github.com/eapache/go-resiliency/batcher"
	_ "github.com/lib/pq"
	amqp "github.com/rabbitmq/amqp091-go"
	"golang.org/x/crypto/blake2b"
)

const (
	// DefaultReadTimeout constant.
	DefaultReadTimeout = 10 * time.Second
	// DefaultReadHeaderTimeout constant.
	DefaultReadHeaderTimeout = 10 * time.Second
	// DefaultWriteTimeout constant.
	DefaultWriteTimeout = 10 * time.Second
	// selectClientSQL query.
	selectClientSQL = `SELECT name FROM clients WHERE id = $1`
)

// Batcher interface.
type Batcher interface {
	Run(param any) error
}

// Input structure.
// nolint: govet // fieldalignment: struct with 16 pointer bytes could be 8 (govet)
type Input struct {
	ID   int
	Data string
}

// Output structure.
type Output struct {
	Name string
	Hash string
}

func handleError(resp http.ResponseWriter, err error, code int) {
	http.Error(resp, err.Error(), code)
	log.Print(err)
}

func getHashHandler(
	sqlDB *sql.DB,
	batcher Batcher,
) func(resp http.ResponseWriter, req *http.Request) {
	return func(resp http.ResponseWriter, req *http.Request) {
		// Input structure
		var input Input
		// Decode the request body into the input structure
		if err := json.NewDecoder(req.Body).Decode(&input); err != nil {
			handleError(resp, fmt.Errorf("json.NewDecoder(req.Body).Decode(&input): %w", err), http.StatusInternalServerError)

			return
		}
		// Make a channel to receive blake2b hash
		hashChan := make(chan string, 1)
		// Fire a goroutine to calculate blake2b hash
		go func(input string) {
			hashBytes := blake2b.Sum512([]byte(input))
			hashChan <- base64.StdEncoding.EncodeToString(hashBytes[:])
		}(input.Data)
		// Output structure
		var output Output
		// Look for id in database
		if err := sqlDB.QueryRowContext(req.Context(), selectClientSQL, input.ID).Scan(&output.Name); err != nil {
			handleError(resp, fmt.Errorf(
				"sqlDB.QueryRowContext(req.Context(), %q, %d).Scan(&output.Name): %w", selectClientSQL, input.ID, err,
			), http.StatusInternalServerError)

			return
		}
		// Retrieve the blake2b hash result
		output.Hash = <-hashChan
		// Set headers
		resp.Header().Set("Content-Type", "application/json")
		resp.Header().Set("Content-Encoding", "utf-8")
		// Encode JSON into HTTP response
		if err := json.NewEncoder(resp).Encode(output); err != nil {
			handleError(resp, fmt.Errorf("json.NewEncoder(resp).Encode(output): %w", err), http.StatusInternalServerError)
		}
		// Push to AMQP
		go func() {
			if err := batcher.Run(output); err != nil {
				log.Print(fmt.Errorf("batcher.Run(&output): %w", err))
			}
		}()
	}
}

func getenvDuration(key string) time.Duration {
	value := os.Getenv(key)
	if value == "" {
		return 0
	}

	duration, err := time.ParseDuration(value)
	if err != nil {
		log.Fatal(fmt.Errorf("time.ParseDuration(%q): %w", value, err))
	}

	return duration
}

func getenvInt(key string) int {
	value := os.Getenv(key)
	if value == "" {
		return 0
	}

	i, err := strconv.Atoi(value)
	if err != nil {
		log.Fatal(fmt.Errorf("strconv.Atoi(%q): %w", value, err))
	}

	return i
}

func openSQLDB() *sql.DB {
	sqlDriverName := os.Getenv("SQL_DRIVERNAME")
	sqlDataSourceName := os.Getenv("SQL_DATASOURCENAME")
	sqlConnMaxIdleTime := getenvDuration("SQL_CONNMAXIDLETIME")
	sqlConnMaxLifetime := getenvDuration("SQL_CONNMAXLIFETIME")
	sqlMaxIdleConns := getenvInt("SQL_MAXIDLECONNS")
	sqlMaxOpenConns := getenvInt("SQL_MAXOPENCONNS")
	// Connect to database
	sqlDB, err := sql.Open(sqlDriverName, sqlDataSourceName)
	if err != nil {
		log.Fatal(fmt.Errorf("sql.Open(%q, %q): %w", sqlDriverName, sqlDataSourceName, err))
	}

	sqlDB.SetConnMaxIdleTime(sqlConnMaxIdleTime)
	sqlDB.SetConnMaxLifetime(sqlConnMaxLifetime)
	sqlDB.SetMaxIdleConns(sqlMaxIdleConns)
	sqlDB.SetMaxOpenConns(sqlMaxOpenConns)
	// Check database connection
	if err := sqlDB.Ping(); err != nil {
		log.Fatal(fmt.Errorf("sqlDB.Ping(): %w", err))
	}

	return sqlDB
}

func openAMQP() *amqp.Channel {
	amqpURI := os.Getenv("AMQP_URI")

	amqpConnection, err := amqp.Dial(amqpURI)
	if err != nil {
		log.Fatal(fmt.Errorf("amqp.Dial(%q): %w", amqpURI, err))
	}

	amqpChannel, err := amqpConnection.Channel()
	if err != nil {
		log.Fatal(fmt.Errorf("amqpConnection.Channel(): %w", err))
	}

	return amqpChannel
}

func main() {
	var verbose bool

	flag.BoolVar(&verbose, "v", false, "verbose")
	flag.Parse()

	sqlDB := openSQLDB()

	amqpChannel := openAMQP()

	// nolint: exhaustruct
	server := http.Server{
		Addr:              ":3000",
		Handler:           http.DefaultServeMux,
		ReadTimeout:       DefaultReadTimeout,
		ReadHeaderTimeout: DefaultReadHeaderTimeout,
		WriteTimeout:      DefaultWriteTimeout,
	}

	batcherTimeout := getenvDuration("AMQP_BATCHERTIMEOUT")
	batcher := batcher.New(batcherTimeout, func(outputBatch []interface{}) error {
		if verbose {
			log.Printf("Publishing batch of %d messages.", len(outputBatch))
		}

		outputBytes, err := json.Marshal(outputBatch)
		if err != nil {
			return fmt.Errorf("json.Marshal(outputBatch): %w", err)
		}
		// Push to AMQP
		if err = amqpChannel.Publish(
			"toRustDemo", // exchange
			"rust",       // routing key
			false,        // mandatory
			false,        // immediate
			amqp.Publishing{ContentType: "application/json", Body: outputBytes}, //nolint: exhaustruct
		); err != nil {
			log.Fatal(fmt.Errorf("amqpChannel.Publish(...): %w", err))
		}

		return nil
	})

	http.HandleFunc("/hash", getHashHandler(sqlDB, batcher))

	log.Print("Listening on ", server.Addr)
	log.Fatal(server.ListenAndServe())
}
