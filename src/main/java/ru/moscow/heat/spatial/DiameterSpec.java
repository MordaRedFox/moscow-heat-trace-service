package ru.moscow.heat.spatial;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Неизменяемая спецификация условного диаметра теплосети (ДУ).
 * Соответствует строке Таблицы 1 Технического приложения ЛЦТ-2026.
 * Java 11: класс неизменяемый (final поля, геттеры, equals/hashCode/toString)
 */
@Getter
@ToString
@EqualsAndHashCode
public final class DiameterSpec {

    /** Условный диаметр трубы, мм */
    private final int diameterMm;

    /** Предельная пропускная способность пары труб, т/ч */
    private final double capacityTph;

    /** Предельная длина непрерывного участка данного ДУ, м */
    private final double maxLengthM;

    /** Стоимость нового строительства, руб./м */
    private final double costPerMeter;

    /** Расчетная ширина пары труб (подающая + обратная), м */
    private final double pairWidthM;

    /** Расчетная строительная высота пары труб, м */
    private final double pairHeightM;

    public DiameterSpec(int diameterMm,
                        double capacityTph,
                        double maxLengthM,
                        double costPerMeter,
                        double pairWidthM,
                        double pairHeightM) {
        this.diameterMm = diameterMm;
        this.capacityTph = capacityTph;
        this.maxLengthM = maxLengthM;
        this.costPerMeter = costPerMeter;
        this.pairWidthM = pairWidthM;
        this.pairHeightM = pairHeightM;
    }
}
