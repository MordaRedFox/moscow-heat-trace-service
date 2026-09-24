package ru.moscow.heat.spatial;

import org.springframework.stereotype.Component;

/**
 * Нормативный справочник стоимости новой тепловой камеры,
 * соответствующий разделу 3.2 Технического приложения ЛЦТ 2026.
 * <p>Стоимость определяется по наибольшему условному диаметру всех
 * примыкающих к камере участков тепловой сети. Для новой камеры
 * стоимость включает присоединение к существующей сети, отдельная
 * стоимость врезки не добавляется
 */
@Component
public class ChamberCostTable {

    /** Нижняя граница первого диапазона таблицы 3.2. */
    private static final int MIN_DIAMETER_MM = 50;

    /** Верхняя граница последнего диапазона таблицы 3.2. */
    private static final int MAX_DIAMETER_MM = 1400;

    /**
     * Возвращает нормативную стоимость строительства новой тепловой
     * камеры по наибольшему примыкающему ДУ
     * @param diameterMm наибольший условный диаметр примыкающих
     *                   участков, мм
     * @return стоимость строительства, руб.
     * @throws IllegalArgumentException если диаметр выходит за
     *                                  диапазон таблицы 3.2
     */
    public long costForDiameter(int diameterMm) {
        if (diameterMm < MIN_DIAMETER_MM
                || diameterMm > MAX_DIAMETER_MM) {
            throw new IllegalArgumentException(
                    "Условный диаметр " + diameterMm
                            + " мм вне диапазона таблицы 3.2 ("
                            + MIN_DIAMETER_MM + ".."
                            + MAX_DIAMETER_MM + " мм)");
        }
        if (diameterMm <= 200) {
            return 3_000_000L;
        }
        if (diameterMm <= 500) {
            return 5_000_000L;
        }
        if (diameterMm <= 1000) {
            return 8_000_000L;
        }
        return 12_000_000L;
    }
}
