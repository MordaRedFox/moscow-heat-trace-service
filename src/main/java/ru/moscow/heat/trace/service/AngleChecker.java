package ru.moscow.heat.trace.service;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.springframework.stereotype.Service;

/**
 * Проверяет угол пересечения новой сети с road/tram_tracks (план, шаг 5 и
 * риск R2: "≥ 45° при пересечении дороги").
 * <p>
 * Прагматичный путь по плану: сначала строим A* без строгого учёта угла,
 * затем постобработкой проверяем углы пересечений; при нарушении —
 * добавляем waypoint на границе зоны и локально перестраиваем участок
 * (не полный перерасчёт графа).
 */
@Service
public class AngleChecker {

    private static final double MIN_CROSSING_ANGLE_DEGREES = 45.0;

    /**
     * Проверяет угол между отрезком маршрута и осью пересекаемого
     * линейного объекта (дорога/трамвайные пути) в точке пересечения.
     *
     * @param segmentStart начало отрезка маршрута, UTM
     * @param segmentEnd   конец отрезка маршрута, UTM
     * @param crossedAxis  геометрия оси пересекаемого объекта (LineString), UTM
     * @return {@code true}, если угол пересечения ≥ 45°
     */
    public boolean isCrossingAngleValid(Coordinate segmentStart, Coordinate segmentEnd, LineString crossedAxis) {
        // TODO:
        // 1. найти точку пересечения segmentStart-segmentEnd с crossedAxis;
        // 2. взять направляющий вектор маршрута и направляющий вектор оси
        //    в окрестности точки пересечения;
        // 3. угол между векторами (через dot product), привести к [0,90];
        // 4. сравнить с MIN_CROSSING_ANGLE_DEGREES.
        throw new UnsupportedOperationException("TODO: итерация 5, шаг 5 / риск R2");
    }
}
