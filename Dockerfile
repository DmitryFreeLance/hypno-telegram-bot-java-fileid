# Multi-stage build: Maven -> slim JRE
FROM maven:3.9.8-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline

COPY src ./src

# Build and normalize jar name
RUN mvn -q -DskipTests package && \
    echo "Built artifacts in target/:" && ls -la target && \
    JAR="$(ls -1 target/*.jar | grep -vE '(^|/)(original-|.*-sources\\.jar$|.*-javadoc\\.jar$)' | head -n 1)" && \
    echo "Using jar: ${JAR}" && \
    cp "${JAR}" /build/app.jar

FROM eclipse-temurin:17-jre
WORKDIR /app

VOLUME ["/data", "/assets"]
ENV DB_PATH=/data/bot.db

COPY --from=build /build/app.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]