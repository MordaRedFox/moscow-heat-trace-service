package ru.moscow.heat.trace.model;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.locationtech.jts.geom.LineString;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Отрезок маршрута новой тепловой сети между двумя узлами.
 */
@ToString
@EqualsAndHashCode
public final class RouteSegment {

    private final UUID id;
    private final String stringId;
    private final RouteNode fromNode;
    private final RouteNode toNode;
    private final LineString geometryUtm;
    private final BigDecimal flowTph;
    private final int diameterMm;
    private final LayingMethod layingMethod;
    private final double kspets;
    private final double lengthM;
    private final BigDecimal cost;

    public RouteSegment(UUID id, RouteNode fromNode, RouteNode toNode, LineString geometryUtm,
                        BigDecimal flowTph, int diameterMm, LayingMethod layingMethod,
                        double kspets, double lengthM, BigDecimal cost) {
        this.id = Objects.requireNonNull(id, "id");
        this.stringId = id.toString();
        this.fromNode = Objects.requireNonNull(fromNode, "fromNode");
        this.toNode = Objects.requireNonNull(toNode, "toNode");
        this.geometryUtm = Objects.requireNonNull(geometryUtm, "geometryUtm");
        this.flowTph = Objects.requireNonNull(flowTph, "flowTph");
        this.diameterMm = diameterMm;
        this.layingMethod = Objects.requireNonNull(layingMethod, "layingMethod");
        this.kspets = kspets;
        this.lengthM = lengthM;
        this.cost = cost;
    }

    public RouteSegment(String id, RouteNode fromNode, RouteNode toNode, LineString geometry,
                        double lengthM, int diameterMm, double flowTph, double kspets, double kgl,
                        BigDecimal cost) {
        UUID parsed;
        try {
            parsed = id != null ? UUID.fromString(id) : UUID.randomUUID();
        } catch (Exception e) {
            parsed = id != null ? UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8)) : UUID.randomUUID();
        }
        this.id = parsed;
        this.stringId = id;
        this.fromNode = Objects.requireNonNull(fromNode, "fromNode");
        this.toNode = Objects.requireNonNull(toNode, "toNode");
        this.geometryUtm = Objects.requireNonNull(geometry, "geometry");
        this.flowTph = BigDecimal.valueOf(flowTph);
        this.diameterMm = diameterMm;
        this.layingMethod = kspets > 1.0 ? LayingMethod.SPECIAL : LayingMethod.BASE;
        this.kspets = kspets > 0 ? kspets : 1.0;
        this.lengthM = lengthM;
        this.cost = cost;
    }

    public RouteSegment withCost(BigDecimal newCost) {
        return new RouteSegment(
                this.id,
                this.fromNode,
                this.toNode,
                this.geometryUtm,
                this.flowTph,
                this.diameterMm,
                this.layingMethod,
                this.kspets,
                this.lengthM,
                newCost
        );
    }

    public UUID getId() {
        return id;
    }

    public String getStringId() {
        return stringId;
    }

    public RouteNode getFromNode() {
        return fromNode;
    }

    public RouteNode getToNode() {
        return toNode;
    }

    public LineString getGeometryUtm() {
        return geometryUtm;
    }

    public LineString getGeometry() {
        return geometryUtm;
    }

    public BigDecimal getFlowTph() {
        return flowTph;
    }

    public double getFlowTphDouble() {
        return flowTph != null ? flowTph.doubleValue() : 0.0;
    }

    public int getDiameterMm() {
        return diameterMm;
    }

    public LayingMethod getLayingMethod() {
        return layingMethod;
    }

    public double getKspets() {
        return kspets;
    }

    public double getLengthM() {
        return lengthM;
    }

    public BigDecimal getCost() {
        return cost;
    }
}
