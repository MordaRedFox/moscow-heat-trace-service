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
 * Разбивает упрощённый путь на {@link RouteSegment} по границам зон
 * специального прохода
 * <p>Узлы разбиения создаются один раз на точку. Соседние сегменты делят
 * один и тот же {@link RouteNode} на общей границе — это сохраняет топологию
 * маршрута: {@code end_node_id} одного сегмента совпадает со
 * {@code start_node_id} следующего
 * <p>Технические узлы создаются на каждой промежуточной границе разбиения.
 * Причина узла: {@link TechnicalNode.Reason#METHOD_CHANGE}, если способ
 * прокладки меняется (BASE ↔ SPECIAL), иначе
 * {@link TechnicalNode.Reason#ZONE_BOUNDARY}
 * <p>Разбиение по смене ДУ сюда НЕ входит — оно выполняется позже
 * {@code DiameterAssigner}. Здесь все сегменты выходят с {@code diameterMm=0}
 */
@Service
public class RouteSegmentSplitter {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final int UTM_SRID = 32637;

    /** Результат разбиения одного маршрута ОКС */
    public static final class SplitResult {
        private final List<RouteSegment> segments;
        private final List<TechnicalNode> technicalNodes;

        public SplitResult(List<RouteSegment> segments,
                            List<TechnicalNode> technicalNodes) {
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
     * Разбивает упрощенный путь на сегменты по границам спецзон
     * @param simplifiedPathUtm путь после {@code RouteSimplifier}, UTM
     * @param obstacleModel     модель препятствий
     * @param flowTph           расход ОКС, т/ч
     * @param oksFeatureId      feature_id ОКС — проставляется на первом узле
     * @param endNodeType       тип последнего узла (EXISTING_CHAMBER / NEW_CHAMBER)
     * @param endFeatureId      feature_id существующей камеры либо {@code null}
     * @return сегменты (без ДУ) + технические узлы на границах разбиения
     */
    public SplitResult split(List<Coordinate> simplifiedPathUtm,
                              ObstacleModel obstacleModel,
                              BigDecimal flowTph,
                              String oksFeatureId,
                              RouteNodeType endNodeType,
                              String endFeatureId) {
        if (simplifiedPathUtm.size() < 2) {
            return new SplitResult(List.of(), List.of());
        }

        LineString fullPath = GEOMETRY_FACTORY.createLineString(
                simplifiedPathUtm.toArray(new Coordinate[0]));
        fullPath.setSRID(UTM_SRID);
        double totalLength = fullPath.getLength();
        LengthIndexedLine indexedLine = new LengthIndexedLine(fullPath);

        TreeSet<Double> breakpoints = collectBreakpoints(
                fullPath, totalLength, indexedLine, obstacleModel);
        List<Double> sortedBps = new ArrayList<>(breakpoints);

        List<RouteNode> nodes = new ArrayList<>(sortedBps.size());
        for (int i = 0; i < sortedBps.size(); i++) {
            double d = sortedBps.get(i);
            Coordinate coord = indexedLine.extractPoint(d);
            RouteNodeType type;
            String sourceFeatureId = null;
            if (i == 0) {
                type = RouteNodeType.OKS_POINT;
                sourceFeatureId = oksFeatureId;
            } else if (i == sortedBps.size() - 1) {
                type = endNodeType;
                sourceFeatureId = endFeatureId;
            } else {
                type = RouteNodeType.CORNER;
            }
            nodes.add(new RouteNode(UUID.randomUUID(), type, coord, sourceFeatureId));
        }

        List<RouteSegment> segments = new ArrayList<>();
        List<TechnicalNode> technicalNodes = new ArrayList<>();

        for (int i = 0; i < nodes.size() - 1; i++) {
            RouteNode fromNode = nodes.get(i);
            RouteNode toNode = nodes.get(i + 1);
            LineString subGeom = GEOMETRY_FACTORY.createLineString(
                    new Coordinate[]{
                            fromNode.getCoordinateUtm(),
                            toNode.getCoordinateUtm()
                    });
            subGeom.setSRID(UTM_SRID);

            Coordinate mid = new Coordinate(
                    (fromNode.getCoordinateUtm().x + toNode.getCoordinateUtm().x) / 2.0,
                    (fromNode.getCoordinateUtm().y + toNode.getCoordinateUtm().y) / 2.0);
            double kspets = maxKspetsAt(mid, obstacleModel);
            LayingMethod layingMethod =
                    kspets > 1.0 ? LayingMethod.SPECIAL : LayingMethod.BASE;

            RouteSegment segment = new RouteSegment(
                    UUID.randomUUID(), fromNode, toNode, subGeom,
                    flowTph, 0, layingMethod, kspets, subGeom.getLength(), null);
            segments.add(segment);

            // Технический узел на каждой промежуточной границе разбиения
            if (i > 0) {
                LayingMethod prevMethod = segments.get(i - 1).getLayingMethod();
                TechnicalNode.Reason reason = (prevMethod != layingMethod)
                        ? TechnicalNode.Reason.METHOD_CHANGE
                        : TechnicalNode.Reason.ZONE_BOUNDARY;
                technicalNodes.add(new TechnicalNode(
                        UUID.randomUUID(),
                        fromNode.getCoordinateUtm(),
                        reason));
            }
        }

        return new SplitResult(segments, technicalNodes);
    }

    private TreeSet<Double> collectBreakpoints(LineString fullPath,
                                                double totalLength,
                                                LengthIndexedLine indexedLine,
                                                ObstacleModel obstacleModel) {
        TreeSet<Double> breakpoints = new TreeSet<>();
        breakpoints.add(0.0);
        breakpoints.add(totalLength);

        // Вершины исходного пути (углы поворота трассы при обходе препятствий)
        for (Coordinate c : fullPath.getCoordinates()) {
            double d = indexedLine.indexOf(c);
            if (d >= 0.0 && d <= totalLength) {
                breakpoints.add(d);
            }
        }

        for (ObstacleModel.SpecialZone zone : obstacleModel.getSpecialZones()) {
            Geometry intersection = fullPath.intersection(zone.getBufferedGeometryUtm());
            if (intersection.isEmpty()) {
                continue;
            }
            for (Coordinate c : intersection.getCoordinates()) {
                double d = indexedLine.indexOf(c);
                if (d >= 0 && d <= totalLength) {
                    breakpoints.add(d);
                }
            }
        }

        return breakpoints;
    }

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
