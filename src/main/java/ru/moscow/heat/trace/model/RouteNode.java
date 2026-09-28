package ru.moscow.heat.trace.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.locationtech.jts.geom.Point;

/**
 * Неизменяемый узел графа трассировки тепловой сети.
 */
@Getter
@ToString
@EqualsAndHashCode
public final class RouteNode {

    private final String id;
    private final String sourceFeatureId;
    private final Point point;
    private final String nodeType;

    public RouteNode(String id, String sourceFeatureId, Point point, String nodeType) {
        this.id = id;
        this.sourceFeatureId = sourceFeatureId;
        this.point = point;
        this.nodeType = nodeType;
    }
}
