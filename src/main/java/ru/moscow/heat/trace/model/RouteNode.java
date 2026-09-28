package ru.moscow.heat.trace.model;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.util.Objects;
import java.util.UUID;

/**
 * Узел маршрута новой тепловой сети.
 */
@ToString
@EqualsAndHashCode
public final class RouteNode {

    private static final GeometryFactory GF = new GeometryFactory();

    private final UUID id;
    private final RouteNodeType type;
    private final Coordinate coordinateUtm;
    private final String sourceFeatureId;

    public RouteNode(UUID id, RouteNodeType type, Coordinate coordinateUtm, String sourceFeatureId) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.coordinateUtm = Objects.requireNonNull(coordinateUtm, "coordinateUtm");
        this.sourceFeatureId = sourceFeatureId;
    }

    public RouteNode(String id, String sourceFeatureId, Point point, String nodeType) {
        UUID parsedId;
        try {
            parsedId = id != null ? UUID.fromString(id) : UUID.randomUUID();
        } catch (Exception e) {
            parsedId = id != null ? UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)) : UUID.randomUUID();
        }
        this.id = parsedId;
        this.sourceFeatureId = sourceFeatureId != null ? sourceFeatureId : id;
        this.coordinateUtm = point != null ? point.getCoordinate() : new Coordinate(0, 0);
        this.type = parseRouteNodeType(nodeType);
    }

    private static RouteNodeType parseRouteNodeType(String nodeType) {
        if (nodeType == null) {
            return RouteNodeType.TECHNICAL_NODE;
        }
        String upper = nodeType.toUpperCase();
        if (upper.contains("OKS")) return RouteNodeType.OKS_CONNECTION_POINT;
        if (upper.contains("EXISTING") || upper.contains("CHAMBER")) return RouteNodeType.EXISTING_CHAMBER;
        if (upper.contains("NEW")) return RouteNodeType.NEW_CHAMBER;
        if (upper.contains("CORNER")) return RouteNodeType.CORNER;
        return RouteNodeType.TECHNICAL_NODE;
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

    public String getSourceFeatureId() {
        return sourceFeatureId;
    }

    public Point getPoint() {
        return coordinateUtm != null ? GF.createPoint(coordinateUtm) : null;
    }

    public String getNodeType() {
        return type != null ? type.name().toLowerCase() : "technical_node";
    }
}
