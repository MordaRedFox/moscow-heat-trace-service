package ru.moscow.heat.spatial;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Неизменяемое нормативное правило для типа пространственного ограничения.
 * Соответствует строке Таблицы 2 Технического приложения ЛЦТ-2026.
 */
@Getter
@ToString
@EqualsAndHashCode
public final class RestrictionRule {

    /** Строковый тип ограничения (road, water, oks, gas_pipeline и т.д.) */
    private final String restrictionType;

    /** Режим: запрет пересечения или специальный проход */
    private final RestrictionRuleType ruleType;

    /** Минимальное допустимое горизонтальное расстояние при сближении, м */
    private final double minHorizontalDistanceM;

    /** Минимальный угол пересечения в градусах (для дорог/трамвайных путей >= 45.0), или null */
    private final Double minCrossingAngleDeg;

    /** Повышающий коэффициент стоимости участка специального прохода (Kспец), или null */
    private final Double kspets;

    /** Флаг наличия собственного нормативного габарита у коммуникации */
    private final boolean hasOwnEnvelope;

    /** Расчетная ширина собственного габарита коммуникации, м (или null) */
    private final Double envelopeWidthM;

    /** Расчетная строительная высота собственного габарита коммуникации, м (или null) */
    private final Double envelopeHeightM;

    /** Нормативная глубина залегания до верха габарита, м (или null) */
    private final Double envelopeTopDepthM;

    /**
     * Запас длины спецучастка, м:
     * - для road / tram_tracks: 3.0 м за внешней границей полигона с каждой стороны;
     * - для gas_pipeline / power_cable / heat_network: 2.0 м с каждой стороны от точки пересечения;
     * - для запретных зон: 0.0 м.
     */
    private final double specialZonePaddingM;

    public RestrictionRule(String restrictionType,
                           RestrictionRuleType ruleType,
                           double minHorizontalDistanceM,
                           Double minCrossingAngleDeg,
                           Double kspets,
                           boolean hasOwnEnvelope,
                           Double envelopeWidthM,
                           Double envelopeHeightM,
                           Double envelopeTopDepthM,
                           double specialZonePaddingM) {
        this.restrictionType = restrictionType;
        this.ruleType = ruleType;
        this.minHorizontalDistanceM = minHorizontalDistanceM;
        this.minCrossingAngleDeg = minCrossingAngleDeg;
        this.kspets = kspets;
        this.hasOwnEnvelope = hasOwnEnvelope;
        this.envelopeWidthM = envelopeWidthM;
        this.envelopeHeightM = envelopeHeightM;
        this.envelopeTopDepthM = envelopeTopDepthM;
        this.specialZonePaddingM = specialZonePaddingM;
    }
}
