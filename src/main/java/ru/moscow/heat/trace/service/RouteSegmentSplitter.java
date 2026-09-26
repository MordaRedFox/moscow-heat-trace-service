package ru.moscow.heat.trace.service;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.springframework.stereotype.Service;
import ru.moscow.heat.trace.graph.ObstacleModel;
import ru.moscow.heat.trace.model.LayingMethod;
import ru.moscow.heat.trace.model.RouteNode;
import ru.moscow.heat.trace.model.RouteNodeType;
import ru.moscow.heat.trace.model.RouteSegment;
import ru.moscow.heat.trace.model.TechnicalNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Разбивает упрощённый путь ({@code RouteSimplifier}) на {@link RouteSegment}
 * по границам зон специального прохода (план, шаг 7): внутри каждой
 * получившейся под-линии способ прокладки и Kспец постоянны.
 * <p>
 * Разбиение по смене ДУ сюда НЕ входит — оно возможно только после
 * {@code DiameterAssigner} (ДУ ещё не известен на этом шаге), поэтому
 * все сегменты выходят с {@code diameterMm=0} — заглушкой, которую
 * следующим шагом заполняет {@code DiameterAssigner.assign(...)}.
 * <p>
 * Работает в UTM, без трансформации координат: точки пересечения
 * отрезка со спецзонами и их положение "вдоль отрезка" считаются через
 * {@code JTS LengthIndexedLine} — тот же приём, что использует
 * {@code GeometryUtils.pointAtDistance}, но напрямую на geometry_utm.
 */
@Service
public class RouteSegmentSplitter {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    /** Результат разбиения одного маршрута ОКС. */
    public static final class SplitResult {
        private final List<RouteSegment> segments;
        private final List<TechnicalNode> technicalNodes;

        public SplitResult(List<RouteSegment> segments, List<TechnicalNode> technicalNodes) {
            this.segments = segments;
            this.technicalNodes = technicalNodes;
        }

        public List<RouteSegment> getSegments() {
            return segments;
        }

        public List<TechnicalNode> getTechnicalNodes() {
            return technicalNodes;
        }
    }

    /**
     * Разбивает упрощённый путь на сегменты по границам спецзон.
     *
     * @param simplifiedPathUtm путь после {@code RouteSimplifier}, UTM
     * @param obstacleModel     модель препятствий (для определения спецзон)
     * @param flowTph           расход ОКС, т/ч — переносится на каждый
     *                          получившийся сегмент как есть (объединение
     *                          ОКС в итерации 5 не делается)
     * @param oksFeatureId      feature_id ОКС — проставляется на
     *                          {@code RouteNode} самой первой точки пути
     * @return сегменты (без ДУ) + технические узлы на границах спецзон
     */
    public SplitResult split(List<Coordinate> simplifiedPathUtm, ObstacleModel obstacleModel,
                              BigDecimal flowTph, String oksFeatureId) {
        List<RouteSegment> segments = new ArrayList<>();
        List<TechnicalNode> technicalNodes = new ArrayList<>();

        boolean isFirstSubSegment = true;

        for (int i = 0; i < simplifiedPathUtm.size() - 1; i++) {
            Coordinate a = simplifiedPathUtm.get(i);
            Coordinate b = simplifiedPathUtm.get(i + 1);
            LineString segmentLine = GEOMETRY_FACTORY.createLineString(new Coordinate[]{a, b});
            double length = segmentLine.getLength();

            List<Double> breakpoints = computeBreakpoints(segmentLine, length, obstacleModel);

            for (int k = 0; k < breakpoints.size() - 1; k++) {
                double d0 = breakpoints.get(k);
                double d1 = breakpoints.get(k + 1);
                if (d1 - d0 < 1e-6) {
                    continue; // вырожденный под-сегмент (точка пересечения совпала с концом)
                }
                Coordinate p0 = interpolate(a, b, d0, length);
                Coordinate p1 = interpolate(a, b, d1, length);
                Coordinate mid = interpolate(a, b, (d0 + d1) / 2.0, length);

                double kspets = maxKspetsAt(mid, obstacleModel);
                LayingMethod layingMethod = kspets > 1.0 ? LayingMethod.SPECIAL : LayingMethod.BASE;

                LineString subGeom = GEOMETRY_FACTORY.createLineString(new Coordinate[]{p0, p1});

                RouteNode fromNode = new RouteNode(UUID.randomUUID(),
                        isFirstSubSegment ? RouteNodeType.OKS_POINT : RouteNodeType.CORNER,
                        p0, isFirstSubSegment ? oksFeatureId : null);
                RouteNode toNode = new RouteNode(UUID.randomUUID(), RouteNodeType.CORNER, p1, null);

                if (!isFirstSubSegment && k == 0 && i > 0) {
                    // Граница между сегментами исходного пути (не связана со спецзоной) —
                    // технический узел здесь не нужен, это просто стык двух прямых.
                } else if (k > 0) {
                    technicalNodes.add(new TechnicalNode(UUID.randomUUID(), p0, TechnicalNode.Reason.ZONE_BOUNDARY));
                }

                segments.add(new RouteSegment(UUID.randomUUID(), fromNode, toNode, subGeom,
                        flowTph, 0, layingMethod, kspets, subGeom.getLength(), null));

                isFirstSubSegment = false;
            }
        }

        return new SplitResult(segments, technicalNodes);
    }

    /**
     * Точки разбиения отрезка (в "расстояние от начала отрезка", метры):
     * всегда включает 0 и полную длину, плюс точки пересечения отрезка
     * с границами всех спецзон.
     */
    private List<Double> computeBreakpoints(LineString segmentLine, double length, ObstacleModel obstacleModel) {
        TreeSet<Double> breakpoints = new TreeSet<>();
        breakpoints.add(0.0);
        breakpoints.add(length);

        LengthIndexedLine indexedLine = new LengthIndexedLine(segmentLine);
        for (ObstacleModel.SpecialZone zone : obstacleModel.getSpecialZones()) {
            Geometry intersection = segmentLine.intersection(zone.getBufferedGeometryUtm());
            if (intersection.isEmpty()) {
                continue;
            }
            for (Coordinate c : intersection.getCoordinates()) {
                double d = indexedLine.indexOf(c);
                if (d >= 0 && d <= length) {
                    breakpoints.add(d);
                }
            }
        }
        return new ArrayList<>(breakpoints);
    }

    /** Линейная интерполяция точки на прямом отрезке a-b на расстоянии distanceFromA от a. */
    private Coordinate interpolate(Coordinate a, Coordinate b, double distanceFromA, double totalLength) {
        double t = totalLength == 0 ? 0 : distanceFromA / totalLength;
        return new Coordinate(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
    }

    /** Максимальный Kспец среди спецзон, покрывающих точку (1.0, если ни одна не покрывает). */
    private double maxKspetsAt(Coordinate point, ObstacleModel obstacleModel) {
        Point p = GEOMETRY_FACTORY.createPoint(point);
        double maxKspets = 1.0;
        for (ObstacleModel.SpecialZone zone : obstacleModel.getSpecialZones()) {
            if (zone.getBufferedGeometryUtm().covers(p)) {
                maxKspets = Math.max(maxKspets, zone.getKspets());
            }
        }
        return maxKspets;
    }
}
