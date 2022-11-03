FROM clojure:temurin-17-lein-jammy
LABEL maintainer="Fabrice Gabolde <fgabolde@weborama.com>"

RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
COPY project.clj /usr/src/app/
RUN lein deps
COPY . /usr/src/app
RUN lein uberjar && mv /usr/src/app/target/clojure-demo-*-standalone.jar /app-standalone.jar

FROM eclipse-temurin:17
LABEL maintainer="Fabrice Gabolde <fgabolde@weborama.com>"

COPY --from=0 /app-standalone.jar /app-standalone.jar
ENTRYPOINT ["java", "-jar", "/app-standalone.jar"]
