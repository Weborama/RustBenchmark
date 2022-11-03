(ns clojure-demo.handler
  (:require [compojure.core :refer [defroutes POST]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [compojure.coercions :refer [as-int]]
            [ring.middleware.json :as jsonm]
            [hikari-cp.core :refer [make-datasource]]
            [clojure.java.jdbc :as jdbc]
            [cheshire.core :refer [generate-string]]
            [langohr.core :as rmq]
            [langohr.channel :as lch]
            [langohr.basic :as lb]
            [environ.core :refer [env]]
            [org.httpkit.server :refer [run-server]])
  (:import (ove.crypto.digest Blake2b$Digest))
  (:gen-class))

(defonce conn (delay (rmq/connect {:host (or (env :rmq-host) "localhost")
                                   :port (or (env :rmq-port) 5672)
                                   :username (or (env :rmq-username) "guest")
                                   :password (or (env :rmq-password) "guest")
                                   :vhost (or (env :rmq-vhost) "/")})))
(defonce exchange (or (env :rmq-exchange) ""))
(defonce topic (or (env :rmq-topic) "rust"))

(defonce datasource (delay (make-datasource
                            {:jdbc-url (or (env :jdbc-url)
                                           "jdbc:postgresql://localhost/rust?user=rust&password=rust")})))

(defonce blake (Blake2b$Digest/newInstance))

(defn -channel []
  (reify java.util.function.Supplier
    (get [_] (lch/open @conn))))

(def rmqchannel (ThreadLocal/withInitial (-channel)))

(defn -get-name [id]
  (jdbc/with-db-connection [conn {:datasource @datasource}]
    (let [rows (jdbc/query conn ["SELECT name FROM clients WHERE id = ?" id])]
      (if rows
        (:name (first rows))
        nil))))

(defn -hash-data [string]
  (->> string
      (.getBytes)
      (.digest blake)))

(defn -publish [message]
  (let [channel (.get rmqchannel)]
    (lb/publish channel exchange topic (generate-string message) {:content-type "application/json"})))

(defroutes app-routes
  (POST "/hash" [id :<< as-int data]
    (if (every? some? [id data])
      (let [hashed (-hash-data data)
            name (-get-name id)
            message {:id id :hash hashed :name name}]
        (-publish message)
        {:body message})
      {:status 400 :body {:error "id and data must be set"}}))
  (route/not-found "Not Found"))

(def app-handler (handler/api app-routes))

(def app
  (-> app-handler
      jsonm/wrap-json-body
      jsonm/wrap-json-params
      jsonm/wrap-json-response))

(defn -main [& _]
  (run-server app {:port (Integer/valueOf (or (System/getenv "PORT") "3000"))}))
