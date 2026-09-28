package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import ru.moscow.heat.trace.model.RouteTree;
import ru.moscow.heat.trace.model.TreeEdge;
import ru.moscow.heat.trace.model.TreeNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlowAggregatorTest {

    private final FlowAggregator aggregator = new FlowAggregator();

    private TreeEdge edge(TreeNode from, TreeNode to) {
        List<Coordinate> geom = List.of(from.getCoordinateUtm(), to.getCoordinateUtm());
        double len = from.getCoordinateUtm().distance(to.getCoordinateUtm());
        TreeEdge e = new TreeEdge(UUID.randomUUID(), from, to, geom, len);
        from.addChildEdge(e);
        to.setParentEdge(e);
        return e;
    }

    @Test
    @DisplayName("3 ОКС с общим стволом: ствол = сумма, ветви = по одному ОКС")
    void threeOksSharedTrunk_flowAggregated() {
        TreeNode root = new TreeNode(UUID.randomUUID(), new Coordinate(0, 0),
                TreeNode.TreeNodeType.ROOT, null);
        TreeNode branch = new TreeNode(UUID.randomUUID(), new Coordinate(100, 0),
                TreeNode.TreeNodeType.BRANCH, null);
        TreeNode leaf1 = new TreeNode(UUID.randomUUID(), new Coordinate(100, 100),
                TreeNode.TreeNodeType.LEAF, "oks-1");
        TreeNode leaf2 = new TreeNode(UUID.randomUUID(), new Coordinate(200, 0),
                TreeNode.TreeNodeType.LEAF, "oks-2");
        TreeNode leaf3 = new TreeNode(UUID.randomUUID(), new Coordinate(100, -100),
                TreeNode.TreeNodeType.LEAF, "oks-3");

        TreeEdge trunk = edge(root, branch);
        TreeEdge e1 = edge(branch, leaf1);
        TreeEdge e2 = edge(branch, leaf2);
        TreeEdge e3 = edge(branch, leaf3);

        RouteTree tree = new RouteTree(root,
                List.of(root, branch, leaf1, leaf2, leaf3),
                List.of(trunk, e1, e2, e3),
                List.of(leaf1, leaf2, leaf3),
                List.of(branch));

        Map<String, Double> flows = Map.of("oks-1", 10.0, "oks-2", 20.0, "oks-3", 30.0);
        aggregator.aggregate(tree, flows);

        assertThat(trunk.getFlowTph()).isEqualTo(60.0);
        assertThat(e1.getFlowTph()).isEqualTo(10.0);
        assertThat(e2.getFlowTph()).isEqualTo(20.0);
        assertThat(e3.getFlowTph()).isEqualTo(30.0);

        assertThat(trunk.getServedOksIds()).containsExactlyInAnyOrder("oks-1", "oks-2", "oks-3");
        assertThat(e1.getServedOksIds()).containsExactly("oks-1");
    }

    @Test
    @DisplayName("Одиночный ОКС: расход ребра = расход одного ОКС")
    void singleOks_flowEqualsLeaf() {
        TreeNode root = new TreeNode(UUID.randomUUID(), new Coordinate(0, 0),
                TreeNode.TreeNodeType.ROOT, null);
        TreeNode leaf = new TreeNode(UUID.randomUUID(), new Coordinate(50, 0),
                TreeNode.TreeNodeType.LEAF, "oks-1");
        TreeEdge only = edge(root, leaf);

        RouteTree tree = new RouteTree(root, List.of(root, leaf),
                List.of(only), List.of(leaf), List.of());

        aggregator.aggregate(tree, Map.of("oks-1", 15.5));

        assertThat(only.getFlowTph()).isEqualTo(15.5);
        assertThat(only.getServedOksIds()).containsExactly("oks-1");
    }

    @Test
    @DisplayName("Вложенное ветвление: расход накапливается по уровням")
    void nestedBranching_flowAccumulatesPerLevel() {
        TreeNode root = new TreeNode(UUID.randomUUID(), new Coordinate(0, 0),
                TreeNode.TreeNodeType.ROOT, null);
        TreeNode b1 = new TreeNode(UUID.randomUUID(), new Coordinate(100, 0),
                TreeNode.TreeNodeType.BRANCH, null);
        TreeNode b2 = new TreeNode(UUID.randomUUID(), new Coordinate(200, 0),
                TreeNode.TreeNodeType.BRANCH, null);
        TreeNode leaf1 = new TreeNode(UUID.randomUUID(), new Coordinate(0, 100),
                TreeNode.TreeNodeType.LEAF, "oks-1");
        TreeNode leaf2 = new TreeNode(UUID.randomUUID(), new Coordinate(100, 100),
                TreeNode.TreeNodeType.LEAF, "oks-2");
        TreeNode leaf3 = new TreeNode(UUID.randomUUID(), new Coordinate(300, 0),
                TreeNode.TreeNodeType.LEAF, "oks-3");

        TreeEdge rootToB1 = edge(root, b1);
        TreeEdge rootToLeaf1 = edge(root, leaf1);
        TreeEdge b1ToB2 = edge(b1, b2);
        TreeEdge b1ToLeaf2 = edge(b1, leaf2);
        TreeEdge b2ToLeaf3 = edge(b2, leaf3);

        RouteTree tree = new RouteTree(root,
                List.of(root, b1, b2, leaf1, leaf2, leaf3),
                List.of(rootToB1, rootToLeaf1, b1ToB2, b1ToLeaf2, b2ToLeaf3),
                List.of(leaf1, leaf2, leaf3),
                List.of(b1, b2));

        aggregator.aggregate(tree, Map.of("oks-1", 5.0, "oks-2", 7.0, "oks-3", 11.0));

        assertThat(rootToB1.getFlowTph()).isEqualTo(18.0);
        assertThat(rootToLeaf1.getFlowTph()).isEqualTo(5.0);
        assertThat(b1ToB2.getFlowTph()).isEqualTo(11.0);
        assertThat(b1ToLeaf2.getFlowTph()).isEqualTo(7.0);
        assertThat(b2ToLeaf3.getFlowTph()).isEqualTo(11.0);
    }
}
