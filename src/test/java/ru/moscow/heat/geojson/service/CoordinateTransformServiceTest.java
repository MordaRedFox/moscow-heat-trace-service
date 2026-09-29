package ru.moscow.heat.geojson.service;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit-тест трансформации EPSG:4326 → EPSG:32637 (Proj4J).
 * Проверяются: попадание контрольной точки в диапазон зоны 37N,
 * корректность SRID, обратимость
 */
class CoordinateTransformServiceTest {

    /** Кремль: 37.6156° в.д., 55.7522° с.ш. */
    private static final double KREMLIN_LON = 37.6156;
    private static final double KREMLIN_LAT = 55.7522;

    private final CoordinateTransformService service =
            new CoordinateTransformService();
    private final GeometryFactory geometryFactory =
            new GeometryFactory();

    /**
     * Контрольная точка попадает в ожидаемый диапазон зоны 37N
     */
    @Test
    void moscowControlPointToUtm37N() {
        double[] en = service.transformPoint(
                KREMLIN_LON, KREMLIN_LAT);

        assertTrue(en[0] > 400_000 && en[0] < 440_000,
                "easting должен попадать в диапазон зоны 37N: "
                        + en[0]);
        assertTrue(en[1] > 6_170_000 && en[1] < 6_190_000,
                "northing должен попадать в диапазон Москвы: "
                        + en[1]);
    }

    /**
     * Точка датасета попадает в диапазон зоны 37N
     */
    @Test
    void datasetPointFallsIntoZone37Range() {
        double[] en = service.transformPoint(
                37.6344054041544, 55.6994810644531);

        assertTrue(en[0] > 400_000 && en[0] < 430_000,
                "easting должен попадать в диапазон зоны 37N: "
                        + en[0]);
        assertTrue(en[1] > 6_170_000 && en[1] < 6_190_000,
                "northing должен попадать в диапазон Москвы: "
                        + en[1]);
    }

    /**
     * toUtm проставляет SRID 32637; toWgs84 корректно возвращает
     * исходные координаты
     */
    @Test
    void geometryTransformSetsSridAndRoundTrips() {
        Point point = geometryFactory.createPoint(
                new Coordinate(
                        37.6344054041544, 55.6994810644531));
        point.setSRID(4326);

        Geometry utm = service.toUtm(point);
        assertEquals(CoordinateTransformService.SRID_UTM_37N,
                utm.getSRID());
        assertTrue(utm.getCoordinate().x > 400_000);

        Geometry back = service.toWgs84(utm);
        assertEquals(CoordinateTransformService.SRID_WGS84,
                back.getSRID());
        assertEquals(point.getCoordinate().x,
                back.getCoordinate().x, 1e-7);
        assertEquals(point.getCoordinate().y,
                back.getCoordinate().y, 1e-7);
    }

    @Test
    void toWgs84IdempotentWhenAlreadyWgs84() {
        Point point = geometryFactory.createPoint(
                new Coordinate(KREMLIN_LON, KREMLIN_LAT));
        point.setSRID(4326);
        Geometry res = service.toWgs84(point);
        assertEquals(CoordinateTransformService.SRID_WGS84, res.getSRID());
        assertEquals(KREMLIN_LON, res.getCoordinate().x, 1e-7);
        assertEquals(KREMLIN_LAT, res.getCoordinate().y, 1e-7);

        // Также проверяем без явного SRID, но по диапазону координат
        Point noSridPoint = geometryFactory.createPoint(
                new Coordinate(KREMLIN_LON, KREMLIN_LAT));
        Geometry resNoSrid = service.toWgs84(noSridPoint);
        assertEquals(KREMLIN_LON, resNoSrid.getCoordinate().x, 1e-7);
        assertEquals(KREMLIN_LAT, resNoSrid.getCoordinate().y, 1e-7);
    }
}
