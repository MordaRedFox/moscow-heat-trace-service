package ru.moscow.heat.geojson.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.ObjectType;
import ru.moscow.heat.geojson.dto.GeoJsonUploadResponse;
import ru.moscow.heat.geojson.entity.GeoFeature;
import ru.moscow.heat.geojson.exception.GeoJsonParseException;
import ru.moscow.heat.geojson.repository.GeoFeatureRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GeoJsonUploadService {
    private final GeoFeatureManager geoFeatureManager;
    private final ObjectMapper objectMapper;

//    public GeoJsonUploadResponse processStream(InputStream inputStream) throws IOException, GeoJsonParseException {
////        JsonNode rootNode = objectMapper.readTree(inputStream);
//
//        // Валидация типа GeoJson
//        String rootType = rootNode.path("type").asText(null);
//        if (!"FeatureCollection".equals(rootType)) {
//            throw new GeoJsonParseException("Ожидается FeatureCollection, получено: " + rootType);
//        }
//
//        // Валидация features
//        JsonNode features = rootNode.get("features");
//        if (features == null || !features.isArray()) {
//            throw new GeoJsonParseException("Поле features должно быть массивом");
//        }
//
//        GeoJsonUploadResponse response = new GeoJsonUploadResponse();
//
//        try (JsonParser parser = objectMapper.getFactory().createParser(inputStream)) {
//            while (parser.nextToken() != JsonToken.END_ARRAY) {
//                JsonNode feature = objectMapper.readTree(parser);
//
//                ObjectType objectType = ObjectType.fromString(feature.get("properties").get("object_type").asText());
//
//                JsonNode geometry = feature.get("geometry");
//                String geometryType = feature.path("geometry").path("type").asText(null);
//
//                JsonNode properties = feature.get("properties");
//                String featureId = feature.path("properties").path("id").asText(null);
//
//                // Валидация feature
//                List<String> missing = List.of();
//                boolean valid =
//                        featureId != null
//                                && geometryType != null
//                                && objectType != null
//                                && geometry != null
//                                && properties != null
//                                && (missing = missingProperties(objectType, properties)).isEmpty();
//
//                if (valid) {
//                    GeoFeature geoFeature = GeoFeature.builder()
//                            .featureId(featureId)
//                            .objectType(objectType)
//                            .geometryType(geometryType)
//                            .geometry(geometry)
//                            .properties(properties)
//                            .build();
//                    geoFeatureRepository.save(geoFeature);
//
//                    response.incrementCount(objectType);
//                } else if (!missing.isEmpty()) {
//                    response.addError(featureId, "отсутствуют поля: " + missing);
//                }
//            }
//            return response;            }
//        }

    public GeoJsonUploadResponse processStream(InputStream inputStream) throws IOException, GeoJsonParseException {
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
                            throw new GeoJsonParseException("Ожидается FeatureCollection, получено: " + rootType);
                        }
                        typeChecked = true;
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

    private void processFeature(JsonNode feature, GeoJsonUploadResponse response) {
        ObjectType objectType = ObjectType.fromString(
                feature.path("properties").path("object_type").asText(null));
        JsonNode geometry = feature.get("geometry");
        String geometryType = feature.path("geometry").path("type").asText(null);
        JsonNode properties = feature.get("properties");
        String featureId = feature.path("properties").path("id").asText(null);

        List<String> missing = List.of();
        boolean valid =
                featureId != null
                        && geometryType != null
                        && objectType != null
                        && geometry != null
                        && properties != null
                        && (missing = missingProperties(objectType, properties)).isEmpty();

        if (valid) {
            GeoFeature geoFeature = GeoFeature.builder()
                    .featureId(featureId)
                    .objectType(objectType)
                    .geometryType(geometryType)
                    .geometry(geometry)
                    .properties(properties)
                    .build();
            geoFeatureManager.save(geoFeature);
            response.incrementCount(objectType);
        } else if (!missing.isEmpty()) {
            response.addError(featureId, "отсутствуют поля: " + missing);
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
