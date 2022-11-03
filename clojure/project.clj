(defproject clojure-demo "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [com.taoensso/timbre "5.2.1"]
                 [com.fzakaria/slf4j-timbre "0.3.21"]
                 [environ "1.2.0"]
                 [compojure "1.6.1"]
                 [ring/ring-core "1.9.5"]
                 [ring/ring-defaults "0.3.2"]
                 [ring/ring-json "0.5.1"]
                 [http-kit "2.3.0"]
                 [org.clojure/java.jdbc "0.7.12"]
                 [org.postgresql/postgresql "42.4.0"]
                 [hikari-cp "2.14.0"]
                 [com.novemberain/langohr "5.4.0"]
                 [com.github.alphazero/Blake2b "bbf094983c"]]
  :repositories [["scijava" "https://maven.scijava.org/content/repositories/public/"]]
  :plugins [[lein-ring "0.12.5"]]
  :ring {:handler clojure-demo.handler/app}
  :aot :all
  :main clojure-demo.handler
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.2"]]}})
