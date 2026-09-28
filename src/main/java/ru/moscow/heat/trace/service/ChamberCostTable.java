package ru.moscow.heat.trace.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Нормативный справочник стоимости строительства новых тепловых камер по условному диаметру (ДУ).
 * Соответствует разделу 3.2 Технического приложения:
 * <ul>
 *   <li>50..200 мм &rarr; 3 000 000 руб.</li>
 *   <li>250..500 мм &rarr; 5 000 000 руб.</li>
 *   <li>600..1000 мм &rarr; 8 000 000 руб.</li>
 *   <li>1200..1400 мм &rarr; 12 000 000 руб.</li>
 * </ul>
 */
@Component
public class ChamberCostTable {

    public static final BigDecimal COST_50_200 = BigDecimal.valueOf(3_000_000L).setScale(2);
    public static final BigDecimal COST_250_500 = BigDecimal.valueOf(5_000_000L).setScale(2);
    public static final BigDecimal COST_600_1000 = BigDecimal.valueOf(8_000_000L).setScale(2);
    public static final BigDecimal COST_1200_1400 = BigDecimal.valueOf(12_000_000L).setScale(2);

    /**
     * Возвращает нормативную стоимость строительства новой камеры в рублях.
     *
     * @param diameterMm условный диаметр в мм
     * @return нормативная стоимость
     * @throws IllegalArgumentException если диаметр вне допустимого диапазона (50..1400 мм)
     */
    public BigDecimal getCost(int diameterMm) {
        if (diameterMm >= 50 && diameterMm <= 200) {
            return COST_50_200;
        } else if (diameterMm >= 250 && diameterMm <= 500) {
            return COST_250_500;
        } else if (diameterMm >= 600 && diameterMm <= 1000) {
            return COST_600_1000;
        } else if (diameterMm >= 1200 && diameterMm <= 1400) {
            return COST_1200_1400;
        } else {
            throw new IllegalArgumentException(
                    "Недопустимый условный диаметр камеры: " + diameterMm
                            + " мм. Допустимый нормативный диапазон: 50..1400 мм");
        }
    }
}
