package ru.moscow.heat.trace.model;

import org.locationtech.jts.geom.Coordinate;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Новая тепловая камера — создаётся в точке врезки (когда TieInCandidate имеет
 * тип NEW_CHAMBER) или в точке разветвления сети на несколько ОКС (этап 2,
 * после MVP — см. R4 плана итерации 5).
 * <p>
 * Диаметр — максимальный среди примыкающих участков (см. план, п.3).
 * Стоимость — заготовка под итерацию 7, использует {@code ChamberCostTable};
 * в итерации 5 не обязательна к заполнению.
 */
public final class NewChamber {

    private final UUID id;
    private final Coordinate coordinateUtm;

    /** Максимальный ДУ среди примыкающих участков, мм. */
    private final int diameterMm;

    /** Заготовка под итерацию 7. */
    private final BigDecimal cost;

    public NewChamber(UUID id, Coordinate coordinateUtm, int diameterMm, BigDecimal cost) {
        this.id = Objects.requireNonNull(id, "id");
        this.coordinateUtm = Objects.requireNonNull(coordinateUtm, "coordinateUtm");
        this.diameterMm = diameterMm;
        this.cost = cost;
    }

    public UUID getId() {
        return id;
    }

    public Coordinate getCoordinateUtm() {
        return coordinateUtm;
    }

    public int getDiameterMm() {
        return diameterMm;
    }

    public BigDecimal getCost() {
        return cost;
    }
}
