package ru.moscow.heat.spatial;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import ru.moscow.heat.geojson.service.CoordinateTransformService;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Тестирование утилит геометрии (GeometryUtils)")
class GeometryUtilsTest {

    private CoordinateTransformService transformService;
    private GeometryUtils geometryUtils;
    private GeometryFactory geometryFactory;

    // Опорная точка в центре Москвы: Красная площадь (37.6175, 55.7522)
    private static final double BASE_LON = 37.6175;
    private static final double BASE_LAT = 55.7522;

    @BeforeEach
    void setUp() {
        transformService = new CoordinateTransformService();
        geometryUtils = new GeometryUtils(transformService);
        geometryFactory = new GeometryFactory();
    }

    @Test
    @DisplayName("Вычисление длины отрезка ≈700 м через сдвиг в UTM (assert ±1 м)")
    void shouldCalculateLengthOfKnown700mSegment() {
        // Получаем координаты базовой точки в UTM
        double[] baseUtm = transformService.transformPoint(BASE_LON, BASE_LAT);
        double utmX0 = baseUtm[0];
        double utmY0 = baseUtm[1];

        // Точка со сдвигом строго на 700 м по оси X в UTM
        double utmX1 = utmX0 + 700.0;
        double utmY1 = utmY0;

        Point pt0Utm = geometryFactory.createPoint(new Coordinate(utmX0, utmY0));
        Point pt1Utm = geometryFactory.createPoint(new Coordinate(utmX1, utmY1));
        pt0Utm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        pt1Utm.setSRID(CoordinateTransformService.SRID_UTM_37N);

        Point pt0Wgs = (Point) transformService.toWgs84(pt0Utm);
        Point pt1Wgs = (Point) transformService.toWgs84(pt1Utm);

        LineString lineWgs = geometryFactory.createLineString(new Coordinate[]{
                pt0Wgs.getCoordinate(),
                pt1Wgs.getCoordinate()
        });

        double calculatedLength = geometryUtils.lengthMeters(lineWgs);
        assertThat(calculatedLength).isCloseTo(700.0, within(1.0));
    }

    @Test
    @DisplayName("Вычисление расстояния между точками (300 м по X, 400 м по Y -> гипотенуза 500 м)")
    void shouldCalculateDistanceBetweenGeometries() {
        double[] baseUtm = transformService.transformPoint(BASE_LON, BASE_LAT);
        Point p1Utm = geometryFactory.createPoint(new Coordinate(baseUtm[0], baseUtm[1]));
        Point p2Utm = geometryFactory.createPoint(new Coordinate(baseUtm[0] + 300.0, baseUtm[1] + 400.0));
        p1Utm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        p2Utm.setSRID(CoordinateTransformService.SRID_UTM_37N);

        Point p1Wgs = (Point) transformService.toWgs84(p1Utm);
        Point p2Wgs = (Point) transformService.toWgs84(p2Utm);

        double distance = geometryUtils.distanceMeters(p1Wgs, p2Wgs);
        assertThat(distance).isCloseTo(500.0, within(1.0));
    }

    @Test
    @DisplayName("Поиск ближайшей точки на геометрии (nearestPointOnGeometry)")
    void shouldFindNearestPointOnGeometry() {
        double[] baseUtm = transformService.transformPoint(BASE_LON, BASE_LAT);
        // Линия от (x0, y0) до (x0 + 500, y0)
        Coordinate c0 = new Coordinate(baseUtm[0], baseUtm[1]);
        Coordinate c1 = new Coordinate(baseUtm[0] + 500.0, baseUtm[1]);
        LineString lineUtm = geometryFactory.createLineString(new Coordinate[]{c0, c1});
        lineUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        LineString lineWgs = (LineString) transformService.toWgs84(lineUtm);

        // Точка сбоку: (x0 + 200, y0 + 50)
        Point fromUtm = geometryFactory.createPoint(new Coordinate(baseUtm[0] + 200.0, baseUtm[1] + 50.0));
        fromUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point fromWgs = (Point) transformService.toWgs84(fromUtm);

        Point nearestWgs = geometryUtils.nearestPointOnGeometry(lineWgs, fromWgs);

        // Ожидаемая ближайшая точка: (x0 + 200, y0)
        Point expectedUtm = geometryFactory.createPoint(new Coordinate(baseUtm[0] + 200.0, baseUtm[1]));
        expectedUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point expectedWgs = (Point) transformService.toWgs84(expectedUtm);

        double distToExpected = geometryUtils.distanceMeters(nearestWgs, expectedWgs);
        assertThat(distToExpected).isCloseTo(0.0, within(0.1));
    }

    @Test
    @DisplayName("Угол 90° между взаимно перпендикулярными линиями")
    void shouldCalculate90DegreeAngle() {
        double[] baseUtm = transformService.transformPoint(BASE_LON, BASE_LAT);

        // Линия A: горизонтальная от (x0 - 100, y0) до (x0 + 100, y0)
        LineString lineAUtm = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(baseUtm[0] - 100.0, baseUtm[1]),
                new Coordinate(baseUtm[0] + 100.0, baseUtm[1])
        });
        // Линия B: вертикальная от (x0, y0 - 100) до (x0, y0 + 100)
        LineString lineBUtm = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(baseUtm[0], baseUtm[1] - 100.0),
                new Coordinate(baseUtm[0], baseUtm[1] + 100.0)
        });
        lineAUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        lineBUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);

        LineString lineAWgs = (LineString) transformService.toWgs84(lineAUtm);
        LineString lineBWgs = (LineString) transformService.toWgs84(lineBUtm);

        double angle = geometryUtils.crossingAngleDeg(lineAWgs, lineBWgs);
        assertThat(angle).isCloseTo(90.0, within(0.1));
    }

    @Test
    @DisplayName("Угол 45° между горизонтальной линией и диагональю")
    void shouldCalculate45DegreeAngle() {
        double[] baseUtm = transformService.transformPoint(BASE_LON, BASE_LAT);

        // Линия A: горизонтальная от (x0 - 100, y0) до (x0 + 100, y0)
        LineString lineAUtm = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(baseUtm[0] - 100.0, baseUtm[1]),
                new Coordinate(baseUtm[0] + 100.0, baseUtm[1])
        });
        // Линия B: диагональ под 45° от (x0 - 100, y0 - 100) до (x0 + 100, y0 + 100)
        LineString lineBUtm = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(baseUtm[0] - 100.0, baseUtm[1] - 100.0),
                new Coordinate(baseUtm[0] + 100.0, baseUtm[1] + 100.0)
        });
        lineAUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        lineBUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);

        LineString lineAWgs = (LineString) transformService.toWgs84(lineAUtm);
        LineString lineBWgs = (LineString) transformService.toWgs84(lineBUtm);

        double angle = geometryUtils.crossingAngleDeg(lineAWgs, lineBWgs);
        assertThat(angle).isCloseTo(45.0, within(0.1));
    }

    @Test
    @DisplayName("crossingAngleDeg бросает исключение для непересекающихся линий")
    void shouldThrowWhenLinesDoNotIntersect() {
        double[] baseUtm = transformService.transformPoint(BASE_LON, BASE_LAT);
        LineString lineAUtm = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(baseUtm[0], baseUtm[1]),
                new Coordinate(baseUtm[0] + 100.0, baseUtm[1])
        });
        LineString lineBUtm = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(baseUtm[0], baseUtm[1] + 50.0),
                new Coordinate(baseUtm[0] + 100.0, baseUtm[1] + 50.0)
        });
        lineAUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        lineBUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);

        LineString lineAWgs = (LineString) transformService.toWgs84(lineAUtm);
        LineString lineBWgs = (LineString) transformService.toWgs84(lineBUtm);

        assertThatThrownBy(() -> geometryUtils.crossingAngleDeg(lineAWgs, lineBWgs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не пересекаются");
    }

    @Test
    @DisplayName("envelopeAround строит буфер заданной ширины (проверка радиуса widthM / 2)")
    void shouldBuildEnvelopeAroundAxis() {
        double[] baseUtm = transformService.transformPoint(BASE_LON, BASE_LAT);
        Point ptUtm = geometryFactory.createPoint(new Coordinate(baseUtm[0], baseUtm[1]));
        ptUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point ptWgs = (Point) transformService.toWgs84(ptUtm);

        double widthM = 10.0;
        Geometry bufferWgs = geometryUtils.envelopeAround(ptWgs, widthM);

        // Расстояние от центральной точки до края буфера должно быть ровно widthM / 2 = 5.0 м
        // Для этого проверим расстояние от центра до ближайшей точки на границе буфера
        Point boundaryPt = geometryUtils.nearestPointOnGeometry(bufferWgs.getBoundary(), ptWgs);
        double distToBoundary = geometryUtils.distanceMeters(ptWgs, boundaryPt);

        assertThat(distToBoundary).isCloseTo(5.0, within(0.2));
    }

    @Test
    @DisplayName("pointAtDistance находит точку на заданном расстоянии (проверка середины отрезка 700 м)")
    void shouldFindPointAtDistance() {
        double[] baseUtm = transformService.transformPoint(BASE_LON, BASE_LAT);
        LineString lineUtm = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(baseUtm[0], baseUtm[1]),
                new Coordinate(baseUtm[0] + 700.0, baseUtm[1])
        });
        lineUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        LineString lineWgs = (LineString) transformService.toWgs84(lineUtm);

        Point midPoint = geometryUtils.pointAtDistance(lineWgs, 350.0);

        Point startPt = geometryFactory.createPoint(lineWgs.getCoordinateN(0));
        Point endPt = geometryFactory.createPoint(lineWgs.getCoordinateN(1));

        double distFromStart = geometryUtils.distanceMeters(startPt, midPoint);
        double distToEnd = geometryUtils.distanceMeters(endPt, midPoint);

        assertThat(distFromStart).isCloseTo(350.0, within(0.5));
        assertThat(distToEnd).isCloseTo(350.0, within(0.5));
    }

    @Test
    @DisplayName("intersects и intersection возвращают верные геометрические результаты")
    void shouldCheckIntersectsAndIntersection() {
        double[] baseUtm = transformService.transformPoint(BASE_LON, BASE_LAT);
        LineString l1Utm = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(baseUtm[0], baseUtm[1]),
                new Coordinate(baseUtm[0] + 100.0, baseUtm[1])
        });
        LineString l2Utm = geometryFactory.createLineString(new Coordinate[]{
                new Coordinate(baseUtm[0] + 50.0, baseUtm[1] - 50.0),
                new Coordinate(baseUtm[0] + 50.0, baseUtm[1] + 50.0)
        });
        l1Utm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        l2Utm.setSRID(CoordinateTransformService.SRID_UTM_37N);

        LineString l1Wgs = (LineString) transformService.toWgs84(l1Utm);
        LineString l2Wgs = (LineString) transformService.toWgs84(l2Utm);

        assertThat(geometryUtils.intersects(l1Wgs, l2Wgs)).isTrue();

        Geometry intersection = geometryUtils.intersection(l1Wgs, l2Wgs);
        assertThat(intersection).isInstanceOf(Point.class);
        assertThat(geometryUtils.distanceMeters(intersection, geometryFactory.createPoint(l1Wgs.getCoordinateN(0))))
                .isCloseTo(50.0, within(0.5));
    }
}
