package ru.moscow.heat.geojson.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тест конвертера GeoJSON -> JTS -> GeoJSON (round-trip)
 */
class GeometryConverterServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeometryConverterService converter =
            new GeometryConverterService(objectMapper);

    @Test
    void pointRoundTrip() throws Exception {
        JsonNode node = objectMapper.readTree(
                "{\"type\":\"Point\",\"coordinates\":[37.6344054041544,55.6994810644531]}");

        Geometry geometry = converter.fromGeoJson(node);

        assertInstanceOf(Point.class, geometry);
        assertEquals(37.6344054041544, geometry.getCoordinate().x, 1e-12);
        assertEquals(55.6994810644531, geometry.getCoordinate().y, 1e-12);

        JsonNode out = converter.toGeoJson(geometry);
        Geometry back = converter.fromGeoJson(out);
        assertTrue(geometry.equalsExact(back), "round-trip Point изменил координаты");
    }

    @Test
    void lineStringRoundTrip() throws Exception {
        JsonNode node = objectMapper.readTree(
                "{\"type\":\"LineString\",\"coordinates\":["
                        + "[37.64079875310378,55.700214414852034],"
                        + "[37.64074858679058,55.7001284272387]]}");

        Geometry geometry = converter.fromGeoJson(node);

        assertInstanceOf(LineString.class, geometry);
        assertEquals(2, geometry.getNumPoints());

        Geometry back = converter.fromGeoJson(converter.toGeoJson(geometry));
        assertTrue(geometry.equalsExact(back));
    }

    @Test
    void multiPolygonRoundTrip() throws Exception {
        JsonNode node = objectMapper.readTree(
                "{\"type\":\"MultiPolygon\",\"coordinates\":[[[ "
                        + "[37.63,55.69],[37.64,55.69],[37.64,55.70],"
                        + "[37.63,55.70],[37.63,55.69]]]]}");

        Geometry geometry = converter.fromGeoJson(node);

        assertInstanceOf(MultiPolygon.class, geometry);
        assertEquals(1, geometry.getNumGeometries());

        Geometry back = converter.fromGeoJson(converter.toGeoJson(geometry));
        assertTrue(geometry.equalsExact(back));
    }
}