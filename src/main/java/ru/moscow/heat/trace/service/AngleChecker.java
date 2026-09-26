package ru.moscow.heat.trace.service;

import org.locationtech.jts.algorithm.Distance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

/**
 * Проверяет угол пересечения новой сети с road/tram_tracks (план, шаг 5;
 * ТП: минимальный угол не менее 45°)
 * <p>Работает в UTM. Вызывается {@code TraceOrchestrator} после упрощения
 * пути: для каждого сегмента маршрута, пересекающего спецзону с
 * {@code minCrossingAngleDeg != null}, проверяется угол между направляющим
 * вектором сегмента и направляющим вектором оси ограничения в точке
 * пересечения
 * <p>Если линии коллинеарны (пересечение — линия, а не точка), угол
 * считается недопустимым: специальный проход должен быть одним прямым
 * участком, идущим «поперёк» объекта, а не «вдоль» него
 */
@Service
public class AngleChecker {

    /** Значение по умолчанию, если правило типа не задало собственный угол */
    private static final double DEFAULT_MIN_CROSSING_ANGLE_DEG = 45.0;

    /** SRID метрической системы координат UTM zone 37N, в которой работает весь модуль */
    private static final int UTM_SRID = 32637;

    /** Фабрика JTS для построения служебных сегментов */
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    /**
     * Проверяет угол между отрезком маршрута и осью пересекаемого
     * линейного объекта (дорога/трамвайные пути) в точке пересечения
     * @param segmentStart начало отрезка маршрута, UTM
     * @param segmentEnd   конец отрезка маршрута, UTM
     * @param crossedAxis  геометрия оси пересекаемого объекта в UTM
     *                     (обычно {@code LineString} или {@code MultiLineString})
     * @param minAngleDeg  нормативный минимум, град.; если {@code null}
     *                     берётся {@value #DEFAULT_MIN_CROSSING_ANGLE_DEG}
     * @return {@code true}, если угол пересечения ≥ {@code minAngleDeg}
     *         (или если пересечения нет — тогда проверка не применима)
     */
    public boolean isCrossingAngleValid(Coordinate segmentStart,
                                         Coordinate segmentEnd,
                                         Geometry crossedAxis,
                                         Double minAngleDeg) {
        double threshold = minAngleDeg != null
                ? minAngleDeg
                : DEFAULT_MIN_CROSSING_ANGLE_DEG;

        LineString segment = buildSegment(segmentStart, segmentEnd);

        Geometry inter = segment.intersection(crossedAxis);
        if (inter.isEmpty()) {
            return true;
        }
        if (!(inter instanceof Point)) {
            // Коллинеарное пересечение (линия) или касание - угол не определен
            return false;
        }

        Coordinate crossPoint = inter.getCoordinate();
        double[] routeDir = directionAtPoint(segment, crossPoint);
        double[] axisDir = directionAtPoint(crossedAxis, crossPoint);

        double dot = routeDir[0] * axisDir[0] + routeDir[1] * axisDir[1];
        double lenA = Math.hypot(routeDir[0], routeDir[1]);
        double lenB = Math.hypot(axisDir[0], axisDir[1]);
        if (lenA == 0.0 || lenB == 0.0) {
            return false;
        }

        double cosTheta = Math.abs(dot) / (lenA * lenB);
        cosTheta = Math.max(0.0, Math.min(1.0, cosTheta));

        double angleRad = Math.acos(cosTheta);
        double angleDeg = Math.toDegrees(angleRad);

        return angleDeg >= threshold;
    }

    /**
     * Строит {@link LineString} из двух координат в UTM
     * @param a начало отрезка
     * @param b конец отрезка
     * @return линия из двух точек с SRID 32637
     */
    private LineString buildSegment(Coordinate a, Coordinate b) {
        LineString ls = GEOMETRY_FACTORY.createLineString(
                new Coordinate[]{new Coordinate(a.x, a.y),
                                 new Coordinate(b.x, b.y)});
        ls.setSRID(UTM_SRID);
        return ls;
    }

    /**
     * Возвращает направляющий вектор линии в окрестности точки: берем
     * сегмент, ближайший к {@code p}, и возвращаем его разность
     * (ненормализованную - нормализация делается в вызывающем методе)
     * @param line линия или мультилиния в UTM
     * @param p    точка, вокруг которой ищется направление
     * @return вектор {@code [dx, dy]} ближайшего сегмента либо
     *         {@code [0, 0]}, если у линии меньше двух точек
     */
    private double[] directionAtPoint(Geometry line, Coordinate p) {
        Coordinate[] coords = line.getCoordinates();
        if (coords.length < 2) {
            return new double[]{0.0, 0.0};
        }
        int bestSegment = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < coords.length - 1; i++) {
            double d = Distance.pointToSegment(p, coords[i], coords[i + 1]);
            if (d < bestDistance) {
                bestDistance = d;
                bestSegment = i;
            }
        }
        double dx = coords[bestSegment + 1].x - coords[bestSegment].x;
        double dy = coords[bestSegment + 1].y - coords[bestSegment].y;
        return new double[]{dx, dy};
    }
}
