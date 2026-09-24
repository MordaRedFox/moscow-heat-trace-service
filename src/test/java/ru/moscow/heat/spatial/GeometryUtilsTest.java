package ru.moscow.heat.spatial;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import ru.moscow.heat.geojson.service.CoordinateTransformService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Тесты утилит геометрии {@link GeometryUtils}
 * <p>Все проверки выполняются относительно опорной точки в центре
 * Москвы (Красная площадь, 37.6175° в.д., 55.7522° с.ш.).
 * Смещения в метрах задаются в UTM 37N, после чего геометрии
 * переводятся в WGS 84 и подаются в утилиты
 * <p>Покрываются: длина, расстояние, ближайшая точка,
 * пересечение, угол пересечения, буфер по ширине пары труб
 * и точка на заданном расстоянии вдоль линии
 * <p>Для проверки типа геометрии используется
 * {@link org.junit.jupiter.api.Assertions#assertInstanceOf} —
 * JTS объявляет {@code Geometry} с сырым {@code Comparable},
 * из-за чего {@code AssertJ.assertThat(Geometry)} дает
 * unchecked-предупреждение
 */
@DisplayName("Тестирование утилит геометрии (GeometryUtils)")
class GeometryUtilsTest {

    /** Опорная долгота Москвы (Красная площадь) */
    private static final double BASE_LON = 37.6175;

    /** Опорная широта Москвы (Красная площадь) */
    private static final double BASE_LAT = 55.7522;

    private CoordinateTransformService transformService;
    private GeometryUtils geometryUtils;
    private GeometryFactory geometryFactory;

    /**
     * Готовит трансформер, утилиты и фабрику геометрий
     * для каждого теста
     */
    @BeforeEach
    void setUp() {
        transformService = new CoordinateTransformService();
        geometryUtils = new GeometryUtils(transformService);
        geometryFactory = new GeometryFactory();
    }

    /**
     * Длина отрезка, заданного в UTM сдвигом на 700 м,
     * возвращается с точностью ±1 м после round-trip через WGS 84
     */
    @Test
    @DisplayName("Длина отрезка ≈700 м через сдвиг в UTM")
    void shouldCalculateLengthOfKnown700mSegment() {
        double[] baseUtm = transformService.transformPoint(
                BASE_LON, BASE_LAT);
        double utmX0 = baseUtm[0];
        double utmY0 = baseUtm[1];

        double utmX1 = utmX0 + 700.0;
        double utmY1 = utmY0;

        Point pt0Utm = geometryFactory.createPoint(
                new Coordinate(utmX0, utmY0));
        Point pt1Utm = geometryFactory.createPoint(
                new Coordinate(utmX1, utmY1));
        pt0Utm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        pt1Utm.setSRID(CoordinateTransformService.SRID_UTM_37N);

        Point pt0Wgs = (Point) transformService.toWgs84(pt0Utm);
        Point pt1Wgs = (Point) transformService.toWgs84(pt1Utm);

        LineString lineWgs = geometryFactory.createLineString(
                new Coordinate[]{
                        pt0Wgs.getCoordinate(),
                        pt1Wgs.getCoordinate()
                });

        double calculatedLength = geometryUtils.lengthMeters(lineWgs);
        assertThat(calculatedLength).isCloseTo(
                700.0, within(1.0));
    }

    /**
     * Расстояние между точками, разнесенными на 300 м по X
     * и 400 м по Y в UTM, соответствует гипотенузе 500 м
     */
    @Test
    @DisplayName("Расстояние между точками 300/400 м → 500 м")
    void shouldCalculateDistanceBetweenGeometries() {
        double[] baseUtm = transformService.transformPoint(
                BASE_LON, BASE_LAT);
        Point p1Utm = geometryFactory.createPoint(
                new Coordinate(baseUtm[0], baseUtm[1]));
        Point p2Utm = geometryFactory.createPoint(
                new Coordinate(baseUtm[0] + 300.0,
                        baseUtm[1] + 400.0));
        p1Utm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        p2Utm.setSRID(CoordinateTransformService.SRID_UTM_37N);

        Point p1Wgs = (Point) transformService.toWgs84(p1Utm);
        Point p2Wgs = (Point) transformService.toWgs84(p2Utm);

        double distance = geometryUtils.distanceMeters(p1Wgs, p2Wgs);
        assertThat(distance).isCloseTo(500.0, within(1.0));
    }

    /**
     * Ближайшая точка на горизонтальной линии от точки сбоку
     * совпадает с проекцией этой точки на линию
     */
    @Test
    @DisplayName("nearestPointOnGeometry возвращает проекцию")
    void shouldFindNearestPointOnGeometry() {
        double[] baseUtm = transformService.transformPoint(
                BASE_LON, BASE_LAT);
        Coordinate c0 = new Coordinate(baseUtm[0], baseUtm[1]);
        Coordinate c1 = new Coordinate(
                baseUtm[0] + 500.0, baseUtm[1]);
        LineString lineUtm = geometryFactory.createLineString(
                new Coordinate[]{c0, c1});
        lineUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        LineString lineWgs = (LineString) transformService
                .toWgs84(lineUtm);

        Point fromUtm = geometryFactory.createPoint(
                new Coordinate(baseUtm[0] + 200.0,
                        baseUtm[1] + 50.0));
        fromUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point fromWgs = (Point) transformService.toWgs84(fromUtm);

        Point nearestWgs = geometryUtils.nearestPointOnGeometry(
                lineWgs, fromWgs);

        Point expectedUtm = geometryFactory.createPoint(
                new Coordinate(baseUtm[0] + 200.0, baseUtm[1]));
        expectedUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point expectedWgs = (Point) transformService
                .toWgs84(expectedUtm);

        double distToExpected = geometryUtils.distanceMeters(
                nearestWgs, expectedWgs);
        assertThat(distToExpected).isCloseTo(0.0, within(0.1));
    }

    /**
     * Угол между взаимно перпендикулярными линиями - 90°
     */
    @Test
    @DisplayName("Угол 90° между перпендикулярными линиями")
    void shouldCalculate90DegreeAngle() {
        double[] baseUtm = transformService.transformPoint(
                BASE_LON, BASE_LAT);

        LineString lineAUtm = geometryFactory.createLineString(
                new Coordinate[]{
                        new Coordinate(baseUtm[0] - 100.0,
                                baseUtm[1]),
                        new Coordinate(baseUtm[0] + 100.0,
                                baseUtm[1])
                });
        LineString lineBUtm = geometryFactory.createLineString(
                new Coordinate[]{
                        new Coordinate(baseUtm[0],
                                baseUtm[1] - 100.0),
                        new Coordinate(baseUtm[0],
                                baseUtm[1] + 100.0)
                });
        lineAUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        lineBUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);

        LineString lineAWgs = (LineString) transformService
                .toWgs84(lineAUtm);
        LineString lineBWgs = (LineString) transformService
                .toWgs84(lineBUtm);

        double angle = geometryUtils.crossingAngleDeg(
                lineAWgs, lineBWgs);
        assertThat(angle).isCloseTo(90.0, within(0.1));
    }

    /**
     * Угол между горизонталью и диагональю 45° - 45°
     */
    @Test
    @DisplayName("Угол 45° между горизонталью и диагональю")
    void shouldCalculate45DegreeAngle() {
        double[] baseUtm = transformService.transformPoint(
                BASE_LON, BASE_LAT);

        LineString lineAUtm = geometryFactory.createLineString(
                new Coordinate[]{
                        new Coordinate(baseUtm[0] - 100.0,
                                baseUtm[1]),
                        new Coordinate(baseUtm[0] + 100.0,
                                baseUtm[1])
                });
        LineString lineBUtm = geometryFactory.createLineString(
                new Coordinate[]{
                        new Coordinate(baseUtm[0] - 100.0,
                                baseUtm[1] - 100.0),
                        new Coordinate(baseUtm[0] + 100.0,
                                baseUtm[1] + 100.0)
                });
        lineAUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        lineBUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);

        LineString lineAWgs = (LineString) transformService
                .toWgs84(lineAUtm);
        LineString lineBWgs = (LineString) transformService
                .toWgs84(lineBUtm);

        double angle = geometryUtils.crossingAngleDeg(
                lineAWgs, lineBWgs);
        assertThat(angle).isCloseTo(45.0, within(0.1));
    }

    /**
     * Непересекающиеся линии не имеют угла пересечения
     */
    @Test
    @DisplayName("crossingAngleDeg: непересекающиеся линии")
    void shouldThrowWhenLinesDoNotIntersect() {
        double[] baseUtm = transformService.transformPoint(
                BASE_LON, BASE_LAT);
        LineString lineAUtm = geometryFactory.createLineString(
                new Coordinate[]{
                        new Coordinate(baseUtm[0], baseUtm[1]),
                        new Coordinate(baseUtm[0] + 100.0,
                                baseUtm[1])
                });
        LineString lineBUtm = geometryFactory.createLineString(
                new Coordinate[]{
                        new Coordinate(baseUtm[0], baseUtm[1] + 50.0),
                        new Coordinate(baseUtm[0] + 100.0,
                                baseUtm[1] + 50.0)
                });
        lineAUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        lineBUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);

        LineString lineAWgs = (LineString) transformService
                .toWgs84(lineAUtm);
        LineString lineBWgs = (LineString) transformService
                .toWgs84(lineBUtm);

        assertThatThrownBy(() -> geometryUtils.crossingAngleDeg(
                lineAWgs, lineBWgs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не пересекаются");
    }

    /**
     * Буфер вокруг точки с полной шириной 10 м дает радиус
     * 5 м от центра до границы
     */
    @Test
    @DisplayName("envelopeAround: радиус = widthM / 2")
    void shouldBuildEnvelopeAroundAxis() {
        double[] baseUtm = transformService.transformPoint(
                BASE_LON, BASE_LAT);
        Point ptUtm = geometryFactory.createPoint(
                new Coordinate(baseUtm[0], baseUtm[1]));
        ptUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point ptWgs = (Point) transformService.toWgs84(ptUtm);

        double widthM = 10.0;
        Geometry bufferWgs = geometryUtils.envelopeAround(
                ptWgs, widthM);

        Point boundaryPt = geometryUtils.nearestPointOnGeometry(
                bufferWgs.getBoundary(), ptWgs);
        double distToBoundary = geometryUtils.distanceMeters(
                ptWgs, boundaryPt);

        assertThat(distToBoundary).isCloseTo(5.0, within(0.2));
    }

    /**
     * Точка на расстоянии 350 м от начала 700-метрового отрезка
     * равноудалена от обоих его концов
     */
    @Test
    @DisplayName("pointAtDistance: середина отрезка 700 м")
    void shouldFindPointAtDistance() {
        double[] baseUtm = transformService.transformPoint(
                BASE_LON, BASE_LAT);
        LineString lineUtm = geometryFactory.createLineString(
                new Coordinate[]{
                        new Coordinate(baseUtm[0], baseUtm[1]),
                        new Coordinate(baseUtm[0] + 700.0,
                                baseUtm[1])
                });
        lineUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        LineString lineWgs = (LineString) transformService
                .toWgs84(lineUtm);

        Point midPoint = geometryUtils.pointAtDistance(
                lineWgs, 350.0);

        Point startPt = geometryFactory.createPoint(
                lineWgs.getCoordinateN(0));
        Point endPt = geometryFactory.createPoint(
                lineWgs.getCoordinateN(1));

        double distFromStart = geometryUtils.distanceMeters(
                startPt, midPoint);
        double distToEnd = geometryUtils.distanceMeters(
                endPt, midPoint);

        assertThat(distFromStart).isCloseTo(350.0, within(0.5));
        assertThat(distToEnd).isCloseTo(350.0, within(0.5));
    }

    /**
     * Пересечение двух линий дает точку; расстояние от этой точки
     * до начала первой линии соответствует геометрии сцены (50 м)
     * <p>Проверка типа пересечения выполняется через
     * {@code assertInstanceOf} из JUnit — {@code AssertJ}
     * на {@code Geometry} дает unchecked-предупреждение из-за
     * сырого {@code Comparable} в JTS
     */
    @Test
    @DisplayName("intersects и intersection дают корректный результат")
    void shouldCheckIntersectsAndIntersection() {
        double[] baseUtm = transformService.transformPoint(
                BASE_LON, BASE_LAT);
        LineString l1Utm = geometryFactory.createLineString(
                new Coordinate[]{
                        new Coordinate(baseUtm[0], baseUtm[1]),
                        new Coordinate(baseUtm[0] + 100.0,
                                baseUtm[1])
                });
        LineString l2Utm = geometryFactory.createLineString(
                new Coordinate[]{
                        new Coordinate(baseUtm[0] + 50.0,
                                baseUtm[1] - 50.0),
                        new Coordinate(baseUtm[0] + 50.0,
                                baseUtm[1] + 50.0)
                });
        l1Utm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        l2Utm.setSRID(CoordinateTransformService.SRID_UTM_37N);

        LineString l1Wgs = (LineString) transformService
                .toWgs84(l1Utm);
        LineString l2Wgs = (LineString) transformService
                .toWgs84(l2Utm);

        assertThat(geometryUtils.intersects(l1Wgs, l2Wgs)).isTrue();

        Geometry intersection = geometryUtils.intersection(
                l1Wgs, l2Wgs);
        assertInstanceOf(Point.class, intersection);
        assertThat(geometryUtils.distanceMeters(
                intersection,
                geometryFactory.createPoint(l1Wgs.getCoordinateN(0))))
                .isCloseTo(50.0, within(0.5));
    }
}
