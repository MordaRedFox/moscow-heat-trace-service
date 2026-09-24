package ru.moscow.heat.trace.model;

import org.locationtech.jts.geom.LineString;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Отрезок маршрута новой тепловой сети между двумя узлами.
 * <p>
 * Итерация 5: заполняются геометрия, расход, ДУ, способ прокладки, Kспец
 * и длина. Поле {@code cost} — заготовка под итерацию 7 (расчёт стоимости),
 * в итерации 5 не используется / остаётся {@code null}.
 */
public final class RouteSegment {

    private final UUID id;
    private final RouteNode fromNode;
    private final RouteNode toNode;

    /** Геометрия участка в UTM (EPSG:32637). */
    private final LineString geometryUtm;

    /** Суммарный расход подающего трубопровода на участке, т/ч. */
    private final BigDecimal flowTph;

    /** Условный диаметр, мм. Назначается DiameterAssigner (шаг 8 плана). */
    private final int diameterMm;

    private final LayingMethod layingMethod;

    /**
     * Коэффициент Kспец для участка. 1.0 для BASE-участков;
     * &gt;1.0 внутри спецзоны (берётся максимум при наложении зон).
     */
    private final double kspets;

    /** Длина участка, м (в проекции UTM). */
    private final double lengthM;

    /** Заготовка под итерацию 7. Не заполняется в итерации 5. */
    private final BigDecimal cost;

    public RouteSegment(UUID id, RouteNode fromNode, RouteNode toNode, LineString geometryUtm,
                         BigDecimal flowTph, int diameterMm, LayingMethod layingMethod,
                         double kspets, double lengthM, BigDecimal cost) {
        this.id = Objects.requireNonNull(id, "id");
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

    public UUID getId() {
        return id;
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

    public BigDecimal getFlowTph() {
        return flowTph;
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
