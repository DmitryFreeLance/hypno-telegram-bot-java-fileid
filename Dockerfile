# Multi-stage build: Maven -> slim JRE
FROM maven:3.9.8-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

# Where DB and assets are expected (you can mount volumes here)
VOLUME ["/data", "/assets"]

ENV DB_PATH=/data/bot.db

COPY --from=build /build/target/hypno-telegram-bot-1.0.0-shaded.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
