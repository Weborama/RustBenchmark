FROM maven:3-eclipse-temurin-17 as builder

WORKDIR /app

# Get dependencies
COPY pom.xml /app
RUN mvn dependency:go-offline


# Build the stuff
COPY . .
RUN mvn package

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=builder /app/target/*.jar /app/app.jar
RUN addgroup java-demo && useradd -g java-demo java-demo
USER java-demo
EXPOSE 8080

ENTRYPOINT ["java","-jar", "/app/app.jar"]
