package ru.moscow.heat.trace.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;

/**
 * Неизменяемая модель новой проектируемой тепловой камеры.
 */
@Getter
@ToString
@EqualsAndHashCode
public final class NewChamber {

    private final String id;
    private final Point geometry;
    private final int diameterMm;
    private final BigDecimal cost;

    public NewChamber(String id, Point geometry, int diameterMm, BigDecimal cost) {
        this.id = id;
        this.geometry = geometry;
        this.diameterMm = diameterMm;
        this.cost = cost;
    }
}
