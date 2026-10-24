# ---- Build stage: compile and package the jar ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy the build files first so dependencies are cached between builds
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in the CI pipeline's Test stage, so skip them here
RUN ./mvnw -B -q package -DskipTests

# ---- Runtime stage: small JRE-only image ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Version shown by /health; the pipeline passes the build version in
ARG APP_VERSION=1.0.0
ENV APP_VERSION=${APP_VERSION}

# Apply OS security patches released after the base image was built,
# then run as a non-root user
RUN apk upgrade --no-cache \
 && addgroup -S finsecure && adduser -S finsecure -G finsecure
COPY --from=build --chown=finsecure:finsecure /app/target/finsecure-pay.jar app.jar
USER finsecure

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
