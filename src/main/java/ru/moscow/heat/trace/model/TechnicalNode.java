package ru.moscow.heat.trace.model;

import org.locationtech.jts.geom.Coordinate;

import java.util.Objects;
import java.util.UUID;

/**
 * Технический узел — служебная точка на маршруте, не являющаяся тепловой
 * камерой (ТЗ, п.2.3). Появляется на границах разбиения сегмента
 * (см. RouteSegmentSplitter, шаг 7 плана).
 */
public final class TechnicalNode {

    /** Причина появления технического узла. */
    public enum Reason {
        /** Смена условного диаметра. */
        DIAMETER_CHANGE,
        /** Смена способа прокладки (BASE ↔ SPECIAL). */
        METHOD_CHANGE,
        /** Граница спецзоны (вход/выход из буфера пространственного ограничения). */
        ZONE_BOUNDARY
    }

    private final UUID id;
    private final Coordinate coordinateUtm;
    private final Reason reason;

    public TechnicalNode(UUID id, Coordinate coordinateUtm, Reason reason) {
        this.id = Objects.requireNonNull(id, "id");
        this.coordinateUtm = Objects.requireNonNull(coordinateUtm, "coordinateUtm");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public UUID getId() {
        return id;
    }

    public Coordinate getCoordinateUtm() {
        return coordinateUtm;
    }

    public Reason getReason() {
        return reason;
    }
}
