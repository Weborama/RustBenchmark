use axum::http::StatusCode;

use axum::Extension;

use std::str::FromStr;
use std::sync::Arc;

use axum::{routing::post, Json, Router};
use blake2b_simd::blake2b;
use deadpool::unmanaged::Pool as MyPool;
use lapin::options::BasicPublishOptions;
use lapin::BasicProperties;
use lapin::Channel;
use lapin::{Connection, ConnectionProperties};
use serde_json::json;
use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;
use std::net::SocketAddr;
use base64;
#[derive(Debug, serde::Deserialize)]
struct InputData {
    id: i32,
    data: String,
}

/// This endpoint will:
///  * Compute the blake2 hash of the body's `data` field
///  * Find by id the name of a fictional client in the database
///  * Build a json message with the hash and the client's name
///  * Publish a message on rabbitmq
///  * Return the message in the HTTP response
async fn hash(
    Json(data): Json<InputData>,
    Extension(state): Extension<Arc<State>>,
) -> (StatusCode, String) {
    let hash = blake2b(data.data.as_bytes());

    let r = match sqlx::query!("SELECT name FROM clients WHERE id = $1", data.id)
        .fetch_one(&state.pg_pool)
        .await
    {
        Err(_) => return (StatusCode::NOT_FOUND, "".to_owned()),
        Ok(v) => v,
    };

    let message = json!({"name": r.name, "hash": base64::encode(hash.as_bytes())});

    state
        .rmq_pool
        .get()
        .await
        .unwrap()
        .basic_publish(
            "toRustDemo",
            "rust",
            BasicPublishOptions::default(),
            message.to_string().as_bytes(),
            BasicProperties::default(),
        )
        .await
        .unwrap()
        .await
        .unwrap();

    (StatusCode::OK, message.to_string())
}

/// Create a pool of Postgres connection
async fn create_postgres_pool() -> PgPool {
    let url = std::env::var("DEMO_POSTGRES")
        .unwrap_or_else(|_| "postgres://rust:rust@localhost/rust".to_string());
    PgPoolOptions::new()
        .max_connections(50)
        .test_before_acquire(false)
        .min_connections(50)
        .connect(&url)
        .await
        .expect("Could not create DB pool")
}

/// Create a dumb pool of RabbitMQ channels
async fn create_channels_pool() -> MyPool<Channel> {
    let url = std::env::var("DEMO_RABBITMQ")
        .unwrap_or_else(|_| String::from_str("amqp://guest:guest@127.0.0.1:5672/%2f").unwrap());
    let pool_size = num_cpus::get() * 2;
    let pool = MyPool::new(pool_size);
    let conn = Connection::connect(
        &url,
        ConnectionProperties::default().with_connection_name("rust".into()),
    )
    .await
    .unwrap();
    for _ in 0..pool_size {
        pool.add(conn.create_channel().await.expect("Can't create channel"))
            .await
            .expect("Can't add the channel to the pool");
    }

    pool
}

#[derive(Clone)]
struct State {
    pg_pool: PgPool,
    rmq_pool: MyPool<Channel>,
}

#[tokio::main]
async fn main() {
    let pool = create_postgres_pool().await;
    let channels = create_channels_pool().await;
    let state = State {
        pg_pool: pool,
        rmq_pool: channels,
    };

    let state = Arc::new(state);
    // build our application with a route
    let app = Router::new()
        .route("/hash", post(hash))
        .layer(Extension(state));

    let addr = SocketAddr::from(([0, 0, 0, 0], 8080));

    println!("All right let's go!");
    axum::Server::bind(&addr)
        .serve(app.into_make_service())
        .await
        .unwrap();

}
