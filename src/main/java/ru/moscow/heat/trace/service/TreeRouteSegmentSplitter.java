package ru.moscow.heat.trace.service;

import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.springframework.stereotype.Service;
import ru.moscow.heat.trace.graph.ObstacleModel;
import ru.moscow.heat.trace.model.LayingMethod;
import ru.moscow.heat.trace.model.NewChamber;
import ru.moscow.heat.trace.model.RouteNode;
import ru.moscow.heat.trace.model.RouteNodeType;
import ru.moscow.heat.trace.model.RouteSegment;
import ru.moscow.heat.trace.model.RouteTree;
import ru.moscow.heat.trace.model.TechnicalNode;
import ru.moscow.heat.trace.model.TreeEdge;
import ru.moscow.heat.trace.model.TreeNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Разбивает рёбра дерева маршрутов на {@link RouteSegment} по границам
 * зон специального прохода (итерация 6, шаг 5).
 * <p>
 * Адаптация {@link RouteSegmentSplitter} под дерево:
 * <ul>
 *   <li>каждое {@link TreeEdge} обрабатывается как отдельная полилиния;</li>
 *   <li>узлы дерева ({@link TreeNode}) маппятся на общие {@link RouteNode},
 *       обеспечивая топологию: {@code end_node_id} одного сегмента =
 *       {@code start_node_id} следующего, включая точки ветвления;</li>
 *   <li>для узлов ветвления (BRANCH) создаются {@link NewChamber};</li>
 *   <li>ДУ берётся из {@link TreeEdge#getDiameterMm()} (заполнено на шаге 4);</li>
 *   <li>расход берётся из {@link TreeEdge#getFlowTph()} (заполнено на шаге 3).</li>
 * </ul>
 */
@Slf4j
@Service
public class TreeRouteSegmentSplitter {

    private static final GeometryFactory GF = new GeometryFactory();
    private static final int UTM_SRID = 32637;

    /** Результат разбиения дерева на сегменты. */
    public static final class SplitResult {
        private final List<RouteSegment> segments;
        private final List<TechnicalNode> technicalNodes;
        private final List<NewChamber> newChambers;

        public SplitResult(List<RouteSegment> segments,
                           List<TechnicalNode> technicalNodes,
                           List<NewChamber> newChambers) {
            this.segments = segments;
            this.technicalNodes = technicalNodes;
            this.newChambers = newChambers;
        }

        public List<RouteSegment> getSegments() { return segments; }
        public List<TechnicalNode> getTechnicalNodes() { return technicalNodes; }
        public List<NewChamber> getNewChambers() { return newChambers; }
    }

    /**
     * Разбивает все рёбра дерева на сегменты по границам спецзон.
     *
     * @param tree          дерево маршрутов (после FlowAggregator + TreeDiameterAssigner)
     * @param obstacleModel модель препятствий (для спецзон)
     * @return плоские списки сегментов, техузлов и новых камер
     */
    public SplitResult split(RouteTree tree, ObstacleModel obstacleModel) {
        List<RouteSegment> allSegments = new ArrayList<>();
        List<TechnicalNode> allTechNodes = new ArrayList<>();
        List<NewChamber> allChambers = new ArrayList<>();

        // Кэш RouteNode по TreeNode.id — обеспечивает общую топологию
        Map<UUID, RouteNode> nodeCache = new HashMap<>();

        for (TreeEdge edge : tree.getEdges()) {
            splitEdge(edge, obstacleModel, nodeCache,
                    allSegments, allTechNodes);
        }

        // Создаём камеры для узлов ветвления
        for (TreeNode branchNode : tree.getBranchingNodes()) {
            RouteNode routeNode = nodeCache.get(branchNode.getId());
            if (routeNode != null && routeNode.getType() == RouteNodeType.NEW_CHAMBER) {
                int maxDn = maxDiameterAtNode(branchNode);
                allChambers.add(new NewChamber(
                        routeNode.getId(),
                        branchNode.getCoordinateUtm(),
                        maxDn,
                        null // cost — заготовка под итерацию 7
                ));
            }
        }

        // Камера для корня, если он NEW_CHAMBER
        TreeNode root = tree.getRoot();
        RouteNode rootNode = nodeCache.get(root.getId());
        if (rootNode != null && rootNode.getType() == RouteNodeType.NEW_CHAMBER) {
            int maxDn = maxDiameterAtNode(root);
            allChambers.add(new NewChamber(
                    rootNode.getId(),
                    root.getCoordinateUtm(),
                    maxDn,
                    null
            ));
        }

        log.info("Дерево разбито: {} сегментов, {} техузлов, {} камер",
                allSegments.size(), allTechNodes.size(), allChambers.size());

        return new SplitResult(allSegments, allTechNodes, allChambers);
    }

    /**
     * Разбивает одно ребро дерева по границам спецзон.
     */
    private void splitEdge(TreeEdge edge,
                           ObstacleModel obstacleModel,
                           Map<UUID, RouteNode> nodeCache,
                           List<RouteSegment> allSegments,
                           List<TechnicalNode> allTechNodes) {

        List<Coordinate> coords = edge.getGeometryUtm();
        if (coords.size() < 2) {
            return;
        }

        LineString fullPath = GF.createLineString(
                coords.toArray(new Coordinate[0]));
        fullPath.setSRID(UTM_SRID);
        double totalLength = fullPath.getLength();
        LengthIndexedLine indexedLine = new LengthIndexedLine(fullPath);

        TreeSet<Double> breakpoints = collectBreakpoints(
                fullPath, totalLength, indexedLine, obstacleModel);
        List<Double> sortedBps = new ArrayList<>(breakpoints);

        // Получаем или создаём RouteNode для начала и конца ребра
        RouteNode fromRouteNode = getOrCreateRouteNode(
                edge.getFrom(), nodeCache);
        RouteNode toRouteNode = getOrCreateRouteNode(
                edge.getTo(), nodeCache);

        BigDecimal flowTph = BigDecimal.valueOf(edge.getFlowTph());
        int diameterMm = edge.getDiameterMm();

        List<RouteNode> subNodes = new ArrayList<>();
        for (int i = 0; i < sortedBps.size(); i++) {
            double d = sortedBps.get(i);
            Coordinate coord = indexedLine.extractPoint(d);

            if (i == 0) {
                subNodes.add(fromRouteNode);
            } else if (i == sortedBps.size() - 1) {
                subNodes.add(toRouteNode);
            } else {
                // Промежуточный узел — CORNER или TECHNICAL_NODE
                subNodes.add(new RouteNode(
                        UUID.randomUUID(),
                        RouteNodeType.CORNER,
                        coord,
                        null));
            }
        }

        for (int i = 0; i < subNodes.size() - 1; i++) {
            RouteNode from = subNodes.get(i);
            RouteNode to = subNodes.get(i + 1);

            LineString subGeom = GF.createLineString(
                    new Coordinate[]{
                            from.getCoordinateUtm(),
                            to.getCoordinateUtm()
                    });
            subGeom.setSRID(UTM_SRID);

            Coordinate mid = new Coordinate(
                    (from.getCoordinateUtm().x + to.getCoordinateUtm().x) / 2.0,
                    (from.getCoordinateUtm().y + to.getCoordinateUtm().y) / 2.0);
            double kspets = maxKspetsAt(mid, obstacleModel);
            LayingMethod layingMethod =
                    kspets > 1.0 ? LayingMethod.SPECIAL : LayingMethod.BASE;

            RouteSegment segment = new RouteSegment(
                    UUID.randomUUID(), from, to, subGeom,
                    flowTph, diameterMm, layingMethod,
                    kspets, subGeom.getLength(), null);
            allSegments.add(segment);

            // Технический узел на промежуточных границах
            if (i > 0) {
                LayingMethod prevMethod = allSegments.get(
                        allSegments.size() - 2).getLayingMethod();
                TechnicalNode.Reason reason =
                        (prevMethod != layingMethod)
                                ? TechnicalNode.Reason.METHOD_CHANGE
                                : TechnicalNode.Reason.ZONE_BOUNDARY;
                allTechNodes.add(new TechnicalNode(
                        UUID.randomUUID(),
                        from.getCoordinateUtm(),
                        reason));
            }
        }
    }

    /**
     * Маппит TreeNode → RouteNode, используя кэш для сохранения топологии.
     */
    private RouteNode getOrCreateRouteNode(TreeNode treeNode,
                                           Map<UUID, RouteNode> cache) {
        return cache.computeIfAbsent(treeNode.getId(), id -> {
            RouteNodeType type;
            String sourceFeatureId = null;

            switch (treeNode.getType()) {
                case ROOT:
                    // Корень — точка врезки. Если существующая камера —
                    // EXISTING_CHAMBER, иначе NEW_CHAMBER.
                    // Упрощение: определяем по наличию oksFeatureId
                    type = RouteNodeType.NEW_CHAMBER;
                    break;
                case LEAF:
                    type = RouteNodeType.OKS_POINT;
                    sourceFeatureId = treeNode.getOksFeatureId();
                    break;
                case BRANCH:
                    type = RouteNodeType.NEW_CHAMBER;
                    break;
                default:
                    type = RouteNodeType.CORNER;
                    break;
            }

            return new RouteNode(id, type,
                    treeNode.getCoordinateUtm(), sourceFeatureId);
        });
    }

    /** Максимальный ДУ среди всех примыкающих к узлу рёбер. */
    private int maxDiameterAtNode(TreeNode node) {
        int max = 0;
        if (node.getParentEdge() != null) {
            max = Math.max(max, node.getParentEdge().getDiameterMm());
        }
        for (TreeEdge child : node.getChildEdges()) {
            max = Math.max(max, child.getDiameterMm());
        }
        return max;
    }

    /** Собирает точки разбиения по границам спецзон (аналог текущего splitter). */
    private TreeSet<Double> collectBreakpoints(LineString fullPath,
                                               double totalLength,
                                               LengthIndexedLine indexedLine,
                                               ObstacleModel obstacleModel) {
        TreeSet<Double> breakpoints = new TreeSet<>();
        breakpoints.add(0.0);
        breakpoints.add(totalLength);

        for (ObstacleModel.SpecialZone zone : obstacleModel.getSpecialZones()) {
            Geometry intersection = fullPath.intersection(
                    zone.getBufferedGeometryUtm());
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
        Point p = GF.createPoint(point);
        double maxKspets = 1.0;
        for (ObstacleModel.SpecialZone zone : obstacleModel.getSpecialZones()) {
            if (zone.getBufferedGeometryUtm().covers(p)) {
                maxKspets = Math.max(maxKspets, zone.getKspets());
            }
        }
        return maxKspets;
    }
}