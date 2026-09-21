package ru.moscow.heat.geojson.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.exception.GeoJsonParseException;

/**
 * Конвертер между представлениями геометрии:
 * JsonNode (GeoJSON) &lt;-&gt; JTS {@link Geometry}.
 * Реализован на прямом построении через {@link GeometryFactory}
 * без использования {@code GeoJsonReader} / {@code GeoJsonWriter}
 * из {@code jts-io-common}: последние сериализуют координаты через
 * {@code DecimalFormat} с ограниченным числом знаков, из-за чего
 * теряются последние разряды double и нарушается round-trip.
 * Прямая работа с {@link JsonNode} сохраняет точное значение координат
 */
@Service
@RequiredArgsConstructor
public class GeometryConverterService {

    private final ObjectMapper objectMapper;
    private final GeometryFactory geometryFactory =
            new GeometryFactory();

    /**
     * Преобразует GeoJSON-геометрию в JTS.
     * SRID не проставляется - вызывающий код сам выставляет нужное
     * значение (обычно 4326 для входных данных)
     * @param node узел {@code geometry} из фичи
     * @return JTS-геометрия
     * @throws GeoJsonParseException если структура некорректна
     *                               или тип не поддерживается
     */
    public Geometry fromGeoJson(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new GeoJsonParseException("geometry отсутствует");
        }
        String type = node.path("type").asText(null);
        if (type == null) {
            throw new GeoJsonParseException(
                    "geometry.type отсутствует");
        }
        JsonNode coords = node.get("coordinates");
        if (coords == null || coords.isNull()) {
            throw new GeoJsonParseException(
                    "geometry.coordinates отсутствует");
        }
        try {
            switch (type) {
                case "Point":
                    return geometryFactory.createPoint(
                            readCoordinate(coords));
                case "LineString":
                    return geometryFactory.createLineString(
                            readCoordinateArray(coords));
                case "MultiLineString":
                    return readMultiLineString(coords);
                case "Polygon":
                    return readPolygon(coords);
                case "MultiPolygon":
                    return readMultiPolygon(coords);
                default:
                    throw new GeoJsonParseException(
                            "Неподдерживаемый тип геометрии: "
                                    + type);
            }
        } catch (GeoJsonParseException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new GeoJsonParseException(
                    "Не удалось разобрать геометрию: "
                            + e.getMessage(), e);
        }
    }

    /**
     * Преобразует JTS-геометрию в GeoJSON-узел
     * @param geometry JTS-геометрия или {@code null}
     * @return узел GeoJSON или {@code null}, если геометрия пуста
     * @throws IllegalStateException если тип не поддерживается
     */
    public JsonNode toGeoJson(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        ObjectNode out = objectMapper.createObjectNode();
        if (geometry instanceof Point) {
            out.put("type", "Point");
            out.set("coordinates",
                    writeCoordinate(geometry.getCoordinate()));
        } else if (geometry instanceof LineString) {
            out.put("type", "LineString");
            out.set("coordinates",
                    writeCoordinateArray(
                            geometry.getCoordinates()));
        } else if (geometry instanceof MultiLineString) {
            out.put("type", "MultiLineString");
            ArrayNode lines = objectMapper.createArrayNode();
            for (int i = 0;
                 i < geometry.getNumGeometries(); i++) {
                lines.add(writeCoordinateArray(
                        geometry.getGeometryN(i).getCoordinates()));
            }
            out.set("coordinates", lines);
        } else if (geometry instanceof Polygon) {
            out.put("type", "Polygon");
            out.set("coordinates",
                    writePolygon((Polygon) geometry));
        } else if (geometry instanceof MultiPolygon) {
            out.put("type", "MultiPolygon");
            ArrayNode polys = objectMapper.createArrayNode();
            for (int i = 0;
                 i < geometry.getNumGeometries(); i++) {
                polys.add(writePolygon(
                        (Polygon) geometry.getGeometryN(i)));
            }
            out.set("coordinates", polys);
        } else {
            throw new IllegalStateException(
                    "Неподдерживаемый тип геометрии: "
                            + geometry.getGeometryType());
        }
        return out;
    }

    /**
     * Читает одиночную координату из узла {@code [x, y]}
     * @param node узел GeoJSON с парой чисел
     * @return координата JTS
     * @throws GeoJsonParseException если структура некорректна
     */
    private Coordinate readCoordinate(JsonNode node) {
        if (!node.isArray() || node.size() < 2) {
            throw new GeoJsonParseException(
                    "Точка должна содержать минимум 2 координаты");
        }
        if (!node.get(0).isNumber() || !node.get(1).isNumber()) {
            throw new GeoJsonParseException(
                    "Координаты точки должны быть числами");
        }
        return new Coordinate(
                node.get(0).asDouble(),
                node.get(1).asDouble());
    }

    /**
     * Читает массив координат {@code [[x1,y1], [x2,y2], ...]}
     * @param arr узел-массив координат
     * @return массив координат JTS
     * @throws GeoJsonParseException если узел не является массивом
     */
    private Coordinate[] readCoordinateArray(JsonNode arr) {
        if (!arr.isArray()) {
            throw new GeoJsonParseException(
                    "Ожидается массив координат");
        }
        Coordinate[] result = new Coordinate[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            result[i] = readCoordinate(arr.get(i));
        }
        return result;
    }

    /**
     * Читает {@code MultiLineString} - массив линий
     * {@code [[[x,y],...], [[x,y],...]]}
     * @param lines узел-массив линий
     * @return JTS {@link MultiLineString}
     * @throws GeoJsonParseException если массив пуст
     */
    private MultiLineString readMultiLineString(JsonNode lines) {
        if (!lines.isArray() || lines.size() < 1) {
            throw new GeoJsonParseException(
                    "MultiLineString должен содержать хотя бы "
                            + "одну линию");
        }
        LineString[] result = new LineString[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            result[i] = geometryFactory.createLineString(
                    readCoordinateArray(lines.get(i)));
        }
        return geometryFactory.createMultiLineString(result);
    }

    /**
     * Читает {@code Polygon} - массив колец, где первое кольцо
     * внешнее, остальные - отверстия
     * @param rings узел-массив колец
     * @return JTS {@link Polygon}
     * @throws GeoJsonParseException если массив пуст
     */
    private Polygon readPolygon(JsonNode rings) {
        if (!rings.isArray() || rings.size() < 1) {
            throw new GeoJsonParseException(
                    "Polygon должен содержать хотя бы одно кольцо");
        }
        LinearRing shell = geometryFactory.createLinearRing(
                readCoordinateArray(rings.get(0)));
        LinearRing[] holes = new LinearRing[rings.size() - 1];
        for (int i = 1; i < rings.size(); i++) {
            holes[i - 1] = geometryFactory.createLinearRing(
                    readCoordinateArray(rings.get(i)));
        }
        return geometryFactory.createPolygon(shell, holes);
    }

    /**
     * Читает {@code MultiPolygon} - массив полигонов, каждый из
     * которых задан массивом колец
     * @param polygons узел-массив полигонов
     * @return JTS {@link MultiPolygon}
     * @throws GeoJsonParseException если массив пуст
     */
    private MultiPolygon readMultiPolygon(JsonNode polygons) {
        if (!polygons.isArray() || polygons.size() < 1) {
            throw new GeoJsonParseException(
                    "MultiPolygon должен содержать хотя бы "
                            + "один полигон");
        }
        Polygon[] result = new Polygon[polygons.size()];
        for (int i = 0; i < polygons.size(); i++) {
            result[i] = readPolygon(polygons.get(i));
        }
        return geometryFactory.createMultiPolygon(result);
    }

    /**
     * Записывает координату JTS в узел {@code [x, y]}
     * @param c координата
     * @return узел GeoJSON с парой чисел
     */
    private ArrayNode writeCoordinate(Coordinate c) {
        ArrayNode arr = objectMapper.createArrayNode();
        arr.add(c.x);
        arr.add(c.y);
        return arr;
    }

    /**
     * Записывает массив координат в узел {@code [[x1,y1], [x2,y2], ...]}
     * @param coords массив координат
     * @return узел-массив GeoJSON
     */
    private ArrayNode writeCoordinateArray(Coordinate[] coords) {
        ArrayNode arr = objectMapper.createArrayNode();
        for (Coordinate c : coords) {
            arr.add(writeCoordinate(c));
        }
        return arr;
    }

    /**
     * Записывает полигон в узел-массив колец: первым идет внешнее
     * кольцо, далее - отверстия
     * @param p полигон JTS
     * @return узел-массив GeoJSON с кольцами
     */
    private ArrayNode writePolygon(Polygon p) {
        ArrayNode rings = objectMapper.createArrayNode();
        rings.add(writeCoordinateArray(
                p.getExteriorRing().getCoordinates()));
        for (int i = 0; i < p.getNumInteriorRing(); i++) {
            rings.add(writeCoordinateArray(
                    p.getInteriorRingN(i).getCoordinates()));
        }
        return rings;
    }
}
