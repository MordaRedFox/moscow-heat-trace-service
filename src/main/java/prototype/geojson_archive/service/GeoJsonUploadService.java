package prototype.geojson_archive.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import prototype.geojson_archive.dto.InputFeature;
import prototype.geojson_archive.ObjectType;
import prototype.geojson_archive.RequiredAttributes;
import prototype.geojson_archive.dto.GeoJsonUploadSummary;
import prototype.geojson_archive.exception.GeoJsonParseException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class GeoJsonUploadService {

    private final ObjectMapper objectMapper;

    public GeoJsonUploadService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GeoJsonUploadSummary processStream(InputStream inputStream) throws IOException, GeoJsonParseException {
        JsonFactory factory = objectMapper.getFactory();

        try (JsonParser parser = factory.createParser(inputStream)) {
            GeoJsonUploadSummary summary = new GeoJsonUploadSummary();
            double[] bboxAcc = {
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY
            };
            boolean featuresFound = false;

            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new GeoJsonParseException("Файл должен начинаться с JSON-объекта");
            }

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.getCurrentName();
                parser.nextToken();

                if ("type".equals(fieldName)) {
                    String rootType = parser.getValueAsString();
                    if (!"FeatureCollection".equals(rootType)) {
                        throw new GeoJsonParseException("Ожидается FeatureCollection, получено: " + rootType);
                    }
                } else if ("features".equals(fieldName)) {
                    if (parser.currentToken() != JsonToken.START_ARRAY) {
                        throw new GeoJsonParseException("Поле features должно быть массивом");
                    }
                    featuresFound = true;

                    int index = 0;
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        JsonNode featureNode = objectMapper.readTree(parser); // читаем только один Feature
                        InputFeature feature = mapToFeature(featureNode, index);

                        List<String> errors = validate(feature);
                        if (!errors.isEmpty()) {
                            summary.addError(index, feature.getId(), errors);
                        } else {
                            summary.incrementCount(feature.getObjectType());
                            accumulateBbox(featureNode.get("geometry"), bboxAcc);
                        }
                        index++;
                    }
                } else {
                    parser.skipChildren();
                }
            }

            if (!featuresFound) {
                throw new GeoJsonParseException("В файле отсутствует поле features");
            }
            if (bboxAcc[0] != Double.POSITIVE_INFINITY) {
                summary.setBbox(bboxAcc);
            }
            return summary;
        }
    }

    private InputFeature mapToFeature(JsonNode node, int index) {
        JsonNode props = node.path("properties");
        InputFeature f = new InputFeature();
        f.setIndex(index);
        f.setId(props.path("id").isMissingNode() ? null : props.path("id").asText(null));
        f.setObjectType(ObjectType.fromCode(props.path("object_type").asText(null)));
        f.setGeometryType(node.path("geometry").path("type").asText(null));
        f.setRawProperties(props);
        return f;
    }

    private List<String> validate(InputFeature feature) {
        List<String> errors = new ArrayList<>();

        if (feature.getId() == null || feature.getId().isBlank()) {
            errors.add("Отсутствует обязательный атрибут 'id'");
        }
        if (feature.getObjectType() == null) {
            errors.add("Неизвестный или отсутствующий 'object_type'");
            return errors; // остальное проверять бессмысленно
        }

        JsonNode props = feature.getRawProperties();
        for (String attr : RequiredAttributes.forType(feature.getObjectType())) {
            JsonNode value = props.get(attr);
            if (value == null || value.isNull()) {
                errors.add("Отсутствует обязательный атрибут '" + attr + "' для типа "
                        + feature.getObjectType().getCode());
            }
        }
        return errors;
    }

    private void accumulateBbox(JsonNode geometry, double[] bbox) {
        if (geometry == null || geometry.isNull() || geometry.path("coordinates").isMissingNode()) {
            return;
        }
        walkCoordinates(geometry.path("coordinates"), bbox);
    }

    private void walkCoordinates(JsonNode node, double[] bbox) {
        if (!node.isArray() || node.isEmpty()) return;

        if (node.get(0).isNumber()) {
            // это координатная пара [x, y] (возможно, третий элемент — высота, игнорируем)
            double x = node.get(0).asDouble();
            double y = node.get(1).asDouble();
            if (x < bbox[0]) bbox[0] = x;
            if (y < bbox[1]) bbox[1] = y;
            if (x > bbox[2]) bbox[2] = x;
            if (y > bbox[3]) bbox[3] = y;
        } else {
            for (JsonNode child : node) {
                walkCoordinates(child, bbox);
            }
        }
    }
}