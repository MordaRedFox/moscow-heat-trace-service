package ru.moscow.heat.geojson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Фабрика для построения тестовых GeoJSON-документов.
 * Позволяет точечно конструировать фичи без ручных строковых
 * литералов JSON. Все методы статические, экземпляр класса
 * не создается
 */
public final class TestGeoJsonFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestGeoJsonFactory() {
    }

    /**
     * Создает пустую коллекцию {@code FeatureCollection}
     * @return корневой узел с полями {@code type} и пустым
     *         массивом {@code features}
     */
    public static ObjectNode featureCollection() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", "FeatureCollection");
        root.set("features", MAPPER.createArrayNode());
        return root;
    }

    /**
     * Создает фичу с минимальным набором свойств: {@code id}
     * и {@code object_type}. Каждый параметр может быть
     * {@code null}, тогда соответствующее поле не добавляется
     * @param id           идентификатор фичи
     * @param objectType   значение {@code object_type}
     * @param geometryType тип геометрии
     * @param coordinates  плоский массив координат
     * @return узел фичи
     */
    public static ObjectNode feature(String id,
                                     String objectType,
                                     String geometryType,
                                     double... coordinates) {
        ObjectNode f = MAPPER.createObjectNode();
        f.put("type", "Feature");

        ObjectNode props = f.putObject("properties");
        if (id != null) {
            props.put("id", id);
        }
        if (objectType != null) {
            props.put("object_type", objectType);
        }

        ObjectNode geom = f.putObject("geometry");
        geom.put("type", geometryType);
        geom.set("coordinates",
                coordinatesNode(geometryType, coordinates));

        return f;
    }

    /**
     * Создает фичу с готовым блоком {@code properties}.
     * Используется, когда нужно полностью контролировать состав
     * атрибутов (например, для тестов с отсутствующими полями)
     * @param geometryType тип геометрии
     * @param properties   готовый узел свойств
     * @param coordinates  плоский массив координат
     * @return узел фичи
     */
    public static ObjectNode featureWithRawProps(
            String geometryType,
            ObjectNode properties,
            double... coordinates) {
        ObjectNode f = MAPPER.createObjectNode();
        f.put("type", "Feature");
        f.set("properties", properties);

        ObjectNode geom = f.putObject("geometry");
        geom.put("type", geometryType);
        geom.set("coordinates",
                coordinatesNode(geometryType, coordinates));

        return f;
    }

    /**
     * Добавляет готовую фичу в массив {@code features}
     * коллекции
     * @param collection коллекция {@code FeatureCollection}
     * @param feature    добавляемая фича
     */
    public static void addFeature(ObjectNode collection,
                                  ObjectNode feature) {
        ((ArrayNode) collection.get("features")).add(feature);
    }

    /**
     * Добавляет блок {@code crs} с указанным именем в коллекцию
     * @param collection коллекция {@code FeatureCollection}
     * @param crsName    имя CRS (например, {@code EPSG:4326})
     */
    public static void addCrs(ObjectNode collection, String crsName) {
        ObjectNode crs = collection.putObject("crs");
        crs.put("type", "name");
        crs.putObject("properties").put("name", crsName);
    }

    /**
     * Удаляет корневое поле {@code type} из коллекции.
     * Используется в негативных тестах
     * @param collection коллекция {@code FeatureCollection}
     */
    public static void removeType(ObjectNode collection) {
        collection.remove("type");
    }

    /**
     * Удаляет корневое поле {@code features} из коллекции.
     * Используется в негативных тестах
     * @param collection коллекция {@code FeatureCollection}
     */
    public static void removeFeatures(ObjectNode collection) {
        collection.remove("features");
    }

    /**
     * Сериализует узел в строку JSON
     * @param node любой JSON-узел
     * @return строковое представление
     * @throws RuntimeException при ошибке сериализации
     */
    public static String toJson(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Сериализует узел в массив байт (UTF-8).
     * Используется для передачи в {@code ByteArrayInputStream}
     * и {@code MockMultipartFile}
     * @param node любой JSON-узел
     * @return массив байт
     */
    public static byte[] toBytes(JsonNode node) {
        return toJson(node).getBytes();
    }

    /**
     * Преобразует плоский массив координат в структуру,
     * соответствующую типу геометрии GeoJSON
     * <p>Для {@code Point} — массив чисел. Для {@code LineString},
     * {@code MultiPoint} — массив точек. Для {@code Polygon},
     * {@code MultiLineString} — одно кольцо. Для
     * {@code MultiPolygon} — один полигон с одним кольцом
     * @param geometryType тип геометрии
     * @param coords       плоский массив координат
     * @return узел с координатами
     */
    private static JsonNode coordinatesNode(String geometryType,
                                            double... coords) {
        ArrayNode root = MAPPER.createArrayNode();
        switch (geometryType) {
            case "Point":
                for (double c : coords) {
                    root.add(c);
                }
                return root;
            case "LineString":
            case "MultiPoint":
                for (int i = 0; i + 1 < coords.length; i += 2) {
                    ArrayNode pt = root.addArray();
                    pt.add(coords[i]);
                    pt.add(coords[i + 1]);
                }
                return root;
            case "Polygon":
            case "MultiLineString":
                ArrayNode ring = root.addArray();
                for (int i = 0; i + 1 < coords.length; i += 2) {
                    ArrayNode pt = ring.addArray();
                    pt.add(coords[i]);
                    pt.add(coords[i + 1]);
                }
                return root;
            case "MultiPolygon":
                ArrayNode polygon = root.addArray();
                ArrayNode ring2 = polygon.addArray();
                for (int i = 0; i + 1 < coords.length; i += 2) {
                    ArrayNode pt = ring2.addArray();
                    pt.add(coords[i]);
                    pt.add(coords[i + 1]);
                }
                return root;
            default:
                return root;
        }
    }
}
