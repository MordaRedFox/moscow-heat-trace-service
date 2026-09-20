package ru.moscow.heat.geojson.service;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-тест трансформации EPSG:4326 -> EPSG:32637 (Proj4J).
 * Контрольная точка: Москва, Кремль (55.7522, 37.6156).
 * Ожидаемые метрические координаты UTM 37N: E ~ 413 113 м, N ~ 6 180 516 м
 * (сверено с проекцией Transverse Mercator, допуск 30 м).
 */
class CoordinateTransformServiceTest {

    private final CoordinateTransformService service = new CoordinateTransformService();
    private final GeometryFactory geometryFactory = new GeometryFactory();

    @Test
    void moscowControlPointToUtm37N() {
        double[] en = service.transformPoint(37.6156, 55.7522);

        assertEquals(413_113, en[0], 30, "easting контрольной точки");
        assertEquals(6_180_516, en[1], 30, "northing контрольной точки");
    }

    @Test
    void datasetPointFallsIntoZone37Range() {
        // Первая точка конкурсного датасета (oks_connection_point id=1)
        double[] en = service.transformPoint(37.6344054041544, 55.6994810644531);

        assertTrue(en[0] > 400_000 && en[0] < 430_000,
                "easting должен попадать в диапазон зоны 37N: " + en[0]);
        assertTrue(en[1] > 6_170_000 && en[1] < 6_190_000,
                "northing должен попадать в диапазон Москвы: " + en[1]);
    }

    @Test
    void geometryTransformSetsSridAndRoundTrips() {
        Point point = geometryFactory.createPoint(
                new org.locationtech.jts.geom.Coordinate(37.6344054041544, 55.6994810644531));
        point.setSRID(4326);

        Geometry utm = service.toUtm(point);
        assertEquals(CoordinateTransformService.SRID_UTM_37N, utm.getSRID());
        assertTrue(utm.getCoordinate().x > 400_000);

        Geometry back = service.toWgs84(utm);
        assertEquals(CoordinateTransformService.SRID_WGS84, back.getSRID());
        assertEquals(point.getCoordinate().x, back.getCoordinate().x, 1e-7);
        assertEquals(point.getCoordinate().y, back.getCoordinate().y, 1e-7);
    }
}