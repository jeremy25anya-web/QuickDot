# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -B -DskipTests package && cp target/*.jar app.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --no-create-home appuser
# Metadata audit log (logback-spring.xml, AUDIT_LOG_DIR default "logs") is
# written relative to /app, which is root-owned — pre-create the directory
# for the non-root user. Container filesystem is ephemeral; that's fine for
# 30-day metadata and nothing content-bearing lands here (spec §6).
RUN mkdir -p /app/logs && chown appuser /app/logs
COPY --from=build /app/app.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
