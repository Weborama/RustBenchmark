FROM maven:3.8.6-eclipse-temurin-17 as builder

WORKDIR /app

# Get dependencies
COPY pom.xml /app

RUN mvn dependency:go-offline

# Build the stuff
COPY src/ /app/src/
COPY compiler-config.groovy /app/compiler-config.groovy
RUN mvn package

FROM eclipse-temurin:17.0.3_7-jre
LABEL maintainer="Nelson <nfonkoua@weborama.com>"

COPY --from=builder /app/target/groovy-demo-0.1-jar-with-dependencies.jar /groovy-demo.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XshowSettings:vm", "-jar", "/groovy-demo.jar"]