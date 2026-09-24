package ru.moscow.heat.trace.model;

import org.locationtech.jts.geom.Coordinate;

import java.util.Objects;
import java.util.UUID;

/**
 * Узел маршрута новой тепловой сети.
 * <p>
 * Соответствует плану итерации 5 (trace/model.RouteNode): id, тип узла,
 * координата в проекции UTM (EPSG:32637).
 * <p>
 * Иммутабельный DTO — по аналогии с {@code TieInCandidate} из итерации 4.
 */
public final class RouteNode {

    private final UUID id;
    private final RouteNodeType type;
    private final Coordinate coordinateUtm;

    /**
     * Ссылка на исходный доменный объект, если узел ему соответствует
     * (id ОКС, id существующей камеры и т.п.). Может быть {@code null}
     * для чисто геометрических узлов (CORNER, TECHNICAL_NODE, NEW_CHAMBER
     * до сохранения в БД).
     */
    private final Long sourceEntityId;

    public RouteNode(UUID id, RouteNodeType type, Coordinate coordinateUtm, Long sourceEntityId) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.coordinateUtm = Objects.requireNonNull(coordinateUtm, "coordinateUtm");
        this.sourceEntityId = sourceEntityId;
    }

    public UUID getId() {
        return id;
    }

    public RouteNodeType getType() {
        return type;
    }

    public Coordinate getCoordinateUtm() {
        return coordinateUtm;
    }

    public Long getSourceEntityId() {
        return sourceEntityId;
    }

    @Override
    public String toString() {
        return "RouteNode{" +
                "id=" + id +
                ", type=" + type +
                ", coordinateUtm=" + coordinateUtm +
                '}';
    }
}
