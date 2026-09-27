package ru.moscow.heat.trace.model;

import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Узел дерева маршрутов группы ОКС (итерация 6, шаг 2).
 * <p>
 * Дерево строится от общего тай-ина (корень) к точкам ОКС группы (листья).
 * Узлы ветвления — потенциальные новые тепловые камеры.
 * <p>
 * Координата — метры в UTM zone 37N (EPSG:32637), как и у
 * {@link RouteNode}, {@link RouteSegment} и во всём графе видимости.
 * <p>
 * Не путать с {@link RouteNodeType} / {@link RouteNode}: это внутренняя
 * вычислительная сущность конвейера «групповая трассировка», используемая
 * шагами 2–5. В финальный {@code TraceResult} она не попадает напрямую,
 * а конвертируется в {@code RouteNode}/{@code RouteSegment}.
 * <p>
 * Структура мутабельна в фазе построения/агрегации (шаги 2–4) и должна
 * считаться зафиксированной после сборки {@link RouteTree}.
 */
public final class TreeNode {

    /** Тип узла внутри дерева маршрутов группы. */
    public enum TreeNodeType {
        /** Корень дерева — общая точка врезки (тай-ин). */
        ROOT,
        /** Лист — точка подключения ОКС. */
        LEAF,
        /** Узел ветвления (более одного дочернего ребра) — будущая камера. */
        BRANCH,
        /** Промежуточный узел (одно дочернее ребро) — поворот/техточка. */
        INTERMEDIATE
    }

    private final UUID id;
    private final Coordinate coordinateUtm;
    private TreeNodeType type;

    /**
     * feature_id ОКС для листьев, {@code null} для остальных узлов.
     * Аналогично {@code RouteNode.sourceFeatureId}.
     */
    private final String oksFeatureId;

    /** Рёбра к дочерним узлам (от корня к листьям). Пусто для листьев. */
    private final List<TreeEdge> childEdges = new ArrayList<>();

    /** Ребро к родительскому узлу. {@code null} для корня. */
    private TreeEdge parentEdge;

    public TreeNode(UUID id, Coordinate coordinateUtm, TreeNodeType type,
                    String oksFeatureId) {
        this.id = Objects.requireNonNull(id, "id");
        this.coordinateUtm = Objects.requireNonNull(coordinateUtm, "coordinateUtm");
        this.type = Objects.requireNonNull(type, "type");
        this.oksFeatureId = oksFeatureId;
    }

    public UUID getId() {
        return id;
    }

    /** @return координата в метрах EPSG:32637 */
    public Coordinate getCoordinateUtm() {
        return coordinateUtm;
    }

    public TreeNodeType getType() {
        return type;
    }

    /**
     * Уточняет тип узла после построения (например,
     * {@link TreeNodeType#INTERMEDIATE} → {@link TreeNodeType#BRANCH},
     * когда выяснилось, что у узла несколько детей).
     * @param type новый тип
     */
    public void setType(TreeNodeType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    /** @return feature_id ОКС для листьев, иначе {@code null} */
    public String getOksFeatureId() {
        return oksFeatureId;
    }

    /** @return неизменяемое представление рёбер к дочерним узлам */
    public List<TreeEdge> getChildEdges() {
        return Collections.unmodifiableList(childEdges);
    }

    /** @return ребро к родителю, либо {@code null} для корня */
    public TreeEdge getParentEdge() {
        return parentEdge;
    }

    /**
     * Добавляет ребро к дочернему узлу. Только фаза построения дерева.
     * @param edge ребро, у которого {@code from == this}
     */
    public void addChildEdge(TreeEdge edge) {
        childEdges.add(Objects.requireNonNull(edge, "edge"));
    }

    /**
     * Устанавливает ребро к родителю. Только фаза построения дерева.
     * @param edge ребро, у которого {@code to == this}
     */
    public void setParentEdge(TreeEdge edge) {
        this.parentEdge = edge;
    }

    /** @return число дочерних рёбер */
    public int getChildCount() {
        return childEdges.size();
    }

    /** @return {@code true}, если у узла больше одного дочернего ребра */
    public boolean isBranching() {
        return childEdges.size() > 1;
    }

    public boolean isLeaf() {
        return type == TreeNodeType.LEAF;
    }

    public boolean isRoot() {
        return type == TreeNodeType.ROOT;
    }

    @Override
    public String toString() {
        return "TreeNode{id=" + id
                + ", type=" + type
                + ", oks=" + oksFeatureId
                + ", children=" + childEdges.size()
                + ", utm=" + coordinateUtm + '}';
    }
}