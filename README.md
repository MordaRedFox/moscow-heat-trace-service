# moscow-heat-trace-service

## Структура проекта на данный момент
```bash
moscow-heat-trace-service/
 ├── .devcontainer/
 ├── src/
 │   ├── main/
 │   │   ├── java/ru/moscow/heat/
 │   │   │   ├── geojson/
 │   │   │   │   ├── config/
 │   │   │   │   │   ├── AsyncConfig.java
 │   │   │   │   │   └── SpatialIndexInitializer.java      # [NEW] GIST-индексы после старта
 │   │   │   │   ├── controller/
 │   │   │   │   │   └── GeoJsonUploadController.java
 │   │   │   │   ├── dto/
 │   │   │   │   │   ├── GeoJsonUploadResponse.java
 │   │   │   │   │   ├── UploadAcceptedResponse.java
 │   │   │   │   │   ├── UploadStatusResponse.java
 │   │   │   │   │   └── UploadSummary.java
 │   │   │   │   ├── entity/
 │   │   │   │   │   ├── AbstractGeoObject.java            # базовый класс типизированных гео-сущностей
 │   │   │   │   │   ├── GeoFeature.java                   # [CHANGED] + geom (4326), geom_utm (32637)
 │   │   │   │   │   ├── HeatChamberEntity.java
 │   │   │   │   │   ├── HeatNetworkEntity.java
 │   │   │   │   │   ├── OksConnectionPointEntity.java
 │   │   │   │   │   ├── OksExistingEntity.java
 │   │   │   │   │   ├── OksFutureEntity.java
 │   │   │   │   │   ├── RestrictionEntity.java
 │   │   │   │   │   ├── SourceEntity.java
 │   │   │   │   │   └── UploadSession.java
 │   │   │   │   ├── exception/
 │   │   │   │   │   ├── GeoJsonParseException.java
 │   │   │   │   │   └── UploadNotFoundException.java
 │   │   │   │   ├── repository/
 │   │   │   │   │   ├── GeoFeatureRepository.java         # [CHANGED] + spatial-запросы (bbox/intersects/dwithin)
 │   │   │   │   │   ├── HeatChamberRepository.java
 │   │   │   │   │   ├── HeatNetworkRepository.java
 │   │   │   │   │   ├── OksConnectionPointRepository.java
 │   │   │   │   │   ├── OksExistingRepository.java
 │   │   │   │   │   ├── OksFutureRepository.java
 │   │   │   │   │   ├── RestrictionRepository.java
 │   │   │   │   │   ├── SourceRepository.java
 │   │   │   │   │   └── UploadSessionRepository.java
 │   │   │   │   ├── service/
 │   │   │   │   │   ├── CoordinateTransformService.java   # [CHANGED] Proj4J, toUtm37N/toUtm/toWgs84
 │   │   │   │   │   ├── GeoFeatureBatchWriter.java
 │   │   │   │   │   ├── GeoJsonAsyncProcessor.java
 │   │   │   │   │   ├── GeoJsonParserService.java         # [CHANGED] строит geom/geom_utm при парсинге
 │   │   │   │   │   ├── GeoJsonUploadService.java
 │   │   │   │   │   ├── GeoObjectPersister.java           # типизированные таблицы (уже использует toUtm37N)
 │   │   │   │   │   ├── GeometryConverterService.java     # [NEW] GeoJSON <-> JTS (jts-io-common)
 │   │   │   │   │   └── UploadCleanupScheduler.java
 │   │   │   │   ├── util/
 │   │   │   │   │   └── GeoJsonGeometryMapper.java        # существующий маппер (используется Persister-ом)
 │   │   │   │   ├── FeatureError.java
 │   │   │   │   ├── ObjectType.java
 │   │   │   │   └── UploadStatus.java
 │   │   │   ├── health/controller/
 │   │   │   │   └── HealthController.java
 │   │   │   └── HeatTraceServiceApplication.java
 │   │   └── resources/
 │   │       ├── schema.sql                                # [NEW] CREATE EXTENSION postgis
 │   │       └── application.yml                           # [CHANGED] dialect + sql.init
 │   └── test/
 │       ├── java/ru/moscow/heat/
 │       │   ├── geojson/
 │       │   │   ├── controller/GeoJsonUploadControllerTest.java
 │       │   │   ├── dto/GeoJsonUploadResponseTest.java
 │       │   │   ├── repository/
 │       │   │   │   └── GeoFeatureSpatialIntegrationTest.java   # [NEW] ST_DWithin/ST_Intersects/bbox
 │       │   │   ├── service/
 │       │   │   │   ├── CoordinateTransformServiceTest.java     # [NEW] контрольная точка Москвы
 │       │   │   │   ├── GeometryConverterServiceTest.java       # [NEW] round-trip GeoJSON->JTS->GeoJSON
 │       │   │   │   ├── GeoJsonAsyncProcessorTest.java
 │       │   │   │   ├── GeoJsonParserServiceTest.java
 │       │   │   │   ├── GeoJsonUploadServiceTest.java
 │       │   │   │   └── UploadCleanupSchedulerTest.java
 │       │   │   ├── GeoJsonUploadIntegrationTest.java
 │       │   │   ├── ObjectTypeTest.java
 │       │   │   └── TestGeoJsonFactory.java
 │       │   ├── health/controller/HealthControllerTest.java
 │       │   └── AbstractIntegrationTest.java              # [CHANGED] образ postgis/postgis:16-3.4
 │       └── resources/
 │           └── application-test.yml
 ├── docker-compose.yml                                    # [CHANGED] postgis/postgis:16-3.4, healthcheck -d heat
 ├── pom.xml                                               # [CHANGED] + jts-io-common 1.18.2
 └── README.md
```
