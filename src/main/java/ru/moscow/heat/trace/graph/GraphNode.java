package ru.moscow.heat.trace.graph;

import java.util.Objects;

/**
 * Внутренний узел графа видимости (visibility graph).
 * <p>
 * Координаты {@code x/y} — метры в UTM zone 37N (EPSG:32637), как и все
 * геометрии в {@link ObstacleModel}. Никакой трансформации координат
 * внутри {@link VisibilityGraph} не требуется — расстояния считаются
 * обычной евклидовой метрикой в этой же плоскости.
 * <p>
 * Не путать с {@link ru.moscow.heat.trace.model.RouteNode} — это чисто
 * вычислительная сущность, используемая только внутри {@link VisibilityGraph}.
 * После нахождения пути координаты конвертируются в {@code RouteNode}
 * с нужным {@code RouteNodeType}.
 */
public final class GraphNode {

    /** Роль узла графа — влияет только на то, как он был получен, не на алгоритм поиска. */
    public enum Kind {
        FORBIDDEN_CORNER,
        SPECIAL_CORNER,
        /** Зарезервировано: конец существующего участка сети (не используется в MVP). */
        EXISTING_NETWORK_ENDPOINT
    }

    private final long id;
    private final double x;
    private final double y;
    private final Kind kind;

    /** Ссылка на исходный объект (пока не заполняется в MVP), может быть {@code null}. */
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

    /** X, метры UTM zone 37N. */
    public double getX() {
        return x;
    }

    /** Y, метры UTM zone 37N. */
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
