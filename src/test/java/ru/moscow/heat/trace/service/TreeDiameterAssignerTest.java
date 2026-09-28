package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import ru.moscow.heat.spatial.DiameterTable;
import ru.moscow.heat.trace.model.RouteTree;
import ru.moscow.heat.trace.model.TreeEdge;
import ru.moscow.heat.trace.model.TreeNode;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты {@link TreeDiameterAssigner} (итерация 6, шаг 4).
 *
 * <p>Проверяются:
 * <ul>
 *   <li>подбор ДУ по расходу ребра;</li>
 *   <li>инвариант монотонности: ДУ не убывает от листа к корню;</li>
 *   <li>каскадное повышение ДУ ствола при повышении ДУ ветви;</li>
 *   <li>предельная длина: длинный путь повышает ДУ участка;</li>
 *   <li>общий участок учитывается в каждом пути лист→корень.</li>
 * </ul>
 */
class TreeDiameterAssignerTest {

    private final DiameterTable table = new DiameterTable();
    private final TreeDiameterAssigner assigner = new TreeDiameterAssigner(table);

    // ---------- хелперы ----------

    private TreeNode node(double x, double y,
                          TreeNode.TreeNodeType type, String oksFeatureId) {
        return new TreeNode(UUID.randomUUID(), new Coordinate(x, y), type, oksFeatureId);
    }

    /** Ребро от родителя (ближе к корню) к ребёнку (дальше от корня). */
    private TreeEdge edge(TreeNode from, TreeNode to) {
        double len = from.getCoordinateUtm().distance(to.getCoordinateUtm());
        List<Coordinate> geom = List.of(from.getCoordinateUtm(), to.getCoordinateUtm());
        TreeEdge e = new TreeEdge(UUID.randomUUID(), from, to, geom, len);
        from.addChildEdge(e);
        to.setParentEdge(e);
        return e;
    }

    private RouteTree tree(TreeNode root, List<TreeNode> nodes, List<TreeEdge> edges,
                           List<TreeNode> leaves, List<TreeNode> branches) {
        return new RouteTree(root, nodes, edges, leaves, branches);
    }

    // ---------- тесты ----------

    @Test
    @DisplayName("Одиночный ОКС: ДУ по расходу, путь короткий")
    void singleOks_diameterByFlow() {
        TreeNode root = node(0, 0, TreeNode.TreeNodeType.ROOT, null);
        TreeNode leaf = node(50, 0, TreeNode.TreeNodeType.LEAF, "oks-1");
        TreeEdge only = edge(root, leaf);
        only.setFlowTph(20.0); // ДУ 100 (22.3 >= 20)

        RouteTree tree = tree(root, List.of(root, leaf), List.of(only),
                List.of(leaf), List.of());
        assigner.assign(tree);

        assertThat(only.getDiameterMm()).isEqualTo(100);
    }

    @Test
    @DisplayName("Общий ствол: ДУ ствола больше ветвей из-за суммарного расхода")
    void sharedTrunk_largerDiameterThanBranches() {
        TreeNode root = node(0, 0, TreeNode.TreeNodeType.ROOT, null);
        TreeNode branch = node(100, 0, TreeNode.TreeNodeType.BRANCH, null);
        TreeNode leaf1 = node(100, 50, TreeNode.TreeNodeType.LEAF, "oks-1");
        TreeNode leaf2 = node(100, -50, TreeNode.TreeNodeType.LEAF, "oks-2");

        TreeEdge trunk = edge(root, branch);
        TreeEdge b1 = edge(branch, leaf1);
        TreeEdge b2 = edge(branch, leaf2);

        trunk.setFlowTph(60.0); // 150: 65.1 >= 60
        b1.setFlowTph(30.0);    // 125: 40.2 >= 30
        b2.setFlowTph(30.0);

        RouteTree tree = tree(root, List.of(root, branch, leaf1, leaf2),
                List.of(trunk, b1, b2), List.of(leaf1, leaf2), List.of(branch));
        assigner.assign(tree);

        assertThat(trunk.getDiameterMm()).isEqualTo(150);
        assertThat(b1.getDiameterMm()).isEqualTo(125);
        assertThat(b2.getDiameterMm()).isEqualTo(125);
    }

    @Test
    @DisplayName("Монотонность: ДУ не убывает от листа к корню")
    void monotonicNonDecreasing_towardsRoot() {
        TreeNode root = node(0, 0, TreeNode.TreeNodeType.ROOT, null);
        TreeNode mid = node(100, 0, TreeNode.TreeNodeType.BRANCH, null);
        TreeNode leaf = node(100, 50, TreeNode.TreeNodeType.LEAF, "oks-1");

        TreeEdge trunk = edge(root, mid);
        TreeEdge branch = edge(mid, leaf);
        trunk.setFlowTph(40.0);  // 125
        branch.setFlowTph(40.0); // 125

        RouteTree tree = tree(root, List.of(root, mid, leaf),
                List.of(trunk, branch), List.of(leaf), List.of(mid));
        assigner.assign(tree);

        // от листа к корню: ДУ ветви <= ДУ ствола
        assertThat(trunk.getDiameterMm())
                .isGreaterThanOrEqualTo(branch.getDiameterMm());
    }

    @Test
    @DisplayName("Каскадная монотонность: повышение ДУ в ветви поднимает ДУ ствола")
    void cascadeMonotonicity_branchRaisesTrunk() {
        // Ствол 100 м, две ветви по 200 м каждая.
        // Малый расход → ДУ 50 не проходит по предельной длине ветви (181 м).
        // Ветвь поднимается до 65 (предел 245 м).
        // Ствол обязан стать не меньше ветвей по инварианту монотонности.
        TreeNode root = node(0, 0, TreeNode.TreeNodeType.ROOT, null);
        TreeNode branch = node(100, 0, TreeNode.TreeNodeType.BRANCH, null);
        TreeNode leaf1 = node(100, 200, TreeNode.TreeNodeType.LEAF, "oks-1");
        TreeNode leaf2 = node(100, -200, TreeNode.TreeNodeType.LEAF, "oks-2");

        TreeEdge trunk = edge(root, branch);
        TreeEdge b1 = edge(branch, leaf1);
        TreeEdge b2 = edge(branch, leaf2);

        trunk.setFlowTph(4.0);  // → ДУ 65 по расходу
        b1.setFlowTph(2.0);     // → ДУ 50 по расходу, но 200 м > 181 → 65
        b2.setFlowTph(2.0);

        // Форсируем ДУ 50, чтобы тест проверял именно каскад
        trunk.setDiameterMm(50);
        b1.setDiameterMm(50);
        b2.setDiameterMm(50);

        RouteTree tree = tree(root, List.of(root, branch, leaf1, leaf2),
                List.of(trunk, b1, b2), List.of(leaf1, leaf2), List.of(branch));
        assigner.assign(tree);

        // Ветви длиной 200 м не проходят по ДУ 50 (предел 181) → поднимаются.
        assertThat(b1.getDiameterMm()).isGreaterThanOrEqualTo(65);
        assertThat(b2.getDiameterMm()).isGreaterThanOrEqualTo(65);
        // Ствол не меньше ветвей.
        assertThat(trunk.getDiameterMm())
                .isGreaterThanOrEqualTo(b1.getDiameterMm());
        assertThat(trunk.getDiameterMm())
                .isGreaterThanOrEqualTo(b2.getDiameterMm());
    }

    @Test
    @DisplayName("Предельная длина: длинный путь повышает ДУ участка")
    void longPath_raisesDiameter() {
        // ДУ 50: предел 181 м. Путь 200 м с малым расходом 2 т/ч.
        // Минимальный по расходу ДУ=50 не проходит по длине → повышаем.
        TreeNode root = node(0, 0, TreeNode.TreeNodeType.ROOT, null);
        TreeNode leaf = node(200, 0, TreeNode.TreeNodeType.LEAF, "oks-1");
        TreeEdge only = edge(root, leaf); // длина 200 м
        only.setFlowTph(2.0);

        RouteTree tree = tree(root, List.of(root, leaf), List.of(only),
                List.of(leaf), List.of());
        assigner.assign(tree);

        // ДУ должен быть таким, что maxLen >= 200 и capacity >= 2
        assertThat(only.getDiameterMm()).isGreaterThan(50);
        assertThat(table.findByDiameter(only.getDiameterMm()).orElseThrow()
                .getMaxLengthM()).isGreaterThanOrEqualTo(200.0);
    }

    @Test
    @DisplayName("Общий участок учитывается в каждом пути")
    void sharedSegment_accountedInEveryPath() {
        // Ствол 150 м, две ветви по 60 м. Путь каждого ОКС: 150 + 60.
        TreeNode root = node(0, 0, TreeNode.TreeNodeType.ROOT, null);
        TreeNode branch = node(150, 0, TreeNode.TreeNodeType.BRANCH, null);
        TreeNode leaf1 = node(150, 60, TreeNode.TreeNodeType.LEAF, "oks-1");
        TreeNode leaf2 = node(150, -60, TreeNode.TreeNodeType.LEAF, "oks-2");

        TreeEdge trunk = edge(root, branch);
        TreeEdge b1 = edge(branch, leaf1);
        TreeEdge b2 = edge(branch, leaf2);
        trunk.setFlowTph(6.0);  // ДУ 65 (8.3 >= 6), предел 245
        b1.setFlowTph(3.0);     // ДУ 50 (3.5 >= 3), предел 181
        b2.setFlowTph(3.0);

        RouteTree tree = tree(root, List.of(root, branch, leaf1, leaf2),
                List.of(trunk, b1, b2), List.of(leaf1, leaf2), List.of(branch));
        assigner.assign(tree);

        assertThat(trunk.getDiameterMm()).isEqualTo(65);
        assertThat(b1.getDiameterMm()).isEqualTo(50);
        assertThat(b2.getDiameterMm()).isEqualTo(50);
    }
}
