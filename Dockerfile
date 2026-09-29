# Stage 1: сборка
# - Maven 3.9.9 — совпадает с версией в devcontainer (post-create.sh)
#   Maven 3.8 ломается на spring-boot-starter-parent 2.6.3 при
#   dependency:go-offline
# - Базовый образ — Temurin 11 (Ubuntu Jammy 22.04), JDK для компиляции

FROM maven:3.9.9-eclipse-temurin-11 AS build

WORKDIR /build

COPY pom.xml .

RUN mvn -B -q dependency:go-offline

COPY src ./src

RUN mvn -B -q clean package -DskipTests

# Stage 2: runtime
# - eclipse-temurin:11-jre-jammy = Java 11 JRE на Ubuntu 22.04
# - Устанавливаем curl для HEALTHCHECK (jammy minimal его не содержит)
# - Каталог /var/tmp/heat — точка монтирования тома из docker-compose,
#   туда же указывает -Djava.io.tmpdir, поэтому multipart-временные файлы
#   и heat.upload.temp-dir пишутся на volume, а не в эфемерный /tmp

FROM eclipse-temurin:11-jre-jammy

ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    TZ=Europe/Moscow

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tzdata \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN mkdir -p /var/tmp/heat && chmod 777 /var/tmp/heat

COPY --from=build /build/target/*.jar /app/app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=60.0 \
-XX:+ExitOnOutOfMemoryError \
-Djava.io.tmpdir=/var/tmp/heat \
-Dfile.encoding=UTF-8 \
-Duser.timezone=Europe/Moscow"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:8080/api/health || exit 1
