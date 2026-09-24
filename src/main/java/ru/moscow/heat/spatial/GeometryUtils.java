package ru.moscow.heat.spatial;

import org.locationtech.jts.algorithm.Distance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.springframework.stereotype.Component;
import ru.moscow.heat.geojson.service.CoordinateTransformService;

import java.util.Objects;

/**
 * Пространственные утилиты для работы с геометриями теплосети.
 * Согласно правилам кейса:
 * - Все входные и выходные геометрии представлены в WGS 84 (EPSG:4326)
 * - ВСЕ вычисления расстояний, длин, буферов и углов производятся в метрической
 *   проекции UTM zone 37N (EPSG:32637)
 */
@Component
public class GeometryUtils {

    private final CoordinateTransformService transformService;

    public GeometryUtils(CoordinateTransformService transformService) {
        this.transformService = Objects.requireNonNull(
            transformService, "CoordinateTransformService must not be null");
    }

    /**
     * Длина геометрии в метрах (вычисляется в EPSG:32637)
     * @param geom геометрия в EPSG:4326
     * @return длина в метрах
     */
    public double lengthMeters(Geometry geom) {
        if (geom == null || geom.isEmpty()) {
            return 0.0;
        }
        return transformService.toUtm(geom).getLength();
    }

    /**
     * Кратчайшее евклидово расстояние между двумя геометриями в
     * метрах (в EPSG:32637)
     * @param a первая геометрия в EPSG:4326
     * @param b вторая геометрия в EPSG:4326
     * @return расстояние в метрах
     */
    public double distanceMeters(Geometry a, Geometry b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            throw new IllegalArgumentException(
                "Геометрии для вычисления расстояния не должны быть null или пустыми");
        }
        return transformService.toUtm(a).distance(transformService.toUtm(b));
    }

    /**
     * Поиск ближайшей точки на целевой геометрии target к заданной точке from
     * @param target целевая геометрия в EPSG:4326 (полигон, линия и т.п.)
     * @param from   исходная точка в EPSG:4326
     * @return ближайшая точка на target в EPSG:4326
     */
    public Point nearestPointOnGeometry(Geometry target, Point from) {
        if (target == null || from == null || target.isEmpty() || from.isEmpty()) {
            throw new IllegalArgumentException(
                "Геометрии не должны быть null или пустыми");
        }
        Geometry targetUtm = transformService.toUtm(target);
        Geometry fromUtm = transformService.toUtm(from);

        Coordinate[] nearest = DistanceOp.nearestPoints(targetUtm, fromUtm);
        Coordinate nearestOnTarget = nearest[0];

        Point pointUtm = targetUtm.getFactory().createPoint(nearestOnTarget);
        pointUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);

        return (Point) transformService.toWgs84(pointUtm);
    }

    /**
     * Проверка пространственного пересечения двух геометрий
     * @param a первая геометрия в EPSG:4326
     * @param b вторая геометрия в EPSG:4326
     * @return true, если геометрии пересекаются в метрической СК
     */
    public boolean intersects(Geometry a, Geometry b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return transformService.toUtm(a).intersects(transformService.toUtm(b));
    }

    /**
     * Вычисление геометрического пересечения двух геометрий
     * @param a первая геометрия в EPSG:4326
     * @param b вторая геометрия в EPSG:4326
     * @return геометрия пересечения в EPSG:4326
     */
    public Geometry intersection(Geometry a, Geometry b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            throw new IllegalArgumentException(
                "Геометрии не должны быть null или пустыми");
        }
        Geometry aUtm = transformService.toUtm(a);
        Geometry bUtm = transformService.toUtm(b);
        Geometry interUtm = aUtm.intersection(bUtm);
        interUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        return transformService.toWgs84(interUtm);
    }

    /**
     * Вычисление угла пересечения двух линий в точке пересечения в градусах,
     * нормализованного в диапазон [0...90].
     * Применяется для проверки нормативного требования угла пересечения
     * автомобильных и трамвайных путей (не менее 45 градусов)
     * @param a первая линия в EPSG:4326
     * @param b вторая линия в EPSG:4326
     * @return угол пересечения в градусах от 0.0 до 90.0
     * @throws IllegalArgumentException если линии не пересекаются
     */
    public double crossingAngleDeg(LineString a, LineString b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            throw new IllegalArgumentException(
                "Линии не должны быть null или пустыми");
        }
        LineString aUtm = (LineString) transformService.toUtm(a);
        LineString bUtm = (LineString) transformService.toUtm(b);

        Geometry interUtm = aUtm.intersection(bUtm);
        if (interUtm.isEmpty()) {
            throw new IllegalArgumentException("Линии не пересекаются");
        }

        Coordinate entryPoint = interUtm.getCoordinate();

        double[] dirA = findDirectionVectorAtPoint(aUtm, entryPoint);
        double[] dirB = findDirectionVectorAtPoint(bUtm, entryPoint);

        double dot = dirA[0] * dirB[0] + dirA[1] * dirB[1];
        double lenA = Math.hypot(dirA[0], dirA[1]);
        double lenB = Math.hypot(dirB[0], dirB[1]);

        if (lenA == 0.0 || lenB == 0.0) {
            return 0.0;
        }

        double cosTheta = Math.abs(dot) / (lenA * lenB);
        cosTheta = Math.max(0.0, Math.min(1.0, cosTheta));

        double angleRad = Math.acos(cosTheta);
        return Math.toDegrees(angleRad);
    }

    /**
     * Построение буферной зоны (габарита) вокруг оси геометрии на заданную
     * ширину. Буфер откладывается на widthM / 2.0 в каждую сторону от оси в UTM
     * @param axis   осевая геометрия в EPSG:4326
     * @param widthM полная ширина габарита в метрах
     * @return полигональная геометрия габарита в EPSG:4326
     */
    public Geometry envelopeAround(Geometry axis, double widthM) {
        if (axis == null || axis.isEmpty()) {
            throw new IllegalArgumentException(
                "Осевая геометрия не должна быть null или пустой");
        }
        if (widthM <= 0.0) {
            throw new IllegalArgumentException(
                "Ширина габарита должна быть положительной: " + widthM);
        }
        Geometry axisUtm = transformService.toUtm(axis);
        Geometry bufferUtm = axisUtm.buffer(widthM / 2.0);
        bufferUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        return transformService.toWgs84(bufferUtm);
    }

    /**
     * Вычисление координаты точки на линии на расстоянии distanceM от начала.
     * Выполняется через JTS LengthIndexedLine в метрической проекции UTM
     * @param line      линия в EPSG:4326
     * @param distanceM расстояние от начала линии в метрах
     * @return точка на линии в EPSG:4326
     */
    public Point pointAtDistance(LineString line, double distanceM) {
        if (line == null || line.isEmpty()) {
            throw new IllegalArgumentException(
                "Линия не должна быть null или пустой");
        }
        if (distanceM < 0.0) {
            throw new IllegalArgumentException(
                "Расстояние не может быть отрицательным: " + distanceM);
        }
        LineString lineUtm = (LineString) transformService.toUtm(line);
        LengthIndexedLine indexedLine = new LengthIndexedLine(lineUtm);
        Coordinate coord = indexedLine.extractPoint(distanceM);

        Point ptUtm = lineUtm.getFactory().createPoint(coord);
        ptUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        return (Point) transformService.toWgs84(ptUtm);
    }

    private double[] findDirectionVectorAtPoint(LineString line, Coordinate p) {
        Coordinate[] coords = line.getCoordinates();
        double minDistance = Double.MAX_VALUE;
        int bestSegment = 0;

        for (int i = 0; i < coords.length - 1; i++) {
            double d = Distance.pointToSegment(p, coords[i], coords[i + 1]);
            if (d < minDistance) {
                minDistance = d;
                bestSegment = i;
            }
        }

        double dx = coords[bestSegment + 1].x - coords[bestSegment].x;
        double dy = coords[bestSegment + 1].y - coords[bestSegment].y;
        return new double[]{dx, dy};
    }
}
