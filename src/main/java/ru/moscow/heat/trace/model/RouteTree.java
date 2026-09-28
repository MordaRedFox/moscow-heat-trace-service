package ru.moscow.heat.trace.model;

import java.util.List;
import java.util.Objects;

/**
 * Дерево маршрутов группы ОКС от общей точки врезки (итерация 6, шаг 2).
 * <p>
 * Иммутабельная модель-контейнер (по аналогии с {@link TraceResult}).
 * Удерживает корень, все узлы/рёбра, листья и узлы ветвления.
 * <p>
 * Направление теплоносителя — от листьев (ОКС) к корню (тай-ину).
 * Инвариант шага 4: условный диаметр не убывает при движении от ОКС
 * к тай-ину. Узлы ветвления — точки, где сеть разделяется на несколько
 * направлений; каждая такая точка должна быть тепловой камерой
 * (ТП, п. 2.3: «Разветвления выполняются только в тепловых камерах»).
 */
public final class RouteTree {

    /** Корень дерева — общая точка врезки. */
    private final TreeNode root;

    /** Все узлы дерева, включая корень и листья. */
    private final List<TreeNode> nodes;

    /** Все рёбра дерева. */
    private final List<TreeEdge> edges;

    /** Листья — точки подключения ОКС группы. */
    private final List<TreeNode> leaves;

    /** Узлы ветвления (более одного дочернего ребра), исключая корень. */
    private final List<TreeNode> branchingNodes;

    public RouteTree(TreeNode root, List<TreeNode> nodes,
                     List<TreeEdge> edges, List<TreeNode> leaves,
                     List<TreeNode> branchingNodes) {
        this.root = Objects.requireNonNull(root, "root");
        this.nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        this.edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
        this.leaves = List.copyOf(Objects.requireNonNull(leaves, "leaves"));
        this.branchingNodes = List.copyOf(
                Objects.requireNonNull(branchingNodes, "branchingNodes"));
    }

    public TreeNode getRoot() {
        return root;
    }

    public List<TreeNode> getNodes() {
        return nodes;
    }

    public List<TreeEdge> getEdges() {
        return edges;
    }

    public List<TreeNode> getLeaves() {
        return leaves;
    }

    public List<TreeNode> getBranchingNodes() {
        return branchingNodes;
    }

    /** @return число ОКС в группе (= число листьев) */
    public int getOksCount() {
        return leaves.size();
    }

    /** @return суммарная длина всех рёбер дерева, м */
    public double getTotalLengthM() {
        double sum = 0.0;
        for (TreeEdge edge : edges) {
            sum += edge.getLengthM();
        }
        return sum;
    }

    @Override
    public String toString() {
        return "RouteTree{oks=" + leaves.size()
                + ", nodes=" + nodes.size()
                + ", edges=" + edges.size()
                + ", branches=" + branchingNodes.size() + '}';
    }
}
