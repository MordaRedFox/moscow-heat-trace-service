package ru.moscow.heat.geojson.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Unit-тесты конвертера GeoJSON → JTS → GeoJSON (round-trip)
 */
class GeometryConverterServiceTest {

    /**
     * Допуск сравнения координат. 1e-9° по широте ≈ 0.1 мм —
     * заведомо меньше погрешности любой практической задачи
     */
    private static final double TOLERANCE = 1e-9;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeometryConverterService converter =
            new GeometryConverterService(objectMapper);

    /**
     * Point: разбор, чтение координат, round-trip
     * @throws Exception при разборе JSON
     */
    @Test
    void pointRoundTrip() throws Exception {
        JsonNode node = objectMapper.readTree(
                "{\"type\":\"Point\",\"coordinates\":"
                        + "[37.6344054041544,55.6994810644531]}");

        Geometry geometry = converter.fromGeoJson(node);

        assertInstanceOf(Point.class, geometry);
        assertEquals(37.6344054041544,
                geometry.getCoordinate().x, TOLERANCE);
        assertEquals(55.6994810644531,
                geometry.getCoordinate().y, TOLERANCE);

        JsonNode out = converter.toGeoJson(geometry);
        Geometry back = converter.fromGeoJson(out);

        assertCoordinatesEqual(geometry, back);
    }

    /**
     * LineString: round-trip сохраняет число точек и координаты
     * @throws Exception при разборе JSON
     */
    @Test
    void lineStringRoundTrip() throws Exception {
        JsonNode node = objectMapper.readTree(
                "{\"type\":\"LineString\",\"coordinates\":["
                        + "[37.64079875310378,55.700214414852034],"
                        + "[37.64074858679058,55.7001284272387]]}");

        Geometry geometry = converter.fromGeoJson(node);

        assertInstanceOf(LineString.class, geometry);
        assertEquals(2, geometry.getNumPoints());

        Geometry back = converter.fromGeoJson(
                converter.toGeoJson(geometry));

        assertCoordinatesEqual(geometry, back);
    }

    /**
     * MultiPolygon: round-trip сохраняет число полигонов
     * и координаты внешних колец
     * @throws Exception при разборе JSON
     */
    @Test
    void multiPolygonRoundTrip() throws Exception {
        JsonNode node = objectMapper.readTree(
                "{\"type\":\"MultiPolygon\",\"coordinates\":[[["
                        + "[37.63,55.69],[37.64,55.69],"
                        + "[37.64,55.70],[37.63,55.70],"
                        + "[37.63,55.69]]]]}");

        Geometry geometry = converter.fromGeoJson(node);

        assertInstanceOf(MultiPolygon.class, geometry);
        assertEquals(1, geometry.getNumGeometries());

        Geometry back = converter.fromGeoJson(
                converter.toGeoJson(geometry));

        assertCoordinatesEqual(geometry, back);
    }

    /**
     * Сравнивает две геометрии покомпонентно с допуском
     * {@link #TOLERANCE}. Работает для Point, LineString и
     * MultiPolygon одинаково - по всем вершинам всех частей
     * @param expected ожидаемая геометрия
     * @param actual   фактическая геометрия
     */
    private void assertCoordinatesEqual(Geometry expected,
                                        Geometry actual) {
        Coordinate[] a = expected.getCoordinates();
        Coordinate[] b = actual.getCoordinates();
        assertEquals(a.length, b.length,
                "число координат после round-trip изменилось");
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i].x, b[i].x, TOLERANCE,
                    "x-координата #" + i + " изменилась");
            assertEquals(a[i].y, b[i].y, TOLERANCE,
                    "y-координата #" + i + " изменилась");
        }
    }
}
