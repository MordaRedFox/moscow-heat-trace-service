package ru.moscow.heat.trace.graph;

import java.util.Objects;

/**
 * Внутренний узел графа видимости (visibility graph).
 * <p>
 * Не путать с {@link ru.moscow.heat.trace.model.RouteNode} — это чисто
 * вычислительная сущность, используемая только внутри {@link VisibilityGraph}
 * при построении и поиске A*. После нахождения пути узлы конвертируются
 * в {@code RouteNode} с нужным {@code RouteNodeType}.
 */
public final class GraphNode {

    /** Роль узла графа — влияет только на то, как он был получен, не на алгоритм поиска. */
    public enum Kind {
        START,
        END,
        FORBIDDEN_CORNER,
        SPECIAL_CORNER,
        EXISTING_NETWORK_ENDPOINT
    }

    private final long id;
    private final double x;
    private final double y;
    private final Kind kind;

    /**
     * Ссылка на исходный геометрический объект (id restriction / heat_network),
     * от которого получен угол. Может быть {@code null} для START/END.
     */
    private final Long sourceRestrictionId;

    public GraphNode(long id, double x, double y, Kind kind, Long sourceRestrictionId) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.sourceRestrictionId = sourceRestrictionId;
    }

    public long getId() {
        return id;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public Kind getKind() {
        return kind;
    }

    public Long getSourceRestrictionId() {
        return sourceRestrictionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GraphNode)) return false;
        GraphNode graphNode = (GraphNode) o;
        return id == graphNode.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
