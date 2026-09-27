package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import ru.moscow.heat.trace.graph.ObstacleModel;
import ru.moscow.heat.trace.model.NewChamber;
import ru.moscow.heat.trace.model.RouteNode;
import ru.moscow.heat.trace.model.RouteSegment;
import ru.moscow.heat.trace.model.RouteTree;
import ru.moscow.heat.trace.model.TreeEdge;
import ru.moscow.heat.trace.model.TreeNode;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TreeRouteSegmentSplitterTest {

    private final TreeRouteSegmentSplitter splitter = new TreeRouteSegmentSplitter();

    private TreeNode node(double x, double y,
                          TreeNode.TreeNodeType type, String oksId) {
        return new TreeNode(UUID.randomUUID(), new Coordinate(x, y), type, oksId);
    }

    private TreeEdge edge(TreeNode from, TreeNode to) {
        double len = from.getCoordinateUtm().distance(to.getCoordinateUtm());
        List<Coordinate> geom = List.of(from.getCoordinateUtm(), to.getCoordinateUtm());
        TreeEdge e = new TreeEdge(UUID.randomUUID(), from, to, geom, len);
        from.addChildEdge(e);
        to.setParentEdge(e);
        return e;
    }

    @Test
    @DisplayName("Одиночное ребро без спецзон → один сегмент")
    void singleEdge_noSpecialZones_oneSegment() {
        TreeNode root = node(0, 0, TreeNode.TreeNodeType.ROOT, null);
        TreeNode leaf = node(100, 0, TreeNode.TreeNodeType.LEAF, "oks-1");
        TreeEdge e = edge(root, leaf);
        e.setFlowTph(20.0);
        e.setDiameterMm(100);

        RouteTree tree = new RouteTree(root, List.of(root, leaf),
                List.of(e), List.of(leaf), List.of());

        ObstacleModel emptyModel = new ObstacleModel(List.of(), List.of());
        TreeRouteSegmentSplitter.SplitResult result = splitter.split(tree, emptyModel);

        assertThat(result.getSegments()).hasSize(1);
        RouteSegment seg = result.getSegments().get(0);
        assertThat(seg.getDiameterMm()).isEqualTo(100);
        assertThat(seg.getFlowTph().doubleValue()).isEqualTo(20.0);
        assertThat(seg.getLengthM()).isCloseTo(100.0,
                org.assertj.core.data.Offset.offset(0.01));

        // Топология: fromNode = корень, toNode = лист
        assertThat(seg.getFromNode().getType()).isEqualTo(
                ru.moscow.heat.trace.model.RouteNodeType.NEW_CHAMBER);
        assertThat(seg.getToNode().getType()).isEqualTo(
                ru.moscow.heat.trace.model.RouteNodeType.OKS_POINT);
        assertThat(seg.getToNode().getSourceFeatureId()).isEqualTo("oks-1");
    }

    @Test
    @DisplayName("Дерево с ветвлением → общая топология RouteNode")
    void branchingTree_sharedTopology() {
        TreeNode root = node(0, 0, TreeNode.TreeNodeType.ROOT, null);
        TreeNode branch = node(100, 0, TreeNode.TreeNodeType.BRANCH, null);
        TreeNode leaf1 = node(100, 50, TreeNode.TreeNodeType.LEAF, "oks-1");
        TreeNode leaf2 = node(100, -50, TreeNode.TreeNodeType.LEAF, "oks-2");

        TreeEdge trunk = edge(root, branch);
        TreeEdge b1 = edge(branch, leaf1);
        TreeEdge b2 = edge(branch, leaf2);

        trunk.setFlowTph(40.0); trunk.setDiameterMm(150);
        b1.setFlowTph(20.0); b1.setDiameterMm(100);
        b2.setFlowTph(20.0); b2.setDiameterMm(100);

        RouteTree tree = new RouteTree(root,
                List.of(root, branch, leaf1, leaf2),
                List.of(trunk, b1, b2),
                List.of(leaf1, leaf2),
                List.of(branch));

        ObstacleModel emptyModel = new ObstacleModel(List.of(), List.of());
        TreeRouteSegmentSplitter.SplitResult result = splitter.split(tree, emptyModel);

        assertThat(result.getSegments()).hasSize(3);

        // Проверка топологии: конец ствола = начало ветвей
        RouteSegment segTrunk = result.getSegments().stream()
                .filter(s -> s.getDiameterMm() == 150)
                .findFirst().orElseThrow();
        RouteNode branchNode = segTrunk.getToNode();

        List<RouteSegment> branches = result.getSegments().stream()
                .filter(s -> s.getDiameterMm() == 100)
                .collect(Collectors.toList());
        assertThat(branches).hasSize(2);
        for (RouteSegment br : branches) {
            // end_node_id ствола = start_node_id ветви
            assertThat(br.getFromNode().getId()).isEqualTo(branchNode.getId());
        }

        // Камера ветвления создана
        assertThat(result.getNewChambers()).hasSizeGreaterThanOrEqualTo(1);
        NewChamber chamber = result.getNewChambers().stream()
                .filter(c -> c.getDiameterMm() == 150)
                .findFirst().orElse(null);
        assertThat(chamber).isNotNull();
    }

    @Test
    @DisplayName("Сегменты наследуют ДУ и расход из TreeEdge")
    void segments_inheritDiameterAndFlow() {
        TreeNode root = node(0, 0, TreeNode.TreeNodeType.ROOT, null);
        TreeNode leaf = node(50, 0, TreeNode.TreeNodeType.LEAF, "oks-1");
        TreeEdge e = edge(root, leaf);
        e.setFlowTph(12.5);
        e.setDiameterMm(80);

        RouteTree tree = new RouteTree(root, List.of(root, leaf),
                List.of(e), List.of(leaf), List.of());

        ObstacleModel emptyModel = new ObstacleModel(List.of(), List.of());
        TreeRouteSegmentSplitter.SplitResult result = splitter.split(tree, emptyModel);

        RouteSegment seg = result.getSegments().get(0);
        assertThat(seg.getDiameterMm()).isEqualTo(80);
        assertThat(seg.getFlowTph().doubleValue()).isEqualTo(12.5);
    }
}