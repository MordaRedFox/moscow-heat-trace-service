# moscow-heat-trace-service

## Структура проекта на данный момент
```bash
moscow-heat-trace-service/
├── .devcontainer/
│   ├── devcontainer.json
│   ├── docker-compose.yml
│   ├── post-create.sh
│   └── post-start.sh
│
├── src/
│   ├── main/
│   │   ├── java/ru/moscow/heat/
│   │   │   ├── geojson/                       # Всё, что связано с приёмом, парсингом и хранением входного GeoJSON
│   │   │   │   ├── config/
│   │   │   │   │   ├── AsyncConfig.java                    # Пул потоков для @Async-парсинга и трассировки (2–4 потока) + @EnableScheduling для cron-очистки
│   │   │   │   │   └── SpatialIndexInitializer.java        # Идемпотентно создаёт GIST-индексы на geom/geom_utm после старта Hibernate DDL
│   │   │   │   ├── controller/
│   │   │   │   │   └── GeoJsonUploadController.java        # REST: POST /api/geojson/upload, GET /api/geojson/uploads/{id}; обработчики 400/404/500
│   │   │   │   ├── dto/
│   │   │   │   │   ├── GeoJsonUploadResponse.java          # Внутренний накопитель парсинга: счётчики по типам, bbox, ошибки (лимит 1000)
│   │   │   │   │   ├── UploadAcceptedResponse.java         # Ответ 202: uploadId, статус PENDING, URL опроса статуса
│   │   │   │   │   ├── UploadStatusResponse.java           # Публичный ответ GET статуса: тайминги, счётчики, bbox, список ошибок
│   │   │   │   │   └── UploadSummary.java                  # Компактная сводка для jsonb-колонки upload_session.summary
│   │   │   │   ├── entity/
│   │   │   │   │   ├── AbstractGeoObject.java              # Базовый класс типизированных сущностей: id, upload_id, feature_id, geom(4326), geom_utm(32637), properties
│   │   │   │   │   ├── GeoFeature.java                     # Сырая фича: geometry(jsonb) + geom(4326) + geom_utm(32637); изоляция по upload_id
│   │   │   │   │   ├── HeatChamberEntity.java              # Существующая тепловая камера (без атрибутов — только id и точка)
│   │   │   │   │   ├── HeatNetworkEntity.java              # Существующий участок сети: только diameter; без расхода и цепочки к источнику
│   │   │   │   │   ├── OksConnectionPointEntity.java       # Точка подключения ОКС: flow_tph; каждая точка — самостоятельная цель
│   │   │   │   │   ├── RestrictionEntity.java              # Пространственное ограничение: restriction_type + геометрия
│   │   │   │   │   ├── SourceEntity.java                   # Источник теплоснабжения (один на загрузку)
│   │   │   │   │   └── UploadSession.java                  # Сессия загрузки: статус, тайминги, temp-файл, счётчики, summary
│   │   │   │   ├── exception/
│   │   │   │   │   ├── GeoJsonParseException.java          # Структурная ошибка GeoJSON → HTTP 400
│   │   │   │   │   └── UploadNotFoundException.java        # Сессия не найдена → HTTP 404
│   │   │   │   ├── repository/
│   │   │   │   │   ├── GeoFeatureRepository.java           # Сырые фичи + пространственные запросы: bbox, ST_Intersects, ST_DWithin
│   │   │   │   │   ├── HeatChamberRepository.java          # Камеры + поиск в радиусе от точки через geometry_utm (GIST + ST_DWithin)
│   │   │   │   │   ├── HeatNetworkRepository.java          # Участки сети + KNN (оператор <->) + countConnectionsToChamber (native SQL)
│   │   │   │   │   ├── OksConnectionPointRepository.java   # Точки ОКС + поиск по feature_id
│   │   │   │   │   ├── RestrictionRepository.java          # Ограничения + базовые операции по upload_id
│   │   │   │   │   ├── SourceRepository.java               # Источники + базовые операции по upload_id
│   │   │   │   │   ├── UploadAwareRepository.java          # Базовый интерфейс: findByUploadId / deleteByUploadId для всех типизированных
│   │   │   │   │   └── UploadSessionRepository.java        # Сессии + поиск старых (COMPLETED/FAILED) для cron-очистки
│   │   │   │   ├── service/
│   │   │   │   │   ├── CoordinateTransformService.java     # Proj4J: EPSG:4326 ↔ EPSG:32637, точка и геометрия, без потери точности
│   │   │   │   │   ├── GeoFeatureBatchWriter.java          # Транзакционная пакетная запись GeoFeature + типизированных через GeoObjectPersister
│   │   │   │   │   ├── GeoJsonAsyncProcessor.java          # @Async-обработка загрузки: чтение temp-файла, обновление сессии, гарантированный cleanup
│   │   │   │   │   ├── GeoJsonParserService.java           # Потоковый парсер GeoJSON: структура, атрибуты, типы геометрии, дедупликация id, батчи; id — строка или число
│   │   │   │   │   ├── GeoJsonUploadService.java           # Приём файла → temp-диск → создание сессии → запуск @Async-обработки; чтение статуса
│   │   │   │   │   ├── GeometryConverterService.java       # Ручной GeoJSON ↔ JTS через JsonNode (в обход jts-io-common, чтобы не терять разряды double)
│   │   │   │   │   ├── GeoObjectPersister.java             # Раскладка GeoFeature по 5 типизированным таблицам согласно object_type
│   │   │   │   │   └── UploadCleanupScheduler.java         # Cron 03:00: удаляет сессии старше retention-days + их данные во всех таблицах
│   │   │   │   ├── FeatureError.java                       # Пара featureId + message; сериализуется в jsonb-сводку
│   │   │   │   ├── ObjectType.java                         # Enum типов (5 шт) + требуемые атрибуты + допустимые типы геометрии
│   │   │   │   └── UploadStatus.java                       # PENDING / PROCESSING / COMPLETED / FAILED
│   │   │   │
│   │   │   ├── health/controller/
│   │   │   │   └── HealthController.java                   # GET /api/health: статус сервиса
│   │   │   │
│   │   │   ├── spatial/                       # Нормативные справочники и утилиты геометрии
│   │   │   │   ├── ChamberCostTable.java                   # Таблица 3.2 ТП: стоимость новой камеры по наибольшему ДУ (3/5/8/12 млн)
│   │   │   │   ├── ConsistencyReport.java                  # Отчёт валидации: valid + errors + warnings
│   │   │   │   ├── DiameterSpec.java                       # Строка Таблицы 1: ДУ, пропускная, предельная длина, цена, габариты пары
│   │   │   │   ├── DiameterTable.java                      # Таблица 1 ТП: minDiameterForFlow, minDiameterForFlowAndLength, nextDiameter, largestOf
│   │   │   │   ├── GeometryUtils.java                      # Метрические операции в UTM: длина, расстояние, ближайшая точка, пересечение, угол, буфер, точка на линии
│   │   │   │   ├── OksConnectionPointResolver.java         # ST_Contains: точка ОКС → полигон restriction_type=oks; список unbound-точек; resolvePolygonDatabaseId для ignore-set
│   │   │   │   ├── RestrictionRule.java                    # Строка Таблицы 2: правило, мин.расстояние, угол, Kспец, габарит, padding спецзоны
│   │   │   │   ├── RestrictionRuleRegistry.java            # Реестр правил по restriction_type + шкала отступа до ОКС (5/7/9 м по ДУ)
│   │   │   │   ├── RestrictionRuleType.java                # FORBIDDEN / SPECIAL_CROSSING
│   │   │   │   └── UploadConsistencyValidator.java         # Пред-трассировочная валидация: один source, точки с flow_tph>0, диаметры>0, уникальность id
│   │   │   │
│   │   │   ├── trace/                         # Трассировка новой сети: граф видимости, A*, постобработка
│   │   │   │   ├── controller/
│   │   │   │   │   └── TraceController.java                # POST /api/trace/{uploadId}, GET /api/trace/{traceId}, GET /candidates
│   │   │   │   ├── dto/
│   │   │   │   │   ├── PointGeoJsonSerializer.java         # Jackson-сериализатор JTS Point → GeoJSON (для REST-ответов)
│   │   │   │   │   ├── TieInCandidate.java                 # Immutable DTO кандидата: тип, точки (tie-in + target), расстояния, примыкания, стоимость
│   │   │   │   │   ├── TieInType.java                      # EXISTING_CHAMBER / NEW_CHAMBER
│   │   │   │   │   ├── TraceAcceptedResponse.java          # Ответ 202: traceId + statusUrl
│   │   │   │   │   ├── TraceStatus.java                    # PENDING/PROCESSING/COMPLETED/FAILED
│   │   │   │   │   └── TraceStatusResponse.java            # Публичный ответ GET /api/trace/{traceId}: статус, тайминги, счётчики, список неподключённых
│   │   │   │   ├── exception/
│   │   │   │   │   └── TraceNotFoundException.java         # Задача трассировки не найдена → HTTP 404
│   │   │   │   ├── graph/                     # Модель препятствий и граф видимости
│   │   │   │   │   ├── GraphNode.java                      # Внутренний узел графа: id, x/y UTM, Kind (FORBIDDEN_CORNER / SPECIAL_CORNER / EXISTING_NETWORK_ENDPOINT)
│   │   │   │   │   ├── ObstacleModel.java                  # Подготовленные FORBIDDEN и SPECIAL зоны с envelope, PreparedGeometry и STRtree-индексами
│   │   │   │   │   ├── ObstacleModelBuilder.java           # Строит ObstacleModel: буфер по правилу, TopologyPreservingSimplifier 1 м, раскладка FORBIDDEN/SPECIAL
│   │   │   │   │   └── VisibilityGraph.java                # Узлы — углы FORBIDDEN-буферов (дедуп по spatial grid), A* с эвристикой, ignore-set для своего полигона ОКС
│   │   │   │   ├── model/                     # DTO маршрута
│   │   │   │   │   ├── LayingMethod.java                   # BASE / SPECIAL
│   │   │   │   │   ├── NewChamber.java                     # Новая тепловая камера: точка UTM, диаметр, стоимость (заготовка под итерацию 7)
│   │   │   │   │   ├── OksGroup.java                       # Группа ОКС, подключаемых совместно через общий tie-in (камерная / радиусная / одиночная)
│   │   │   │   │   ├── RouteNode.java                      # Узел маршрута: id, тип, координата UTM, source_feature_id
│   │   │   │   │   ├── RouteNodeType.java                  # OKS_POINT / EXISTING_CHAMBER / NEW_CHAMBER / TECHNICAL_NODE / CORNER
│   │   │   │   │   ├── RouteSegment.java                   # Отрезок маршрута: узлы, геометрия UTM, flow, ДУ, способ прокладки, Kспец, длина, cost (null)
│   │   │   │   │   ├── RouteTree.java                      # Дерево маршрутов группы ОКС: корень (tie-in), рёбра, листья, узлы ветвления
│   │   │   │   │   ├── TechnicalNode.java                  # Служебная точка смены ДУ / способа прокладки / границы спецзоны
│   │   │   │   │   ├── TraceResult.java                    # Итог трассировки: сегменты, камеры, техузлы, неподключённые, счётчики
│   │   │   │   │   ├── TreeEdge.java                       # Ребро дерева: from/to узлы, геометрия, длина, flow, ДУ, servedOksIds (мутабельно в фазе расчёта)
│   │   │   │   │   ├── TreeNode.java                       # Узел дерева: ROOT / LEAF / BRANCH / INTERMEDIATE, координата, рёбра к родителю и детям
│   │   │   │   │   └── UnconnectedOks.java                 # Неподключённый ОКС: feature_id, Reason, детали
│   │   │   │   └── service/
│   │   │   │       ├── AngleChecker.java                   # Проверка угла пересечения road/tram_tracks ≥ 45°; коллинеарные — недопустимо
│   │   │   │       ├── DiameterAssigner.java               # Назначение ДУ сегментам линейного маршрута; техузлы смены ДУ; ДУ не убывает к tie-in
│   │   │   │       ├── FlowAggregator.java                 # Агрегация flow и servedOksIds по рёбрам дерева (снизу вверх от листьев к корню)
│   │   │   │       ├── LengthValidator.java                # Проверка предельной длины непрерывной части одного ДУ; защитная проверка перед сборкой TraceResult
│   │   │   │       ├── OksGrouper.java                     # Группировка ОКС: по общей камере, по радиусу tie-in (30 м), одиночки
│   │   │   │       ├── RouteSegmentSplitter.java           # Разбиение линейного пути по границам спецзон; топология RouteNode между соседними сегментами
│   │   │   │       ├── RouteSimplifier.java                # String-pulling с envelope prefilter + PreparedGeometry + STRtree; ignore-set для первого сегмента
│   │   │   │       ├── TieInCandidateService.java          # Алгоритм: ближайший heat_network → tie-in → камеры ≤10 м и ≤4 примыканий → EXISTING или NEW
│   │   │   │       ├── TraceAsyncProcessor.java            # @Async-обработка трассировки: markProcessing → run → markCompleted / markFailed
│   │   │   │       ├── TraceOrchestrator.java              # Главный конвейер: группировка → групповой пайплайн с fallback на поштучную обработку
│   │   │   │       ├── TraceService.java                   # In-memory сессии: traceId → uploadId → TraceResult; статусы, кандидаты, счётчики
│   │   │   │       ├── TreeDiameterAssigner.java           # Назначение ДУ рёбрам дерева: по flow → монотонность от листа к корню → предельная длина
│   │   │   │       ├── TreeRouter.java                     # Построение дерева маршрутов группы: A* на ОКС + RouteSimplifier, слияние путей по координате
│   │   │   │       └── TreeRouteSegmentSplitter.java       # Разбиение рёбер дерева на сегменты; камеры для ветвлений и корня; топология RouteNode по nodeCache
│   │   │   │
│   │   │   └── HeatTraceServiceApplication.java            # Точка входа Spring Boot
│   │   │
│   │   └── resources/
│   │       ├── application.yml                             # Datasource, JPA (PostgisDialect, batch), multipart до 3ГБ, springdoc, management
│   │       └── schema.sql                                  # CREATE EXTENSION IF NOT EXISTS postgis до Hibernate DDL
│   │
│   └── test/
│       ├── java/ru/moscow/heat/
│       │   ├── geojson/
│       │   │   ├── controller/
│       │   │   │   └── GeoJsonUploadControllerTest.java    # @WebMvcTest: 202/400/404, структура ответов
│       │   │   ├── dto/
│       │   │   │   └── GeoJsonUploadResponseTest.java      # Счётчики, усечение ошибок, bbox
│       │   │   ├── repository/
│       │   │   │   └── GeoFeatureSpatialIntegrationTest.java # PostGIS: geom/geom_utm, ST_Intersects, ST_DWithin
│       │   │   ├── service/
│       │   │   │   ├── CoordinateTransformServiceTest.java # Трансформация, SRID, round-trip, диапазон зоны 37N
│       │   │   │   ├── GeoJsonAsyncProcessorTest.java      # PENDING→COMPLETED/FAILED, cleanup файла
│       │   │   │   ├── GeoJsonParserServiceTest.java       # Позитивы, негативы, CRS, удалённые типы, дубликаты, типы атрибутов, числовой id
│       │   │   │   ├── GeoJsonUploadServiceTest.java       # Приём файла, дефолтное имя, битый summary, 404
│       │   │   │   ├── GeometryConverterMultiLineStringTest.java # MultiLineString: разбор и round-trip
│       │   │   │   ├── GeometryConverterServiceTest.java   # Point/LineString/MultiPolygon round-trip с допуском 1e-9
│       │   │   │   ├── GeoObjectPersisterIntegrationTest.java # Раскладка по 5 таблицам, изоляция по upload_id
│       │   │   │   └── UploadCleanupSchedulerTest.java     # Удаление из всех таблиц, только COMPLETED/FAILED
│       │   │   ├── GeoJsonUploadIntegrationTest.java       # E2E: POST /upload → опрос статуса до COMPLETED
│       │   │   ├── ObjectTypeTest.java                     # Парсинг enum, required-наборы, удалённые типы
│       │   │   └── TestGeoJsonFactory.java                 # Билдер тестовых FeatureCollection без ручных строк JSON
│       │   │
│       │   ├── health/controller/
│       │   │   └── HealthControllerTest.java               # @WebMvcTest для /api/health
│       │   │
│       │   ├── spatial/
│       │   │   ├── DiameterTableTest.java                  # Все 18 строк Таблицы 1 + границы подбора + next + largestOf
│       │   │   ├── GeometryUtilsTest.java                  # Длина, расстояние, угол, буфер, точка на линии (UTM 37N)
│       │   │   ├── OksConnectionPointResolverTest.java     # ST_Contains: внутри/вне/на границе + изоляция по upload_id + resolvePolygonDatabaseId
│       │   │   ├── RestrictionRuleRegistryTest.java        # Таблица 2 ТП: все правила, Kспец, шкала отступа для oks
│       │   │   └── UploadConsistencyValidatorTest.java     # errors vs warnings на каждом сценарии
│       │   │
│       │   ├── trace/
│       │   │   ├── controller/
│       │   │   │   └── TraceControllerTest.java            # @WebMvcTest: 202/404/200, verify async process
│       │   │   ├── graph/
│       │   │   │   └── VisibilityGraphTest.java            # Пустой ObstacleModel, обход прямоугольника, ignore-set для старта внутри полигона
│       │   │   └── service/
│       │   │       ├── AngleCheckerTest.java               # 90°, ровно 45°, 30°, коллинеарные, нет пересечения, дефолт
│       │   │       ├── DiameterAssignerTest.java           # Постоянный ДУ, рост на превышении длины, ДУ не убывает
│       │   │       ├── FlowAggregatorTest.java             # Общий ствол, одиночный ОКС, вложенное ветвление
│       │   │       ├── LengthValidatorTest.java            # В пределах/превышение, сброс счётчика при смене ДУ, неизвестный ДУ
│       │   │       ├── OksGrouperTest.java                 # Камерная и радиусная группировка, одиночки, исключение без кандидата
│       │   │       ├── RouteSegmentSplitterTest.java       # Разбиение BASE/SPECIAL, end_node_id == start_node_id, тип конечного узла, METHOD_CHANGE
│       │   │       ├── RouteSimplifierTest.java            # Склейка коллинеарных, обход препятствия, ignore-set
│       │   │       ├── TieInCandidateIntegrationTest.java  # PostGIS: countConnections (0/1/2/4 + транзит), EXISTING/NEW, target-координаты, изоляция
│       │   │       ├── TieInCandidateServiceTest.java      # Mockito: границы 10 м, 1–4 примыканий, target vs tie-in, стоимость камеры, сортировка
│       │   │       ├── TraceOrchestratorIntegrationTest.java # E2E на PostGIS: один ОКС внутри полигона; 3 ОКС к одной камере — групповой пайплайн
│       │   │       ├── TraceServiceTest.java               # Mockito: сессия, статусы PENDING/PROCESSING/COMPLETED/FAILED, кандидаты, 404
│       │   │       ├── TreeDiameterAssignerTest.java       # Монотонность, каскад, предельная длина, общий ствол
│       │   │       ├── TreeRouterTest.java                 # Слияние ствола, ветвление в точке расхождения, корень как ветвление
│       │   │       └── TreeRouteSegmentSplitterTest.java   # Топология, EXISTING_CHAMBER-корень, камеры ветвлений, наследование ДУ и flow
│       │   │
│       │   └── AbstractIntegrationTest.java                # @SpringBootTest + singleton PostGIS-контейнер через Testcontainers
│       │
│       └── resources/
│           └── application-test.yml                        # Профиль test: create-drop, batch-size 2, малый error-log-limit
│
├── .dockerignore
├── .gitignore
├── docker-compose.yml
├── Dockerfile
├── LICENSE
├── pom.xml
└── README.md
```
