package ru.moscow.heat.trace.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.locationtech.jts.geom.LineString;

import java.math.BigDecimal;

/**
 * Неизменяемый участок трассируемой тепловой сети (сегмент).
 * Хранит геометрию, диаметр, расчетные коэффициенты и расчетную стоимость.
 */
@Getter
@ToString
@EqualsAndHashCode
public final class RouteSegment {

    private final String id;
    private final RouteNode fromNode;
    private final RouteNode toNode;
    private final LineString geometry;
    private final double lengthM;
    private final int diameterMm;
    private final double flowTph;
    private final double kspets;
    private final double kgl;
    private final BigDecimal cost;

    public RouteSegment(String id,
                        RouteNode fromNode,
                        RouteNode toNode,
                        LineString geometry,
                        double lengthM,
                        int diameterMm,
                        double flowTph,
                        double kspets,
                        double kgl,
                        BigDecimal cost) {
        this.id = id;
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.geometry = geometry;
        this.lengthM = lengthM;
        this.diameterMm = diameterMm;
        this.flowTph = flowTph;
        this.kspets = kspets > 0 ? kspets : 1.0;
        this.kgl = kgl > 0 ? kgl : 1.0;
        this.cost = cost;
    }

    /**
     * Фабричный метод создания копии сегмента с установленной стоимостью.
     *
     * @param newCost расчетная стоимость сегмента
     * @return новый неизменяемый экземпляр RouteSegment
     */
    public RouteSegment withCost(BigDecimal newCost) {
        return new RouteSegment(
                this.id,
                this.fromNode,
                this.toNode,
                this.geometry,
                this.lengthM,
                this.diameterMm,
                this.flowTph,
                this.kspets,
                this.kgl,
                newCost
        );
    }
}
