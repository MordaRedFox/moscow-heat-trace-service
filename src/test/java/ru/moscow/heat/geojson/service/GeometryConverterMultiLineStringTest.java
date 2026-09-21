package ru.moscow.heat.geojson.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiLineString;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-тесты конвертации {@code MultiLineString} - линейного
 * ограничения, встречающегося в актуальном ТП ЛЦТ 2026
 */
class GeometryConverterMultiLineStringTest {

    private static final double TOLERANCE = 1e-9;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeometryConverterService converter =
            new GeometryConverterService(objectMapper);

    /**
     * MultiLineString из двух несвязанных отрезков разбирается
     * в JTS MultiLineString с двумя частями
     * @throws Exception при разборе JSON
     */
    @Test
    void parseMultiLineStringWithTwoParts() throws Exception {
        JsonNode node = objectMapper.readTree(
                "{\"type\":\"MultiLineString\",\"coordinates\":["
                        + "[[37.60,55.75],[37.61,55.75]],"
                        + "[[37.62,55.76],[37.63,55.76]]]}");

        Geometry g = converter.fromGeoJson(node);

        assertInstanceOf(MultiLineString.class, g);
        assertEquals(2, g.getNumGeometries());
    }

    /**
     * Round-trip MultiLineString сохраняет структуру и координаты
     * @throws Exception при разборе JSON
     */
    @Test
    void roundTripMultiLineString() throws Exception {
        JsonNode original = objectMapper.readTree(
                "{\"type\":\"MultiLineString\",\"coordinates\":["
                        + "[[37.60,55.75],[37.61,55.75]],"
                        + "[[37.62,55.76],[37.63,55.76]]]}");

        Geometry g = converter.fromGeoJson(original);
        JsonNode back = converter.toGeoJson(g);

        assertEquals("MultiLineString",
                back.get("type").asText());
        assertEquals(2, back.get("coordinates").size());

        Geometry reparsed = converter.fromGeoJson(back);
        assertTrue(g.equalsExact(reparsed, TOLERANCE));
    }

    /**
     * Пустой массив линий отклоняется
     * @throws Exception при разборе JSON
     */
    @Test
    void rejectEmptyMultiLineString() throws Exception {
        JsonNode node = objectMapper.readTree(
                "{\"type\":\"MultiLineString\",\"coordinates\":[]}");

        try {
            converter.fromGeoJson(node);
            throw new AssertionError(
                    "ожидалось исключение для пустого MultiLineString");
        } catch (RuntimeException expected) {
            // ok
        }
    }
}
