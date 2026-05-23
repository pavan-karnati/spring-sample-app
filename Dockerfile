# ---- Build stage ----
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Cache Maven dependencies separately from source code
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

# Build the jar
COPY src ./src
RUN ./mvnw clean package -DskipTests -q

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Run as non-root user
RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar

USER spring:spring

EXPOSE 8080

# Respect container memory limits and allow runtime JVM tuning via JAVA_OPTS
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
