# moscow-heat-trace-service

## Структура проекта на данный момент
```bash
moscow-heat-trace-service/
│
├── .devcontainer/
│   ├── devcontainer.json
│   ├── docker-compose.yml
│   ├── post-create.sh
│   └── post-start.sh
│
├── src/
│   ├── main/
│   │   ├── java/ru/moscow/heat/
│   │   │   ├── geojson/
│   │   │   │   ├── config/
│   │   │   │   │   └── AsyncConfig.java                  # Executor для @Async, @EnableScheduling
│   │   │   │   ├── controller/
│   │   │   │   │   └── GeoJsonUploadController.java      # POST /upload (202) + GET /uploads/{id}
│   │   │   │   ├── dto/
│   │   │   │   │   ├── GeoJsonUploadResponse.java        # Внутренний результат парсинга (counts, bbox, errors)
│   │   │   │   │   ├── UploadAcceptedResponse.java       # Ответ 202: uploadId, status, statusUrl
│   │   │   │   │   ├── UploadStatusResponse.java         # Ответ GET статуса (публичный DTO)
│   │   │   │   │   └── UploadSummary.java                # Компактная сводка, сериализуется в jsonb
│   │   │   │   ├── entity/
│   │   │   │   │   ├── GeoFeature.java                   # Загруженный feature: upload_id + feature_id (unique)
│   │   │   │   │   └── UploadSession.java                # Сессия загрузки: статус, тайминги, summary
│   │   │   │   ├── exception/
│   │   │   │   │   └── GeoJsonParseException.java
│   │   │   │   ├── repository/
│   │   │   │   │   ├── GeoFeatureRepository.java         # +countByUploadId, deleteByUploadId
│   │   │   │   │   └── UploadSessionRepository.java      # Поиск старых сессий для очистки
│   │   │   │   ├── service/
│   │   │   │   │   ├── GeoFeatureBatchWriter.java        # @Transactional saveBatch/saveSingle с flush+clear
│   │   │   │   │   ├── GeoJsonAsyncProcessor.java        # @Async обработка + cleanupTempFile
│   │   │   │   │   ├── GeoJsonParserService.java         # Потоковый парсинг + вся валидация
│   │   │   │   │   ├── GeoJsonUploadService.java         # acceptUpload (202) + getStatus
│   │   │   │   │   └── UploadCleanupScheduler.java       # Cron-очистка сессий старше N дней
│   │   │   │   ├── FeatureError.java                     # {featureId, message}, Jackson-совместимый
│   │   │   │   ├── ObjectType.java                       # Enum типов + required/allowedGeometryTypes
│   │   │   │   └── UploadStatus.java                     # PENDING/PROCESSING/COMPLETED/FAILED
│   │   │   │
│   │   │   ├── health/controller/
│   │   │   │   └── HealthController.java
│   │   │   │
│   │   │   └── HeatTraceServiceApplication.java
│   │   │
│   │   └── resources/
│   │       └── application.yml
│   │
│   └── test/
│       └── java/ru/moscow/heat/
│           └── HeatTraceServiceApplicationTests.java
│
├── target/                         # (gitignored, генерируется Maven)
├── .gitignore
├── LICENSE
├── pom.xml
└── README.md
```
