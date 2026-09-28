package ru.moscow.heat.trace.service;

import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.springframework.stereotype.Service;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TieInType;
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
 *   <li>узлы дерева ({@link TreeNode}) маппятся на общие {@link RouteNode}
 *       через кэш по {@code TreeNode.id} — обеспечивается топология:
 *       {@code end_node_id} одного сегмента совпадает со
 *       {@code start_node_id} следующего, включая точки ветвления;</li>
 *   <li>корневой узел получает тип исходя из {@link TieInCandidate}
 *       группы: для {@link TieInType#EXISTING_CHAMBER} — существующая
 *       камера с соответствующим {@code sourceFeatureId}, новая камера
 *       НЕ создаётся; для {@link TieInType#NEW_CHAMBER} — новая камера;</li>
 *   <li>узлы ветвления (BRANCH) всегда становятся новыми камерами;</li>
 *   <li>ДУ берётся из {@link TreeEdge#getDiameterMm()} (шаг 4);</li>
 *   <li>расход берётся из {@link TreeEdge#getFlowTph()} (шаг 3).</li>
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
     * @param sharedTieIn   кандидат врезки группы — определяет тип корневого узла
     * @return плоские списки сегментов, техузлов и новых камер
     */
    public SplitResult split(RouteTree tree,
                             ObstacleModel obstacleModel,
                             TieInCandidate sharedTieIn) {
        List<RouteSegment> allSegments = new ArrayList<>();
        List<TechnicalNode> allTechNodes = new ArrayList<>();
        List<NewChamber> allChambers = new ArrayList<>();

        TreeNode root = tree.getRoot();

        // Определяем тип корневого узла заранее
        RouteNodeType rootType;
        String rootFeatureId = null;
        if (sharedTieIn.getType() == TieInType.EXISTING_CHAMBER) {
            rootType = RouteNodeType.EXISTING_CHAMBER;
            rootFeatureId = sharedTieIn.getExistingChamberId();
        } else {
            rootType = RouteNodeType.NEW_CHAMBER;
        }

        // Кэш RouteNode по TreeNode.id — общая топология
        Map<UUID, RouteNode> nodeCache = new HashMap<>();
        RouteNode rootRouteNode = new RouteNode(
                root.getId(), rootType, root.getCoordinateUtm(), rootFeatureId);
        nodeCache.put(root.getId(), rootRouteNode);

        for (TreeEdge edge : tree.getEdges()) {
            splitEdge(edge, obstacleModel, nodeCache,
                    allSegments, allTechNodes);
        }

        // Камеры для узлов ветвления (BRANCH всегда NEW_CHAMBER)
        for (TreeNode branchNode : tree.getBranchingNodes()) {
            RouteNode routeNode = nodeCache.get(branchNode.getId());
            if (routeNode != null
                    && routeNode.getType() == RouteNodeType.NEW_CHAMBER) {
                int maxDn = maxDiameterAtNode(branchNode);
                allChambers.add(new NewChamber(
                        routeNode.getId(),
                        branchNode.getCoordinateUtm(),
                        maxDn,
                        null
                ));
            }
        }

        // Камера для корня — только если корень NEW_CHAMBER
        if (rootType == RouteNodeType.NEW_CHAMBER) {
            int maxDn = maxDiameterAtNode(root);
            allChambers.add(new NewChamber(
                    rootRouteNode.getId(),
                    root.getCoordinateUtm(),
                    maxDn,
                    null
            ));
        }

        log.info("Дерево разбито: {} сегментов, {} техузлов, {} камер "
                        + "(root={})",
                allSegments.size(), allTechNodes.size(),
                allChambers.size(), rootType);

        return new SplitResult(allSegments, allTechNodes, allChambers);
    }

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
     * Маппит TreeNode → RouteNode через кэш. ROOT должен быть
     * предварительно помещён в кэш (см. {@link #split}); попытка
     * создать ROOT здесь — ошибка программирования.
     */
    private RouteNode getOrCreateRouteNode(TreeNode treeNode,
                                           Map<UUID, RouteNode> cache) {
        return cache.computeIfAbsent(treeNode.getId(), id -> {
            if (treeNode.isRoot()) {
                throw new IllegalStateException(
                        "ROOT должен быть предварительно помещён в кэш");
            }
            RouteNodeType type;
            String sourceFeatureId = null;
            switch (treeNode.getType()) {
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
