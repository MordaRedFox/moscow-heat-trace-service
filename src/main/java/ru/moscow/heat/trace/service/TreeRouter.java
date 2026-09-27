package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.service.CoordinateTransformService;
import ru.moscow.heat.spatial.OksConnectionPointResolver;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.graph.ObstacleModel;
import ru.moscow.heat.trace.graph.VisibilityGraph;
import ru.moscow.heat.trace.model.OksGroup;
import ru.moscow.heat.trace.model.RouteTree;
import ru.moscow.heat.trace.model.TreeEdge;
import ru.moscow.heat.trace.model.TreeNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TreeRouter {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final int WGS84_SRID = 4326;
    private static final double COORDINATE_MATCH_EPS_M = 0.5;

    private final OksConnectionPointResolver oksResolver;
    private final CoordinateTransformService coordinateTransformService;

    /**
     * Строит дерево маршрутов для группы ОКС.
     * Возвращает null, если хотя бы для одного ОКС путь не найден.
     */
    public RouteTree buildTree(OksGroup group, UUID uploadId,
                               VisibilityGraph graph,
                               ObstacleModel obstacleModel) {
        TieInCandidate sharedTieIn = group.getSharedTieIn();
        Coordinate tieInUtm = toUtmCoordinate(sharedTieIn);

        List<OksConnectionPointEntity> oksList = group.getPoints();
        List<List<Coordinate>> pathsFromTieIn = new ArrayList<>();

        for (OksConnectionPointEntity oks : oksList) {
            if (!(oks.getGeometryUtm() instanceof Point)) {
                log.warn("ОКС {} не является точкой", oks.getFeatureId());
                return null;
            }
            Coordinate oksUtm = ((Point) oks.getGeometryUtm()).getCoordinate();

            Optional<Long> polygonId = oksResolver
                    .resolvePolygonDatabaseId(uploadId, oks.getFeatureId());
            Set<Long> ignoredIds = polygonId
                    .map(Collections::singleton)
                    .orElseGet(Collections::emptySet);

            // Путь от ОКС к врезке (ignored применяется к старту = ОКС)
            VisibilityGraph.PathResult pathResult = graph.shortestPath(
                    oksUtm, tieInUtm, ignoredIds);

            if (!pathResult.isFound()) {
                log.warn("Путь не найден для ОКС {}", oks.getFeatureId());
                return null;
            }

            // Разворачиваем: теперь от врезки к ОКС
            List<Coordinate> path = new ArrayList<>(pathResult.getPathUtm());
            Collections.reverse(path);
            pathsFromTieIn.add(path);
        }

        return buildTreeFromPaths(pathsFromTieIn, oksList, tieInUtm);
    }

    /**
     * Пакетный доступ для тестирования логики объединения путей без графа.
     */
    RouteTree buildTreeFromPaths(List<List<Coordinate>> paths,
                                 List<OksConnectionPointEntity> oksList,
                                 Coordinate tieInUtm) {
        TreeNode root = new TreeNode(UUID.randomUUID(), tieInUtm,
                TreeNode.TreeNodeType.ROOT, null);

        List<TreeNode> allNodes = new ArrayList<>();
        List<TreeEdge> allEdges = new ArrayList<>();
        List<TreeNode> leaves = new ArrayList<>();
        allNodes.add(root);

        for (int i = 0; i < paths.size(); i++) {
            insertPath(root, paths.get(i), oksList.get(i),
                    allNodes, allEdges, leaves);
        }

        List<TreeNode> branchingNodes = new ArrayList<>();
        for (TreeNode node : allNodes) {
            if (node.isBranching()) {
                node.setType(TreeNode.TreeNodeType.BRANCH);
                branchingNodes.add(node);
            }
        }

        log.info("Дерево: {} узлов, {} рёбер, {} ветвлений, {} ОКС",
                allNodes.size(), allEdges.size(),
                branchingNodes.size(), leaves.size());

        return new RouteTree(root, allNodes, allEdges, leaves, branchingNodes);
    }

    private void insertPath(TreeNode root, List<Coordinate> path,
                            OksConnectionPointEntity oks,
                            List<TreeNode> allNodes,
                            List<TreeEdge> allEdges,
                            List<TreeNode> leaves) {
        if (path.size() < 2) return;

        TreeNode current = root;

        for (int i = 1; i < path.size(); i++) {
            Coordinate coord = path.get(i);
            boolean isLast = (i == path.size() - 1);

            TreeNode existingChild = findChildByCoordinate(current, coord);

            if (existingChild != null) {
                current = existingChild;
            } else {
                TreeNode.TreeNodeType type = isLast
                        ? TreeNode.TreeNodeType.LEAF
                        : TreeNode.TreeNodeType.INTERMEDIATE;
                String featureId = isLast ? oks.getFeatureId() : null;

                TreeNode newNode = new TreeNode(UUID.randomUUID(), coord, type, featureId);
                allNodes.add(newNode);

                List<Coordinate> edgeGeom = List.of(current.getCoordinateUtm(), coord);
                double length = current.getCoordinateUtm().distance(coord);

                TreeEdge edge = new TreeEdge(UUID.randomUUID(), current, newNode, edgeGeom, length);
                current.addChildEdge(edge);
                newNode.setParentEdge(edge);
                allEdges.add(edge);

                if (isLast) {
                    leaves.add(newNode);
                }
                current = newNode;
            }
        }
    }

    private TreeNode findChildByCoordinate(TreeNode node, Coordinate coord) {
        for (TreeEdge edge : node.getChildEdges()) {
            Coordinate c = edge.getTo().getCoordinateUtm();
            if (Math.abs(c.x - coord.x) <= COORDINATE_MATCH_EPS_M
                    && Math.abs(c.y - coord.y) <= COORDINATE_MATCH_EPS_M) {
                return edge.getTo();
            }
        }
        return null;
    }

    private Coordinate toUtmCoordinate(TieInCandidate candidate) {
        Point wgs84 = GEOMETRY_FACTORY.createPoint(
                new Coordinate(candidate.getTargetLongitude(),
                        candidate.getTargetLatitude()));
        wgs84.setSRID(WGS84_SRID);
        Geometry utm = coordinateTransformService.toUtm(wgs84);
        return utm.getCoordinate();
    }
}