# =========================================================
# VoyageCraft - Multi-Stage Production Dockerfile
# Java 21 & Spring Boot 3.3.4
# =========================================================

# --- Stage 1: Build stage ---
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and pom.xml first to leverage Docker layer caching
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Download dependencies offline
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy project source code
COPY src ./src

# Build production jar skipping tests (tests executed in CI pipeline)
RUN ./mvnw clean package -DskipTests -B

# Extract application layers for optimal container caching (Spring Boot layered jar)
WORKDIR /build/target
RUN java -Djarmode=tools -jar trip-planner-1.0.0.jar extract --layers --launcher || cp trip-planner-1.0.0.jar /build/target/app.jar

# --- Stage 2: Production Runtime stage ---
FROM eclipse-temurin:21-jre-alpine AS runner

# Install curl for container health checks
RUN apk add --no-cache curl tzdata

# Create dedicated non-root application group and user
RUN addgroup -g 10001 -S appgroup && \
    adduser -u 10001 -S appuser -G appgroup

WORKDIR /app

# Copy built artifact from builder stage
COPY --from=builder /build/target/trip-planner-1.0.0.jar ./app.jar

# Set ownership to non-root user
RUN chown -R appuser:appgroup /app

# Switch to unprivileged non-root user
USER appuser

# Expose HTTP port
EXPOSE 8080

# Configure production environment defaults
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -Djava.security.egd=file:/dev/./urandom"

# Healthcheck probe (checks root or health endpoint)
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -f http://localhost:8080/ || exit 1

# Launch Spring Boot application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
