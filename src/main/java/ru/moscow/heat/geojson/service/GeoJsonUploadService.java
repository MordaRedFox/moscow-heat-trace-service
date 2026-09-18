package ru.moscow.heat.geojson.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class GeoJsonUploadService {

    private final GeoFeatureManager geoFeatureManager;
    private final ObjectMapper objectMapper;

    public GeoJsonUploadResponse processStream(InputStream inputStream) throws IOException {
        GeoJsonUploadResponse response = new GeoJsonUploadResponse();

        try (JsonParser parser = objectMapper.getFactory().createParser(inputStream)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new GeoJsonParseException("Ожидается JSON-объект");
            }

            boolean typeChecked = false;
            boolean featuresFound = false;

            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String fieldName = parser.getCurrentName();
                parser.nextToken(); // перейти к значению поля

                switch (fieldName) {
                    case "type":
                        String rootType = parser.getValueAsString();
                        if (!"FeatureCollection".equals(rootType)) {
                            throw new GeoJsonParseException(
                                    "Ожидается FeatureCollection, получено: " + rootType);
                        }
                        typeChecked = true;
                        break;
                    case "crs":
                        validateCrs(parser, response);
                        break;
                    case "features":
                        if (parser.currentToken() != JsonToken.START_ARRAY) {
                            throw new GeoJsonParseException("Поле features должно быть массивом");
                        }
                        featuresFound = true;
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            JsonNode feature = objectMapper.readTree(parser);
                            processFeature(feature, response);
                        }
                        break;
                    default:
                        parser.skipChildren();
                        break;
                }
            }

            if (!typeChecked) {
                throw new GeoJsonParseException("Отсутствует поле type");
            }
            if (!featuresFound) {
                throw new GeoJsonParseException("Отсутствует поле features");
            }
        }

        return response;
    }

    private void validateCrs(JsonParser parser, GeoJsonUploadResponse response) throws IOException {
        JsonNode crsNode = objectMapper.readTree(parser);
        if (crsNode == null || crsNode.isNull()) {
            return;
        }
        String crsName = crsNode.path("properties").path("name").asText(null);
        if (crsName == null) {
            return;
        }
        String normalized = crsName.toUpperCase();
        boolean isWgs84 = normalized.contains("CRS84")
                || normalized.contains("EPSG:4326")
                || normalized.contains("EPSG::4326");
        if (!isWgs84) {
            response.addError(null,
                    "Неожиданная CRS: " + crsName + " (ожидается WGS 84 / EPSG:4326)");
        }
    }

    private void processFeature(JsonNode feature, GeoJsonUploadResponse response) {
        if (feature == null || !feature.isObject()) {
            response.addError(null, "feature не является объектом");
            return;
        }

        JsonNode properties = feature.get("properties");
        if (properties == null || properties.isNull() || !properties.isObject()) {
            response.addError(null, "отсутствует или некорректен блок properties");
            return;
        }

        String featureId = properties.path("id").asText(null);
        if (featureId == null || featureId.isEmpty()) {
            response.addError(null, "отсутствует id");
            return;
        }

        ObjectType objectType = ObjectType.fromString(properties.path("object_type").asText(null));
        if (objectType == null) {
            response.addError(featureId, "неизвестный или отсутствующий object_type");
            return;
        }

        JsonNode geometry = feature.get("geometry");
        if (geometry == null || geometry.isNull() || !geometry.isObject()) {
            response.addError(featureId, "отсутствует или некорректна geometry");
            return;
        }

        String geometryType = geometry.path("type").asText(null);
        if (geometryType == null) {
            response.addError(featureId, "отсутствует geometry.type");
            return;
        }

        if (!objectType.getAllowedGeometryTypes().contains(geometryType)) {
            response.addError(featureId,
                    "geometry.type '" + geometryType + "' не соответствует object_type "
                            + objectType + " (ожидается: " + objectType.getAllowedGeometryTypes() + ")");
            return;
        }

        List<String> missing = missingProperties(objectType, properties);
        if (!missing.isEmpty()) {
            response.addError(featureId, "отсутствуют поля: " + missing);
            return;
        }

        GeoFeature geoFeature = GeoFeature.builder()
                .featureId(featureId)
                .objectType(objectType)
                .geometryType(geometryType)
                .geometry(geometry)
                .properties(properties)
                .build();

        try {
            geoFeatureManager.save(geoFeature);
        } catch (DataIntegrityViolationException e) {
            response.addError(featureId, "дубликат id: " + featureId);
            return;
        }

        response.incrementCount(objectType);
        updateBbox(geometry, response);
    }

    private void updateBbox(JsonNode geometry, GeoJsonUploadResponse response) {
        JsonNode coordinates = geometry.get("coordinates");
        if (coordinates == null || coordinates.isNull()) {
            return;
        }
        walkCoordinates(coordinates, response);
    }

    private void walkCoordinates(JsonNode node, GeoJsonUploadResponse response) {
        if (node == null || node.isNull() || !node.isArray()) {
            return;
        }
        if (node.size() >= 2 && node.get(0).isNumber() && node.get(1).isNumber()) {
            double x = node.get(0).asDouble();
            double y = node.get(1).asDouble();
            response.updateBbox(x, y);
            return;
        }
        for (JsonNode child : node) {
            walkCoordinates(child, response);
        }
    }

    private List<String> missingProperties(ObjectType type, JsonNode properties) {
        List<String> missing = new ArrayList<>();
        for (String key : type.getRequiredProperties()) {
            if (!properties.hasNonNull(key)) {
                missing.add(key);
            }
        }
        return missing;
    }
}
