package ru.moscow.heat.spatial;

/**
 * Тип правила для пространственного ограничения согласно разделу 4 ТП
 */
public enum RestrictionRuleType {
    /** Пересечение полностью запрещено */
    FORBIDDEN,

    /** Разрешен специальный проход с соблюдением нормативных условий */
    SPECIAL_CROSSING
}
