package ru.moscow.heat.geojson.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.ObjectType;
import ru.moscow.heat.geojson.dto.GeoJsonUploadResponse;
import ru.moscow.heat.geojson.entity.GeoFeature;
import ru.moscow.heat.geojson.exception.GeoJsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Потоковый парсер GeoJSON с валидацией структуры, обязательных атрибутов и
 * типов геометрии. Файл не загружается в память целиком; валидные объекты
 * сохраняются батчами
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoJsonParserService {

    private final GeoFeatureBatchWriter batchWriter;
    private final ObjectMapper objectMapper;

    @Value("${heat.upload.batch-size:500}")
    private int batchSize;

    @Value("${heat.upload.error-log-limit:20}")
    private int errorLogLimit;

    /**
     * Разбирает поток GeoJSON и сохраняет валидные объекты
     * батчами по {@link #batchSize}
     * @param inputStream поток входного файла
     * @param uploadId    идентификатор загрузки, проставляется
     *                    в каждый {@link GeoFeature}
     * @return сводка: счетчики по типам, bbox, ошибки
     * @throws IOException           при ошибках чтения потока
     * @throws GeoJsonParseException при структурных ошибках
     */
    public GeoJsonUploadResponse processStream(
            InputStream inputStream, UUID uploadId) throws IOException {
        GeoJsonUploadResponse response = new GeoJsonUploadResponse();
        List<GeoFeature> batch = new ArrayList<>(batchSize);
        AtomicInteger logCounter = new AtomicInteger(0);

        try (JsonParser parser = objectMapper.getFactory()
                .createParser(inputStream)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new GeoJsonParseException("Ожидается JSON-объект");
            }

            boolean typeChecked = false;
            boolean featuresFound = false;

            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String fieldName = parser.getCurrentName();
                parser.nextToken();

                switch (fieldName) {
                    case "type":
                        String rootType = parser.getValueAsString();
                        if (!"FeatureCollection".equals(rootType)) {
                            throw new GeoJsonParseException(
                                    "Ожидается FeatureCollection, "
                                            + "получено: " + rootType);
                        }
                        typeChecked = true;
                        break;
                    case "crs":
                        validateCrs(parser);
                        break;
                    case "features":
                        if (parser.currentToken()
                                != JsonToken.START_ARRAY) {
                            throw new GeoJsonParseException(
                                    "Поле features должно быть "
                                            + "массивом");
                        }
                        featuresFound = true;
                        while (parser.nextToken()
                                != JsonToken.END_ARRAY) {
                            JsonNode feature =
                                    objectMapper.readTree(parser);
                            GeoFeature gf = validateAndMap(
                                    feature, uploadId,
                                    response, logCounter);
                            if (gf != null) {
                                batch.add(gf);
                                if (batch.size() >= batchSize) {
                                    flushBatch(batch, response);
                                }
                            }
                        }
                        break;
                    default:
                        parser.skipChildren();
                        break;
                }
            }

            if (!typeChecked) {
                throw new GeoJsonParseException(
                        "Отсутствует поле type");
            }
            if (!featuresFound) {
                throw new GeoJsonParseException(
                        "Отсутствует поле features");
            }
        }

        flushBatch(batch, response);

        if (response.isErrorsTruncated()) {
            log.warn("Список ошибок обрезан: показано {}, всего {}",
                    response.getErrors().size(),
                    response.getTotalErrorsCount());
        }
        return response;
    }

    /**
     * Сбрасывает накопленный батч в БД. При нарушении целостности переходит к
     * поштучной вставке, чтобы локализовать дубликат id
     * @param batch    список объектов для сохранения
     * @param response накопитель счётчиков и ошибок
     */
    private void flushBatch(List<GeoFeature> batch,
                            GeoJsonUploadResponse response) {
        if (batch.isEmpty()) {
            return;
        }
        try {
            batchWriter.saveBatch(batch);
            for (GeoFeature f : batch) {
                response.incrementCount(f.getObjectType());
                updateBbox(f.getGeometry(), response);
            }
        } catch (DataIntegrityViolationException e) {
            log.warn("Батч не сохранён целиком, переходим "
                    + "к поштучной вставке: {}", e.getMessage());
            for (GeoFeature f : batch) {
                try {
                    batchWriter.saveSingle(f);
                    response.incrementCount(f.getObjectType());
                    updateBbox(f.getGeometry(), response);
                } catch (DataIntegrityViolationException ex) {
                    response.addError(f.getFeatureId(),
                            "дубликат id в рамках загрузки: "
                                    + f.getFeatureId());
                } catch (Exception ex) {
                    log.error("Ошибка сохранения feature {}",
                            f.getFeatureId(), ex);
                    response.addError(f.getFeatureId(),
                            "ошибка сохранения: "
                                    + ex.getMessage());
                }
            }
        } finally {
            batch.clear();
        }
    }

    /**
     * Валидирует один feature и превращает его в {@link GeoFeature}. При любой
     * ошибке возвращает {@code null} и фиксирует сообщение в {@code response}
     * @param feature    узел feature из потока
     * @param uploadId   идентификатор загрузки
     * @param response   накопитель ошибок и счётчиков
     * @param logCounter счётчик для ограничения логирования
     * @return валидный {@link GeoFeature} или {@code null}
     */
    private GeoFeature validateAndMap(JsonNode feature, UUID uploadId,
                                      GeoJsonUploadResponse response,
                                      AtomicInteger logCounter) {
        if (feature == null || !feature.isObject()) {
            addError(response, logCounter, null,
                    "feature не является объектом");
            return null;
        }

        JsonNode properties = feature.get("properties");
        if (properties == null || properties.isNull()
                || !properties.isObject()) {
            addError(response, logCounter, null,
                    "отсутствует или некорректен блок properties");
            return null;
        }

        String featureId = properties.path("id").asText(null);
        if (featureId == null || featureId.isEmpty()) {
            addError(response, logCounter, null, "отсутствует id");
            return null;
        }

        ObjectType objectType = ObjectType.fromString(
                properties.path("object_type").asText(null));
        if (objectType == null) {
            addError(response, logCounter, featureId,
                    "неизвестный или отсутствующий object_type");
            return null;
        }

        JsonNode geometry = feature.get("geometry");
        if (geometry == null || geometry.isNull()
                || !geometry.isObject()) {
            addError(response, logCounter, featureId,
                    "отсутствует или некорректна geometry");
            return null;
        }

        String geometryType = geometry.path("type").asText(null);
        if (geometryType == null) {
            addError(response, logCounter, featureId,
                    "отсутствует geometry.type");
            return null;
        }

        if (!objectType.getAllowedGeometryTypes()
                .contains(geometryType)) {
            addError(response, logCounter, featureId,
                    "geometry.type '" + geometryType
                            + "' не соответствует object_type "
                            + objectType + " (ожидается: "
                            + objectType.getAllowedGeometryTypes()
                            + ")");
            return null;
        }

        String coordError = validateCoordinates(
                geometryType, geometry.get("coordinates"));
        if (coordError != null) {
            addError(response, logCounter, featureId, coordError);
            return null;
        }

        List<String> missing = missingProperties(
                objectType, properties);
        if (!missing.isEmpty()) {
            addError(response, logCounter, featureId,
                    "отсутствуют поля: " + missing);
            return null;
        }

        List<String> typeErrors =
                validateAttributeTypes(properties);
        if (!typeErrors.isEmpty()) {
            addError(response, logCounter, featureId,
                    "некорректные типы полей: " + typeErrors);
            return null;
        }

        return GeoFeature.builder()
                .uploadId(uploadId)
                .featureId(featureId)
                .objectType(objectType)
                .geometryType(geometryType)
                .geometry(geometry)
                .properties(properties)
                .build();
    }

    /**
     * Добавляет ошибку в накопитель и логирует первые
     * {@link #errorLogLimit} сообщений
     */
    private void addError(GeoJsonUploadResponse response,
                          AtomicInteger counter,
                          String featureId, String message) {
        response.addError(featureId, message);
        if (counter.incrementAndGet() <= errorLogLimit) {
            log.warn("Validation error [{}]: {}",
                    featureId, message);
        }
    }

    /**
     * Проверяет CRS файла: допускаются только WGS 84 / CRS84 / EPSG:4326.
     * Иначе бросает исключение
     */
    private void validateCrs(JsonParser parser) throws IOException {
        JsonNode crsNode = objectMapper.readTree(parser);
        if (crsNode == null || crsNode.isNull()) {
            return;
        }
        String crsName = crsNode.path("properties")
                .path("name").asText(null);
        if (crsName == null) {
            return;
        }
        String normalized = crsName.toUpperCase();
        boolean isWgs84 = normalized.contains("CRS84")
                || normalized.contains("EPSG:4326")
                || normalized.contains("EPSG::4326");
        if (!isWgs84) {
            throw new GeoJsonParseException(
                    "Неподдерживаемая CRS: " + crsName
                            + ". Ожидается WGS 84 / EPSG:4326");
        }
    }

    /**
     * Обновляет bbox по геометрии объекта
     */
    private void updateBbox(JsonNode geometry,
                            GeoJsonUploadResponse response) {
        JsonNode coordinates = geometry.get("coordinates");
        if (coordinates == null || coordinates.isNull()) {
            return;
        }
        walkCoordinates(coordinates, response);
    }

    /**
     * Рекурсивно обходит координаты GeoJSON, определяя
     * пары [x, y] и передавая их в bbox
     */
    private void walkCoordinates(JsonNode node,
                                 GeoJsonUploadResponse response) {
        if (node == null || node.isNull() || !node.isArray()) {
            return;
        }
        if (node.size() >= 2 && node.get(0).isNumber()
                && node.get(1).isNumber()) {
            response.updateBbox(node.get(0).asDouble(),
                    node.get(1).asDouble());
            return;
        }
        for (JsonNode child : node) {
            walkCoordinates(child, response);
        }
    }

    /**
     * Возвращает список отсутствующих обязательных
     * атрибутов для указанного типа объекта
     */
    private List<String> missingProperties(ObjectType type,
                                           JsonNode properties) {
        List<String> missing = new ArrayList<>();
        for (String key : type.getRequiredProperties()) {
            if (!properties.hasNonNull(key)) {
                missing.add(key);
            }
        }
        return missing;
    }

    /**
     * Проверяет структуру блока coordinates в зависимости
     * от типа геометрии
     * @return текст ошибки или {@code null}, если структура корректна
     */
    private String validateCoordinates(String geometryType,
                                       JsonNode coordinates) {
        if (coordinates == null || coordinates.isNull()) {
            return "отсутствуют coordinates";
        }
        if (!coordinates.isArray()) {
            return "coordinates должен быть массивом";
        }
        switch (geometryType) {
            case "Point":
                return validatePoint(coordinates, "coordinates");
            case "LineString":
                return validateLineString(coordinates,
                        "coordinates");
            case "Polygon":
                return validatePolygon(coordinates, "coordinates");
            case "MultiPolygon":
                return validateMultiPolygon(coordinates,
                        "coordinates");
            default:
                return null;
        }
    }

    /**
     * Проверяет Point: массив из ≥2 чисел в допустимых
     * диапазонах долготы и широты
     */
    private String validatePoint(JsonNode point, String path) {
        if (!point.isArray() || point.size() < 2) {
            return path + ": Point должен содержать "
                    + "минимум 2 координаты";
        }
        if (!point.get(0).isNumber() || !point.get(1).isNumber()) {
            return path + ": координаты Point "
                    + "должны быть числами";
        }
        double lon = point.get(0).asDouble();
        double lat = point.get(1).asDouble();
        if (lon < -180.0 || lon > 180.0) {
            return path + ": долгота вне диапазона "
                    + "[-180, 180]";
        }
        if (lat < -90.0 || lat > 90.0) {
            return path + ": широта вне диапазона [-90, 90]";
        }
        return null;
    }

    /**
     * Проверяет LineString: минимум 2 корректные точки
     */
    private String validateLineString(JsonNode line, String path) {
        if (!line.isArray() || line.size() < 2) {
            return path + ": LineString должен содержать "
                    + "минимум 2 точки";
        }
        for (int i = 0; i < line.size(); i++) {
            String err = validatePoint(line.get(i),
                    path + "[" + i + "]");
            if (err != null) {
                return err;
            }
        }
        return null;
    }

    /**
     * Проверяет Polygon: минимум одно корректное кольцо
     */
    private String validatePolygon(JsonNode polygon, String path) {
        if (!polygon.isArray() || polygon.size() < 1) {
            return path + ": Polygon должен содержать "
                    + "минимум 1 кольцо";
        }
        for (int i = 0; i < polygon.size(); i++) {
            String err = validateLinearRing(polygon.get(i),
                    path + "[" + i + "]");
            if (err != null) {
                return err;
            }
        }
        return null;
    }

    /**
     * Проверяет MultiPolygon: минимум один корректный полигон
     */
    private String validateMultiPolygon(JsonNode multi,
                                        String path) {
        if (!multi.isArray() || multi.size() < 1) {
            return path + ": MultiPolygon должен содержать "
                    + "минимум 1 полигон";
        }
        for (int i = 0; i < multi.size(); i++) {
            String err = validatePolygon(multi.get(i),
                    path + "[" + i + "]");
            if (err != null) {
                return err;
            }
        }
        return null;
    }

    /**
     * Проверяет кольцо: ≥4 корректных точек, первая и последняя точки
     * должны совпадать
     */
    private String validateLinearRing(JsonNode ring, String path) {
        if (!ring.isArray() || ring.size() < 4) {
            return path + ": кольцо должно содержать "
                    + "минимум 4 точки";
        }
        for (int i = 0; i < ring.size(); i++) {
            String err = validatePoint(ring.get(i),
                    path + "[" + i + "]");
            if (err != null) {
                return err;
            }
        }
        JsonNode first = ring.get(0);
        JsonNode last = ring.get(ring.size() - 1);
        if (Double.compare(first.get(0).asDouble(),
                last.get(0).asDouble()) != 0
                || Double.compare(first.get(1).asDouble(),
                last.get(1).asDouble()) != 0) {
            return path + ": кольцо должно быть замкнуто "
                    + "(первая и последняя точки должны "
                    + "совпадать)";
        }
        return null;
    }

    /**
     * Проверяет типы известных атрибутов, если они присутствуют в properties
     */
    private List<String> validateAttributeTypes(
            JsonNode properties) {
        List<String> errors = new ArrayList<>();
        checkString(properties, "id", errors);
        checkString(properties, "object_type", errors);
        checkInteger(properties, "diameter", errors);
        checkNumber(properties, "flow_tph", errors);
        checkNumber(properties, "heat_load", errors);
        checkString(properties, "upstream_object_id", errors);
        checkString(properties, "oks_id", errors);
        checkString(properties, "restriction_type", errors);
        return errors;
    }

    /**
     * Проверяет, что поле, если задано, является целым числом
     */
    private void checkInteger(JsonNode node, String field,
                              List<String> errors) {
        JsonNode v = node.get(field);
        if (v != null && !v.isNull() && !v.isIntegralNumber()) {
            errors.add(field + " должен быть целым числом");
        }
    }

    /**
     * Проверяет, что поле, если задано, является числом
     */
    private void checkNumber(JsonNode node, String field,
                             List<String> errors) {
        JsonNode v = node.get(field);
        if (v != null && !v.isNull() && !v.isNumber()) {
            errors.add(field + " должен быть числом");
        }
    }

    /**
     * Проверяет, что поле, если задано, является строкой
     */
    private void checkString(JsonNode node, String field,
                             List<String> errors) {
        JsonNode v = node.get(field);
        if (v != null && !v.isNull() && !v.isTextual()) {
            errors.add(field + " должен быть строкой");
        }
    }
}
