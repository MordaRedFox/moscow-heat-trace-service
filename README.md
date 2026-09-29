<div align="center">

# Сервис моделирования трасс подключения к тепловым сетям

[![Java](https://img.shields.io/badge/Java-11-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.6.3-6DB33F?logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![PostGIS](https://img.shields.io/badge/PostGIS-3.4-336791?logo=postgresql&logoColor=white)](https://postgis.net/)
[![Docker](https://img.shields.io/badge/Docker-compose%201.29.2-2496ED?logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](https://opensource.org/licenses/MIT)

</div>

<div align="justify">

Сервис автоматически строит варианты подключения перспективных ОКС к существующей тепловой сети Москвы: выбирает точки врезки, трассирует новую сеть в обход препятствий, подбирает условные диаметры, рассчитывает стоимость и выгружает результат в GeoJSON

</div>

---

## Требования к окружению

- ОС: Ubuntu Server 22 (для развёртывания), любая ОС для разработки
- Java: 11 (JDK)
- Maven: 3.9.9 (в devcontainer, совпадает с Dockerfile)
- Docker: 20.10+ и docker-compose: 1.29.2
- ОЗУ: не менее 16 ГБ
- PostgreSQL: 15+ с расширением PostGIS 3.4 (поднимается автоматически через docker-compose)

---

## Быстрый запуск

### 1. Клонирование и сборка

```bash
git clone https://github.com/MordaRedFox/moscow-heat-trace-service.git
cd moscow-heat-trace-service
mvn -B clean package -DskipTests
```

### 2. Запуск через docker-compose

```bash
docker-compose up -d
```

Поднимутся два сервиса:
- app — Spring Boot приложение на порту 8080
- postgres — БД heat_trace с PostGIS (порт 5432)

Проверка, что сервис поднялся:
```bash
curl -s http://localhost:8080/api/health
# {"status":"UP","service":"heat-trace-service",...}
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

### 3. Локальный запуск без докера

Если PostgreSQL/PostGIS уже доступны локально:
```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/heat_trace
export SPRING_DATASOURCE_USERNAME=heat
export SPRING_DATASOURCE_PASSWORD=heat
mvn spring-boot:run
```

### 4. Остановка

```bash
docker-compose down
# вместе с томами (удаляет все данные):
docker-compose down -v
```

---

## Сценарий работы

### Шаг 1. Загрузка GeoJSON

```bash
curl -X POST http://localhost:8080/api/geojson/upload \
  -F "file=@path/to/dataset.geojson"
```
Ответ `202 Accepted`:
```json
{
  "uploadId": "57c9e6c2-2453-4a14-a84a-256d72c02f6a",
  "status": "PENDING",
  "statusUrl": "/api/geojson/uploads/57c9e6c2-2453-4a14-a84a-256d72c02f6a"
}
```

### Шаг 2. Опрос статуса загрузки

```bash
curl -s http://localhost:8080/api/geojson/uploads/$UPLOAD_ID | jq
```
Когда `status = COMPLETED` — данные разложены по таблицам и готовы к трассировке

### Шаг 3. Запуск трассировки

```bash
curl -X POST http://localhost:8080/api/trace/$UPLOAD_ID
```
Ответ `202 Accepted` с `traceId`. Далее опрос:
```bash
curl -s http://localhost:8080/api/trace/$TRACE_ID | jq
```
Статусы: `PENDING → PROCESSING → COMPLETED` (или `FAILED`)

### Шаг 4. Получение вариантов

```bash
curl -s http://localhost:8080/api/trace/$TRACE_ID/variants | jq
```
Возвращает список сводок (`VariantSummary`): `rank`, `score`, `calculatedCost`, `newNetworkLength`, `unconnectedPenalty` и т.д.

### Шаг 5. Экспорт в GeoJSON

Один вариант:
```bash
curl -o v1.geojson "http://localhost:8080/api/trace/$TRACE_ID/export?variantId=v1"
```

Все варианты одним файлом:
```bash
curl -o all.geojson "http://localhost:8080/api/trace/$TRACE_ID/export"
```

---

## REST API

<table>
  <thead>
    <tr>
      <th>Метод</th>
      <th>Путь</th>
      <th>Назначение</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>GET</code></td>
      <td><code>/api/health</code></td>
      <td>Проверка живости сервиса</td>
    </tr>
    <tr>
      <td><code>POST</code></td>
      <td><code>/api/geojson/upload</code></td>
      <td>Загрузка GeoJSON (multipart/form-data, поле <code>file</code>)</td>
    </tr>
    <tr>
      <td><code>GET</code></td>
      <td><code>/api/geojson/uploads/{uploadId}</code></td>
      <td>Статус парсинга, счётчики, ошибки, bbox</td>
    </tr>
    <tr>
      <td><code>POST</code></td>
      <td><code>/api/trace/{uploadId}</code></td>
      <td>Запуск трассировки</td>
    </tr>
    <tr>
      <td><code>GET</code></td>
      <td><code>/api/trace/{traceId}</code></td>
      <td>Статус трассировки</td>
    </tr>
    <tr>
      <td><code>GET</code></td>
      <td><code>/api/trace/{traceId}/candidates</code></td>
      <td>Кандидаты на присоединение (отладка)</td>
    </tr>
    <tr>
      <td><code>GET</code></td>
      <td><code>/api/trace/{traceId}/variants</code></td>
      <td>Сводки вариантов</td>
    </tr>
    <tr>
      <td><code>GET</code></td>
      <td><code>/api/trace/{traceId}/export?variantId=v1</code></td>
      <td>Экспорт GeoJSON</td>
    </tr>
  </tbody>
</table>

Все параметры пути — UUID. Ошибки в формате `{"error": "..."}`. Коды: `400` (структура), `404` (не найдено), `500` (внутренняя ошибка)

---

## Как работает алгоритм

### 1. Загрузка и парсинг

Файл читается потоково (`JsonParser`) — не грузится целиком в память. Каждая валидная фича превращается в JTS-геометрию (`geom`, SRID 4326) и её UTM-проекцию (`geom_utm`, SRID 32637). Все метрические расчеты выполняются в UTM 37N. Валидация: обязательные атрибуты по типу объекта, типы геометрии, диапазоны координат, уникальность `id`. Числовой `id` (по ТП раздел 1.1) и строковый — оба допустимы.

```java
// Пример: парсинг потоком, батчами по 500 фич
try (JsonParser parser = objectMapper.getFactory().createParser(inputStream)) {
    while (parser.nextToken() != JsonToken.END_ARRAY) {
        JsonNode feature = objectMapper.readTree(parser);
        // валидация, маппинг, добавление в батч
    }
}
```

### 2. Кандидаты на врезку

Для каждой `oks_connection_point`:
1. Ближайший участок `heat_network` (KNN-оператор `<->`)
2. Точка на этом участке — потенциальная врезка
3. Существующие `heat_chamber` в радиусе 10 м — проверка примыканий (≤ 4)
4. Если камера подходит — `EXISTING_CHAMBER` со стоимостью врезки 5 000 000 руб
5. Иначе — `NEW_CHAMBER` в точке на сети, ДУ по flow, стоимость по табл. 3.2 ТП

```sql
-- KNN: ближайший участок сети к точке ОКС
SELECT * FROM heat_network n
WHERE n.upload_id = :uploadId
ORDER BY n.geometry_utm <-> ST_Transform(ST_GeomFromText(:pointWkt, 4326), 32637)
LIMIT 1;
```

### 3. Группировка ОКС

`OksGrouper` объединяет ОКС по:
- общей существующей камере (`existingChamberId`)
- близости tie-in точек (≤ 30 м)

Остальные — одиночные группы. Группы из 2+ ОКС обрабатываются через дерево маршрутов с общим стволом

```java
public enum TraceStrategy {
    MAIN,       // группировка + лучший кандидат
    NO_GROUP,   // каждый ОКС отдельно
    ALT_TIE_IN  // альтернативный кандидат
}
```

### 4. Трассировка

Одиночный ОКС (линейный пайплайн):
1. A* по visibility graph от ОКС к tie-in
2. Свой полигон ОКС игнорируется (по разъяснениям п. 3 ТП — финальный прямой участок)
3. `RouteSimplifier` — string-pulling для устранения зигзагов
4. `AngleChecker` — проверка углов ≥ 45° для `road`/`tram_tracks`
5. Разбиение на сегменты по границам спецзон
6. `DiameterAssigner` — подбор ДУ по расходу и предельной длине
7. `LengthValidator` — защитная проверка

```java
// Ключевые шаги в TraceOrchestrator.processOne
VisibilityGraph.PathResult path = graph.shortestPath(startUtm, endUtm, ignoredIds);
List<Coordinate> simplified = routeSimplifier.simplify(path.getPathUtm(), obstacleModel, ignoredIds);
// ... splitter → diameterAssigner → lengthValidator
```

Группа ОКС (tree-пайплайн):
1. `TreeRouter` — A* на каждый ОКС + слияние общих префиксов в дерево
2. `FlowAggregator` — суммирование flow снизу вверх по дереву
3. `TreeDiameterAssigner` — ДУ с инвариантом «не убывает от листа к корню» и предельной длиной по каждому пути
4. `TreeRouteSegmentSplitter` — сегменты + камеры для ветвлений и корня
5. Fallback на одиночный пайплайн, если групповой не сработал

```java
// Три фазы TreeDiameterAssigner
assignByFlow(tree);         // 1. минимальный ДУ по расходу
enforceMonotonicity(tree);  // 2. ДУ не убывает от листа к корню
enforceMaxLength(tree);     // 3. предельная длина по каждому пути
```

### 5. Пространственные ограничения

Из ТП таблица 2:
- `FORBIDDEN` (`oks`, `park`, `social_area`, `prohibited_site`, `water`, `railway`) — обход с отступом
- `SPECIAL_CROSSING` (`road`, `tram_tracks`, `gas_pipeline`, `power_cable`, `heat_network`) — спецпроход с `Kспец`

Для `FORBIDDEN` используются буферы + `PreparedGeometry.crosses` + STRtree-индекс. Для `SPECIAL` — `intersects` с буфером, `Kспец = max` при наложении

```java
// Двухуровневая проверка видимости
if (!segmentEnv.intersects(zone.getEnvelope())) continue;         // 1. envelope prefilter
if (zone.getPreparedGeometry().crosses(segment)) return false;    // 2. PreparedGeometry
```

### 6. Стоимость и ранжирование

Стоимость участка: `Cуч = L · cнов(ДУ) · Kгл · Kспец`
- `cнов(ДУ)` — из таблицы 1 ТП
- `Kгл = 1` в 2D-режиме
- `Kспец` — из таблицы 2, максимум при наложении

Стоимость варианта: новые участки + новые камеры + врезки + штраф за неподключенные. Штраф за ОКС: `100 000 000 + 500 000 · G`. Итоговый показатель: `S = 0.7·(C/25M) + 0.3·(L/100)`

```java
public double calculateScore(BigDecimal calculatedCost, double newNetworkLength) {
    double costPart = 0.7 * (calculatedCost.doubleValue() / 25_000_000.0);
    double lengthPart = 0.3 * (newNetworkLength / 100.0);
    return costPart + lengthPart;
}
```

### 7. Варианты

Формируется до 3 содержательно отличающихся вариантов:
- v1 (MAIN) — группировка + лучший кандидат
- v2 (NO_GROUP) — каждый ОКС отдельно
- v3 (ALT_TIE_IN) — альтернативный кандидат врезки

Все строятся одним пайплайном (visibility graph + A*), отличаются только входными параметрами

---

## Формат выходного GeoJSON

`FeatureCollection` в WGS 84 (EPSG:4326) с объектами:

<table>
  <thead>
    <tr>
      <th><code>object_type</code></th>
      <th>Геометрия</th>
      <th>Ключевые атрибуты</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>heat_network</code></td>
      <td><code>LineString</code></td>
      <td><code>start_node_id</code>, <code>end_node_id</code>, <code>flow_tph</code>, <code>diameter</code>, <code>length</code>, <code>laying_method</code>, <code>depth_start</code>, <code>depth_end</code>, <code>cost</code>, <code>variant_id</code></td>
    </tr>
    <tr>
      <td><code>heat_chamber</code></td>
      <td><code>Point</code></td>
      <td><code>diameter</code>, <code>cost</code>, <code>variant_id</code></td>
    </tr>
    <tr>
      <td><code>technical_node</code></td>
      <td><code>Point</code></td>
      <td><code>variant_id</code></td>
    </tr>
    <tr>
      <td><code>variant_summary</code></td>
      <td><code>null</code></td>
      <td><code>rank</code>, <code>construction_cost</code>, <code>chamber_construction_cost</code>, <code>existing_chamber_tie_in_count</code>, <code>existing_chamber_tie_in_cost</code>, <code>unconnected_penalty</code>, <code>calculated_cost</code>, <code>new_network_length</code>, <code>score</code>, <code>unconnected_oks_ids</code></td>
    </tr>
  </tbody>
</table>

Идентификаторы — строковые или числовые, сохраняют исходный тип

### Пример выходного GeoJSON

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "id": "v1-seg-1",
      "geometry": {
        "type": "LineString",
        "coordinates": [
          [37.6344054041544, 55.6994810644531],
          [37.6338, 55.6995]
        ]
      },
      "properties": {
        "id": "v1-seg-1",
        "object_type": "heat_network",
        "variant_id": "v1",
        "start_node_id": "1",
        "end_node_id": "ch-branch-a1b2c3d4",
        "flow_tph": 24.87,
        "diameter": 100,
        "length": 72.4,
        "laying_method": "base",
        "depth_start": null,
        "depth_end": null,
        "cost": 6498400.00
      }
    },
    {
      "type": "Feature",
      "id": "v1-summary",
      "geometry": null,
      "properties": {
        "id": "v1-summary",
        "object_type": "variant_summary",
        "variant_id": "v1",
        "rank": 1,
        "construction_cost": 145230000.00,
        "chamber_construction_cost": 42000000.00,
        "existing_chamber_tie_in_count": 3,
        "existing_chamber_tie_in_cost": 15000000.00,
        "unconnected_penalty": 0.00,
        "calculated_cost": 145230000.00,
        "new_network_length": 8420.5,
        "score": 29.34,
        "unconnected_oks_ids": []
      }
    }
  ]
}
```

---

## Обработка ошибок и частичных результатов

- Структурные ошибки GeoJSON → `400 Bad Request`
- Ошибка обработки загрузки → сессия в статусе `FAILED` с `errorMessage`
- Неподключённые ОКС → попадают в `unconnectedOksFeatureIds`, штраф учитывается в стоимости. Сервис обрабатывает оставшиеся ОКС и возвращает частичный результат
- Fallback групп → если групповой пайплайн не сработал (нет дерева, нарушение угла, не сошёлся ДУ), группа обрабатывается поштучно. Причина пишется в лог

```json
{
  "error": "Загрузка не найдена: 00000000-0000-0000-0000-000000000000"
}
```

Три причины неподключения (`UnconnectedOks.Reason`):

<table>
  <thead>
    <tr>
      <th>Reason</th>
      <th>Когда возникает</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>NO_TIE_IN_CANDIDATE</code></td>
      <td>Нет <code>heat_network</code> или подходящей камеры рядом с точкой ОКС</td>
    </tr>
    <tr>
      <td><code>NO_PATH_IN_GRAPH</code></td>
      <td>Граф видимости не даёт пути от ОКС к tie-in</td>
    </tr>
    <tr>
      <td><code>PATH_REJECTED_BY_VALIDATION</code></td>
      <td>Нарушен угол, превышена предельная длина и т.п.</td>
    </tr>
  </tbody>
</table>

При групповом пайплайне — при любой ошибке в `processGroup` группа уходит в fallback на поштучную обработку

---

## Ограничения

- 2D-режим. Глубина и продольный профиль не рассчитываются. `Kгл = 1`, `depth_start = depth_end = null`
- Реконструкция существующей сети не выполняется (по актуальному ТП, разъяснение 14)
- Полный гидравлический расчёт не делается — только подбор ДУ и проверка предельной длины
- Отступ от ОКС взят по максимальному ДУ (9 м) — консервативно для всех маршрутов
- Объединение ОКС реализовано по простым правилам (общая камера, радиус 30 м). Оптимальная группировка не гарантируется
- Проверочный набор может отличаться по количеству объектов и сложности геометрии. Логика не завязана на конкретные координаты, ID или конфигурацию

### Что не входит в обязательную часть

Согласно ТЗ раздел 2.12, не требуется:
- выполнять полный гидравлический расчёт (давление, потери напора, скорости, насосные режимы)
- рассчитывать или развивать мощность источника
- в обязательной части учитывать глубину и строить продольный профиль
- выполнять рабочее проектирование и расчёты прочности
- проектировать конструкцию тепловых камер и специальных переходов
- рассчитывать температурные деформации и подбирать компенсаторы
- готовить проектную документацию и решения по организации строительства
- использовать надземную прокладку
- определять положение ИТП внутри здания
- изменять трассу существующей тепловой сети при реконструкции

### Универсальность

Координаты, идентификаторы и конкретная конфигурация конкурсного набора не зашиты в код. Сервис готов к запуску на проверочном наборе той же структуры без изменения алгоритма

---

## Структура проекта

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
