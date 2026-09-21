# moscow-heat-trace-service

## Структура проекта на данный момент
```bash
moscow-heat-trace-service/
 ├── .devcontainer/
 │   ├── devcontainer.json                     # параметры devcontainer: env, расширения, порты
 │   ├── docker-compose.yml                    # сервисы dev-окружения: app, postgres, opensearch
 │   ├── post-create.sh                        # разовая настройка контейнера (maven, m2, docker.sock)
 │   └── post-start.sh                         # настройка при каждом старте (maven default, docker.sock)
 │
 ├── src/
 │   ├── main/
 │   │   ├── java/ru/moscow/heat/
 │   │   │   ├── geojson/
 │   │   │   │   ├── config/
 │   │   │   │   │   ├── AsyncConfig.java                    # пул потоков для @Async-парсинга и cron-очистки
 │   │   │   │   │   └── SpatialIndexInitializer.java        # создаёт GIST-индексы на geom/geom_utm после старта
 │   │   │   │   ├── controller/
 │   │   │   │   │   └── GeoJsonUploadController.java        # REST API: POST /upload, GET /uploads/{id}
 │   │   │   │   ├── dto/
 │   │   │   │   │   ├── GeoJsonUploadResponse.java          # накопитель результатов парсинга: счётчики, bbox, ошибки
 │   │   │   │   │   ├── UploadAcceptedResponse.java         # ответ 202: uploadId, статус, URL опроса
 │   │   │   │   │   ├── UploadStatusResponse.java           # публичный ответ GET статуса загрузки
 │   │   │   │   │   └── UploadSummary.java                  # компактная сводка в jsonb-колонке upload_session
 │   │   │   │   ├── entity/
 │   │   │   │   │   ├── AbstractGeoObject.java              # базовый класс типизированных сущностей: id, upload_id, geom, properties
 │   │   │   │   │   ├── GeoFeature.java                     # сырая фича: JSONB + PostGIS geom (4326) + geom_utm (32637)
 │   │   │   │   │   ├── HeatChamberEntity.java              # существующая тепловая камера
 │   │   │   │   │   ├── HeatNetworkEntity.java              # существующий участок сети: diameter
 │   │   │   │   │   ├── OksConnectionPointEntity.java       # точка подключения ОКС: flow_tph
 │   │   │   │   │   ├── RestrictionEntity.java              # пространственное ограничение: restriction_type
 │   │   │   │   │   ├── SourceEntity.java                   # источник теплоснабжения
 │   │   │   │   │   └── UploadSession.java                  # сессия загрузки: статус, тайминги, сводка
 │   │   │   │   ├── exception/
 │   │   │   │   │   ├── GeoJsonParseException.java          # ошибка парсинга → HTTP 400
 │   │   │   │   │   └── UploadNotFoundException.java        # загрузка не найдена → HTTP 404
 │   │   │   │   ├── repository/
 │   │   │   │   │   ├── GeoFeatureRepository.java           # сырые фичи + пространственные запросы (bbox, intersects, within)
 │   │   │   │   │   ├── HeatChamberRepository.java          # тепловые камеры
 │   │   │   │   │   ├── HeatNetworkRepository.java          # участки сети
 │   │   │   │   │   ├── OksConnectionPointRepository.java   # точки подключения ОКС
 │   │   │   │   │   ├── RestrictionRepository.java          # пространственные ограничения
 │   │   │   │   │   ├── SourceRepository.java               # источники теплоснабжения
 │   │   │   │   │   ├── UploadAwareRepository.java          # базовый интерфейс: findByUploadId / deleteByUploadId
 │   │   │   │   │   └── UploadSessionRepository.java        # поиск старых сессий для очистки
 │   │   │   │   ├── service/
 │   │   │   │   │   ├── CoordinateTransformService.java     # трансформация EPSG:4326 ↔ EPSG:32637 через Proj4J
 │   │   │   │   │   ├── GeoFeatureBatchWriter.java          # транзакционная пакетная запись фич + типизированных сущностей
 │   │   │   │   │   ├── GeoJsonAsyncProcessor.java          # @Async-обработка загрузки + cleanup temp-файла
 │   │   │   │   │   ├── GeoJsonParserService.java           # потоковый парсер GeoJSON с валидацией
 │   │   │   │   │   ├── GeoJsonUploadService.java           # приём файла, создание сессии, статус загрузки
 │   │   │   │   │   ├── GeometryConverterService.java       # GeoJSON ↔ JTS без потери точности double
 │   │   │   │   │   ├── GeoObjectPersister.java             # раскладка фичи в типизированную таблицу по object_type
 │   │   │   │   │   └── UploadCleanupScheduler.java         # cron-очистка сессий, файлов и типизированных таблиц
 │   │   │   │   │
 │   │   │   │   ├── FeatureError.java                       # ошибка валидации одной фичи: id + сообщение
 │   │   │   │   ├── ObjectType.java                         # enum типов + required-атрибуты и allowed-геометрии
 │   │   │   │   └── UploadStatus.java                       # PENDING / PROCESSING / COMPLETED / FAILED
 │   │   │   │
 │   │   │   ├── health/controller/
 │   │   │   │   └── HealthController.java                   # GET /api/health
 │   │   │   │
 │   │   │   └── HeatTraceServiceApplication.java            # точка входа Spring Boot
 │   │   │
 │   │   └── resources/
 │   │       ├── application.yml                             # конфигурация Spring Boot: datasource, JPA, multipart
 │   │       └── schema.sql                                  # CREATE EXTENSION postgis до Hibernate DDL
 │   └── test/
 │       ├── java/ru/moscow/heat/
 │       │   ├── geojson/
 │       │   │   ├── controller/
 │       │   │   │   └── GeoJsonUploadControllerTest.java    # @WebMvcTest: HTTP-коды и структура ответов
 │       │   │   ├── dto/
 │       │   │   │   └── GeoJsonUploadResponseTest.java      # счётчики, ошибки, bbox накопителя парсинга
 │       │   │   ├── repository/
 │       │   │   │   └── GeoFeatureSpatialIntegrationTest.java  # PostGIS: geom/geom_utm, ST_Intersects, ST_DWithin
 │       │   │   ├── service/
 │       │   │   │   ├── CoordinateTransformServiceTest.java    # трансформация 4326 ↔ 32637, round-trip
 │       │   │   │   ├── GeoJsonAsyncProcessorTest.java         # жизненный цикл сессии и cleanup файла
 │       │   │   │   ├── GeoJsonParserServiceTest.java          # валидация входного GeoJSON
 │       │   │   │   ├── GeoJsonUploadServiceTest.java          # приём файла и чтение статуса
 │       │   │   │   ├── GeometryConverterMultiLineStringTest.java  # MultiLineString: разбор и round-trip
 │       │   │   │   ├── GeometryConverterServiceTest.java      # GeoJSON ↔ JTS, включая MultiLineString
 │       │   │   │   ├── GeoObjectPersisterIntegrationTest.java # раскладка по 5 типизированным таблицам
 │       │   │   │   └── UploadCleanupSchedulerTest.java        # очистка всех таблиц по upload_id
 │       │   │
 │       │   │   ├── GeoJsonUploadIntegrationTest.java      # E2E: POST /upload → опрос статуса
 │       │   │   ├── ObjectTypeTest.java                    # парсинг enum и required-наборы атрибутов
 │       │   │   └── TestGeoJsonFactory.java                # билдер тестовых фич GeoJSON
 │       │   │
 │       │   ├── health/controller/
 │       │   │   └── HealthControllerTest.java              # @WebMvcTest для /api/health
 │       │   │
 │       │   └── AbstractIntegrationTest.java               # база: @SpringBootTest + singleton PostGIS-контейнер
 │       │
 │       └── resources/
 │           └── application-test.yml                       # профиль test: create-drop, малый batch-size
 │
 ├── .dockerignore
 ├── .gitignore
 ├── docker-compose.yml
 ├── Dockerfile
 ├── LICENSE
 ├── pom.xml
 └── README.md
```
