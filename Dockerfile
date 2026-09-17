# ─── Build Stage ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache dependencies separately (speeds up rebuilds)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build fat JAR (assembly plugin bundles all deps)
COPY src ./src
RUN mvn package -DskipTests -q

# ─── Run Stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the assembled fat JAR from builder
COPY --from=builder /app/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
