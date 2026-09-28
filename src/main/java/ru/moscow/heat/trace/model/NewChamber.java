package ru.moscow.heat.trace.model;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Новая тепловая камера — создаётся в точке врезки или в точке разветвления сети на несколько ОКС.
 */
@ToString
@EqualsAndHashCode
public final class NewChamber {

    private static final GeometryFactory GF = new GeometryFactory();

    private final String id;
    private final UUID uuid;
    private final Coordinate coordinateUtm;
    private final Point geometry;
    private final int diameterMm;
    private final BigDecimal cost;

    public NewChamber(UUID id, Coordinate coordinateUtm, int diameterMm, BigDecimal cost) {
        this.uuid = Objects.requireNonNull(id, "id");
        this.id = id.toString();
        this.coordinateUtm = Objects.requireNonNull(coordinateUtm, "coordinateUtm");
        this.geometry = GF.createPoint(coordinateUtm);
        this.diameterMm = diameterMm;
        this.cost = cost;
    }

    public NewChamber(String id, Point geometry, int diameterMm, BigDecimal cost) {
        this.id = Objects.requireNonNull(id, "id");
        UUID parsed;
        try {
            parsed = UUID.fromString(id);
        } catch (Exception e) {
            parsed = UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        this.uuid = parsed;
        this.geometry = geometry;
        this.coordinateUtm = geometry != null ? geometry.getCoordinate() : new Coordinate(0, 0);
        this.diameterMm = diameterMm;
        this.cost = cost;
    }

    public String getId() {
        return id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Coordinate getCoordinateUtm() {
        return coordinateUtm;
    }

    public Point getGeometry() {
        return geometry;
    }

    public int getDiameterMm() {
        return diameterMm;
    }

    public BigDecimal getCost() {
        return cost;
    }
}
