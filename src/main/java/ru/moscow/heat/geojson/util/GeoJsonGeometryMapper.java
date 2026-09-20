package ru.moscow.heat.geojson.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Component;

@Component
public class GeoJsonGeometryMapper {

    private final GeometryFactory geometryFactory = new GeometryFactory();

    public Geometry toJts(JsonNode geoJsonGeometry) {
        String type = geoJsonGeometry.path("type").asText();
        JsonNode coords = geoJsonGeometry.path("coordinates");

        return switch (type) {
            case "Point"              -> geometryFactory.createPoint(readCoord(coords));
            case "LineString"         -> geometryFactory.createLineString(readCoords(coords));
            case "Polygon"            -> buildPolygon(coords);
            case "MultiPolygon"       -> buildMultiPolygon(coords);
            default -> throw new IllegalArgumentException("Unsupported geometry: " + type);
        };
    }

    private Coordinate readCoord(JsonNode n) {
        return new Coordinate(n.get(0).asDouble(), n.get(1).asDouble());
    }

    private Coordinate[] readCoords(JsonNode arr) {
        Coordinate[] result = new Coordinate[arr.size()];
        for (int i = 0; i < arr.size(); i++) result[i] = readCoord(arr.get(i));
        return result;
    }

    private Polygon buildPolygon(JsonNode rings) {
        LinearRing shell = geometryFactory.createLinearRing(readCoords(rings.get(0)));
        LinearRing[] holes = new LinearRing[rings.size() - 1];
        for (int i = 1; i < rings.size(); i++)
            holes[i - 1] = geometryFactory.createLinearRing(readCoords(rings.get(i)));
        Polygon p = geometryFactory.createPolygon(shell, holes);
        p.setSRID(4326);
        return p;
    }

    private MultiPolygon buildMultiPolygon(JsonNode polys) {
        Polygon[] arr = new Polygon[polys.size()];
        for (int i = 0; i < polys.size(); i++) arr[i] = buildPolygon(polys.get(i));
        MultiPolygon mp = geometryFactory.createMultiPolygon(arr);
        mp.setSRID(4326);
        return mp;
    }
}