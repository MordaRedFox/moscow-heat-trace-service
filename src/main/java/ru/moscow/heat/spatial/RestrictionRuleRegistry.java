package ru.moscow.heat.spatial;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Нормативный реестр правил пространственных ограничений.
 * Загружает и предоставляет данные Таблицы 2 Технического приложения ЛЦТ-2026.
 */
@Component
public class RestrictionRuleRegistry {

    private final Map<String, RestrictionRule> rules;

    public RestrictionRuleRegistry() {
        Map<String, RestrictionRule> map = new LinkedHashMap<>();

        // Запрещенные к пересечению объекты (FORBIDDEN)
        map.put("oks", new RestrictionRule(
                "oks", RestrictionRuleType.FORBIDDEN, 5.0,
                null, null, false, null, null, null, 0.0));

        map.put("park", new RestrictionRule(
                "park", RestrictionRuleType.FORBIDDEN, 1.0,
                null, null, false, null, null, null, 0.0));

        map.put("social_area", new RestrictionRule(
                "social_area", RestrictionRuleType.FORBIDDEN, 1.0,
                null, null, false, null, null, null, 0.0));

        map.put("prohibited_site", new RestrictionRule(
                "prohibited_site", RestrictionRuleType.FORBIDDEN, 1.0,
                null, null, false, null, null, null, 0.0));

        map.put("water", new RestrictionRule(
                "water", RestrictionRuleType.FORBIDDEN, 1.0,
                null, null, false, null, null, null, 0.0));

        map.put("railway", new RestrictionRule(
                "railway", RestrictionRuleType.FORBIDDEN, 1.0,
                null, null, false, null, null, null, 0.0));

        // Объекты со специальным проходом (SPECIAL_CROSSING)
        map.put("road", new RestrictionRule(
                "road", RestrictionRuleType.SPECIAL_CROSSING, 1.5,
                45.0, 1.60, false, null, null, null, 3.0));

        map.put("tram_tracks", new RestrictionRule(
                "tram_tracks", RestrictionRuleType.SPECIAL_CROSSING, 1.5,
                45.0, 1.75, false, null, null, null, 3.0));

        map.put("gas_pipeline", new RestrictionRule(
                "gas_pipeline", RestrictionRuleType.SPECIAL_CROSSING, 2.0,
                null, 1.25, true, 0.40, 0.40, 2.8, 2.0));

        map.put("power_cable", new RestrictionRule(
                "power_cable", RestrictionRuleType.SPECIAL_CROSSING, 2.0,
                null, 1.15, true, 0.20, 0.20, 2.7, 2.0));

        map.put("heat_network", new RestrictionRule(
                "heat_network", RestrictionRuleType.SPECIAL_CROSSING, 1.0,
                null, 1.05, true, null, null, 3.0, 2.0));

        this.rules = Collections.unmodifiableMap(map);
    }

    /**
     * Возвращает нормативное правило для заданного типа ограничения.
     *
     * @param restrictionType строковый тип (road, water, park и т.п.)
     * @return Optional с правилом, либо empty, если тип неизвестен (допустимо по ТЗ)
     */
    public Optional<RestrictionRule> ruleFor(String restrictionType) {
        if (restrictionType == null) {
            return Optional.empty();
        }
        String normalized = restrictionType.trim().toLowerCase();
        return Optional.ofNullable(rules.get(normalized));
    }

    /**
     * Возвращает минимальное допустимое горизонтальное расстояние до полигона ОКС
     * в зависимости от условного диаметра (ДУ) прокладываемой трубы:
     * - ДУ < 500 мм  -> 5 м;
     * - ДУ 500..800 мм -> 7 м;
     * - ДУ >= 900 мм -> 9 м.
     *
     * @param diameterMm условный диаметр, мм
     * @return нормативный отступ, м (целое число 5, 7 или 9)
     * @throws IllegalArgumentException если диаметр <= 0
     */
    public int minDistanceForDiameter(int diameterMm) {
        if (diameterMm <= 0) {
            throw new IllegalArgumentException("Условный диаметр должен быть положительным: " + diameterMm);
        }
        if (diameterMm < 500) {
            return 5;
        } else if (diameterMm <= 800) {
            return 7;
        } else {
            return 9;
        }
    }

    /**
     * Возвращает все зарегистрированные правила.
     */
    public Collection<RestrictionRule> getAllRules() {
        return rules.values();
    }
}
