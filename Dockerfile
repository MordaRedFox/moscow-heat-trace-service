# Stage 1: сборка
FROM maven:3.8-openjdk-11 AS build

WORKDIR /build

COPY pom.xml .

RUN mvn -B dependency:go-offline

COPY src ./src

RUN mvn -B clean package -DskipTests

# Stage 2: runtime
FROM eclipse-temurin:11-jre-jammy

WORKDIR /app

# Директория для временных файлов загрузок до 3 ГБ. В docker-compose к этой
# точке монтируется том heat_uploads
RUN mkdir -p /var/tmp/heat && chmod 777 /var/tmp/heat

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.io.tmpdir=/var/tmp/heat", \
            "-jar", "/app/app.jar"]
