package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TieInType;
import ru.moscow.heat.trace.graph.ObstacleModel;
import ru.moscow.heat.trace.model.RouteNode;
import ru.moscow.heat.trace.model.RouteNodeType;
import ru.moscow.heat.trace.model.RouteSegment;
import ru.moscow.heat.trace.model.RouteTree;
import ru.moscow.heat.trace.model.TreeEdge;
import ru.moscow.heat.trace.model.TreeNode;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты {@link TreeRouteSegmentSplitter} (итерация 6, шаг 5).
 *
 * <p>Проверяются:
 * <ul>
 *   <li>разбиение одного ребра без спецзон на один сегмент;</li>
 *   <li>проставление типа корневого узла по {@code sharedTieIn}:
 *       {@link RouteNodeType#EXISTING_CHAMBER} (без создания камеры)
 *       или {@link RouteNodeType#NEW_CHAMBER};</li>
 *   <li>сохранение топологии {@link RouteNode} между соседними
 *       сегментами и через точки ветвления;</li>
 *   <li>создание камер для узлов ветвления и для корня (только для
 *       случая {@code NEW_CHAMBER});</li>
 *   <li>наследование ДУ и расхода из {@link TreeEdge}.</li>
 * </ul>
 */
class TreeRouteSegmentSplitterTest {

    private final TreeRouteSegmentSplitter splitter = new TreeRouteSegmentSplitter();

    private TreeNode node(double x, double y,
                          TreeNode.TreeNodeType type, String oksId) {
        return new TreeNode(UUID.randomUUID(), new Coordinate(x, y), type, oksId);
    }

    private TreeEdge edge(TreeNode from, TreeNode to) {
        double len = from.getCoordinateUtm().distance(to.getCoordinateUtm());
        List<Coordinate> geom = List.of(from.getCoordinateUtm(),
                to.getCoordinateUtm());
        TreeEdge e = new TreeEdge(UUID.randomUUID(), from, to, geom, len);
        from.addChildEdge(e);
        to.setParentEdge(e);
        return e;
    }

    private TieInCandidate newChamberTieIn() {
        return TieInCandidate.builder()
                .id("tie-new")
                .connectionPointId("shared")
                .type(TieInType.NEW_CHAMBER)
                .tieInLongitude(0).tieInLatitude(0)
                .targetLongitude(0).targetLatitude(0)
                .distanceToNetworkM(0).distanceToChamberM(0)
                .currentAttachments(0).cost(0)
                .build();
    }

    private TieInCandidate existingChamberTieIn(String chamberId) {
        return TieInCandidate.builder()
                .id("tie-existing")
                .connectionPointId("shared")
                .type(TieInType.EXISTING_CHAMBER)
                .existingChamberId(chamberId)
                .tieInLongitude(0).tieInLatitude(0)
                .targetLongitude(0).targetLatitude(0)
                .distanceToNetworkM(0).distanceToChamberM(0)
                .currentAttachments(0).cost(5_000_000L)
                .build();
    }

    private ObstacleModel emptyModel() {
        return new ObstacleModel(
                Collections.<ObstacleModel.ForbiddenZone>emptyList(),
                Collections.<ObstacleModel.SpecialZone>emptyList());
    }

    @Test
    @DisplayName("Одиночное ребро без спецзон → один сегмент, корень NEW_CHAMBER")
    void singleEdge_noSpecialZones_oneSegment() {
        TreeNode root = node(0, 0, TreeNode.TreeNodeType.ROOT, null);
        TreeNode leaf = node(100, 0, TreeNode.TreeNodeType.LEAF, "oks-1");
        TreeEdge e = edge(root, leaf);
        e.setFlowTph(20.0);
        e.setDiameterMm(100);

        RouteTree tree = new RouteTree(root, List.of(root, leaf),
                List.of(e), List.of(leaf), List.of());

        TreeRouteSegmentSplitter.SplitResult result =
                splitter.split(tree, emptyModel(), newChamberTieIn());

        assertThat(result.getSegments()).hasSize(1);
        RouteSegment seg = result.getSegments().get(0);
        assertThat(seg.getDiameterMm()).isEqualTo(100);
        assertThat(seg.getFlowTph().doubleValue()).isEqualTo(20.0);
        assertThat(seg.getLengthM()).isCloseTo(100.0,
                org.assertj.core.data.Offset.offset(0.01));

        assertThat(seg.getFromNode().getType())
                .isEqualTo(RouteNodeType.NEW_CHAMBER);
        assertThat(seg.getToNode().getType())
                .isEqualTo(RouteNodeType.OKS_POINT);
        assertThat(seg.getToNode().getSourceFeatureId()).isEqualTo("oks-1");

        // Корень NEW_CHAMBER → ровно одна новая камера
        assertThat(result.getNewChambers()).hasSize(1);
        assertThat(result.getNewChambers().get(0).getDiameterMm())
                .isEqualTo(100);
    }

    @Test
    @DisplayName("Корень EXISTING_CHAMBER → новая камера не создаётся")
    void existingChamberRoot_noNewChamber() {
        TreeNode root = node(0, 0, TreeNode.TreeNodeType.ROOT, null);
        TreeNode leaf = node(100, 0, TreeNode.TreeNodeType.LEAF, "oks-1");
        TreeEdge e = edge(root, leaf);
        e.setFlowTph(15.0);
        e.setDiameterMm(80);

        RouteTree tree = new RouteTree(root, List.of(root, leaf),
                List.of(e), List.of(leaf), List.of());

        TreeRouteSegmentSplitter.SplitResult result =
                splitter.split(tree, emptyModel(),
                        existingChamberTieIn("ch-42"));

        assertThat(result.getSegments()).hasSize(1);
        RouteSegment seg = result.getSegments().get(0);

        assertThat(seg.getFromNode().getType())
                .isEqualTo(RouteNodeType.EXISTING_CHAMBER);
        assertThat(seg.getFromNode().getSourceFeatureId())
                .isEqualTo("ch-42");

        // Никаких новых камер не создаётся
        assertThat(result.getNewChambers()).isEmpty();
    }

    @Test
    @DisplayName("Ветвление: топология и камера ветвления")
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

        TreeRouteSegmentSplitter.SplitResult result =
                splitter.split(tree, emptyModel(), newChamberTieIn());

        assertThat(result.getSegments()).hasSize(3);

        RouteSegment segTrunk = result.getSegments().stream()
                .filter(s -> s.getDiameterMm() == 150)
                .findFirst().orElseThrow();
        RouteNode branchNode = segTrunk.getToNode();

        List<RouteSegment> branches = result.getSegments().stream()
                .filter(s -> s.getDiameterMm() == 100)
                .collect(Collectors.toList());
        assertThat(branches).hasSize(2);
        for (RouteSegment br : branches) {
            assertThat(br.getFromNode().getId())
                    .isEqualTo(branchNode.getId());
        }

        // Камеры: одна для branch + одна для root = 2
        assertThat(result.getNewChambers()).hasSize(2);
        assertThat(result.getNewChambers())
                .allMatch(c -> c.getDiameterMm() == 150);
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

        TreeRouteSegmentSplitter.SplitResult result =
                splitter.split(tree, emptyModel(), newChamberTieIn());

        RouteSegment seg = result.getSegments().get(0);
        assertThat(seg.getDiameterMm()).isEqualTo(80);
        assertThat(seg.getFlowTph().doubleValue()).isEqualTo(12.5);
    }
}
