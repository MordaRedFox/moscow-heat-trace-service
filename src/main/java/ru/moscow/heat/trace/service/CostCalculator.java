package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.moscow.heat.spatial.DiameterSpec;
import ru.moscow.heat.spatial.DiameterTable;
import ru.moscow.heat.trace.model.RouteSegment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/**
 * Калькулятор стоимости строительства участков тепловой сети (сегментов).
 * Формула расчета: Cуч = L * cнов(ДУ) * Kгл * Kспец.
 * Где:
 *  L = длина участка в метрах (getLengthM())
 *  cнов(ДУ) = стоимость нового строительства за погонный метр из DiameterTable
 *  Kгл = коэффициент глубины (в базовом 2D-режиме всегда = 1.0)
 *  Kспец = повышающий коэффициент специальных проходов (getKspets())
 */
@Service
@RequiredArgsConstructor
public class CostCalculator {

    public static final double BASE_KGL = 1.0;

    private final DiameterTable diameterTable;

    /**
     * Рассчитывает стоимость строительства одного сегмента сети: Cуч = L * cнов(ДУ) * Kгл * Kспец.
     *
     * @param segment сегмент сети
     * @return расчетная стоимость сегмента в рублях
     */
    public BigDecimal calculateCost(RouteSegment segment) {
        Objects.requireNonNull(segment, "Сегмент сети не может быть null");
        DiameterSpec spec = diameterTable.findByDiameter(segment.getDiameterMm())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Не найден условный диаметр " + segment.getDiameterMm() + " мм в таблице диаметров"));

        double lengthM = segment.getLengthM();
        double costPerMeter = spec.getCostPerMeter();
        double kgl = BASE_KGL;
        double kspets = segment.getKspets() > 0 ? segment.getKspets() : 1.0;

        BigDecimal lBd = BigDecimal.valueOf(lengthM);
        BigDecimal cNovBd = BigDecimal.valueOf(costPerMeter);
        BigDecimal kGlBd = BigDecimal.valueOf(kgl);
        BigDecimal kSpetsBd = BigDecimal.valueOf(kspets);

        return lBd.multiply(cNovBd)
                .multiply(kGlBd)
                .multiply(kSpetsBd)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Возвращает копию сегмента с рассчитанной и проставленной стоимостью.
     *
     * @param segment исходный сегмент
     * @return новый сегмент с заполненным полем cost
     */
    public RouteSegment applyCost(RouteSegment segment) {
        return segment.withCost(calculateCost(segment));
    }

    /**
     * Рассчитывает суммарную стоимость набора сегментов сети.
     *
     * @param segments коллекция сегментов сети
     * @return суммарная стоимость в рублях
     */
    public BigDecimal calculateTotalCost(Collection<RouteSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (RouteSegment segment : segments) {
            BigDecimal cost = segment.getCost() != null ? segment.getCost() : calculateCost(segment);
            total = total.add(cost);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
