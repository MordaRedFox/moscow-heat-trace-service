# ===== Stage 1: сборка =====
FROM maven:3.8-openjdk-11 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
# Тесты в образе не гоняем: им нужен Testcontainers (Docker-in-Docker)
RUN mvn -B clean package -DskipTests

# ===== Stage 2: runtime =====
FROM openjdk:11-jre-slim
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]