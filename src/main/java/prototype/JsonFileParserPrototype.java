package prototype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.moscow.heat.geojson.dto.GeoJsonUploadResponse;
import ru.moscow.heat.geojson.entity.GeoFeature;
import ru.moscow.heat.geojson.ObjectType;
import ru.moscow.heat.geojson.exception.GeoJsonParseException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JsonFileParserPrototype {
    public static void main(String[] args) throws GeoJsonParseException {
        Path path = Paths.get("src/main/java/prototype/scratch.json");

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            JsonNode rootNode = objectMapper.readTree(path.toFile());

            // Валидация типа GeoJson
            String rootType = rootNode.get("type").asText();
            if (!"FeatureCollection".equals(rootType)) {
                throw new GeoJsonParseException("Ожидается FeatureCollection, получено: " + rootType);
            }

            // Валидация features
            JsonNode features = rootNode.get("features");
            if (features == null || !features.isArray()) {
                throw new GeoJsonParseException("Поле features должно быть массивом");
            }

            GeoJsonUploadResponse response = new GeoJsonUploadResponse();

            for (JsonNode feature : features) {
                ObjectType objectType = ObjectType.fromString(feature.get("properties").get("object_type").asText());
                String geometryType = feature.get("geometry").get("type").asText();
                JsonNode geometry = feature.get("geometry");
                JsonNode properties = feature.get("properties");

                boolean valid =
                        geometryType != null
                                && objectType != null
                                && geometry != null
                                && properties != null;

                if (valid) {
                    GeoFeature geoFeature = GeoFeature.builder()
                            .objectType(objectType)
                            .geometryType(geometryType)
                            .geometry(geometry)
                            .properties(properties)
                            .build();
                    response.incrementCount(objectType);

                    System.out.println(geoFeature);
                }

            }
            System.out.println(response.getCountsByType());

        } catch (IOException e) {
            System.err.println("Произошла ошибка при чтении или парсинге файла:");
            e.printStackTrace();
        }
    }
}
