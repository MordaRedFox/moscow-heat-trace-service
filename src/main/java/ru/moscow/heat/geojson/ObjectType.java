package ru.moscow.heat.geojson;

import lombok.Getter;

import java.util.Set;

/**
 * Типы объектов входного GeoJSON. Для каждого типа задан набор
 * обязательных атрибутов и допустимые типы геометрии. Для {@code restriction}
 * допустимы только линейные ({@code LineString}, {@code MultiLineString}) и
 * полигональные ({@code Polygon}, {@code MultiPolygon}) типы - точечные
 * ограничения в модели не предусмотрены
 */
@Getter
public enum ObjectType {
    SOURCE(
            Set.of(),
            Set.of("Point")),
    HEAT_NETWORK(
            Set.of("diameter"),
            Set.of("LineString")),
    HEAT_CHAMBER(
            Set.of(),
            Set.of("Point")),
    OKS_CONNECTION_POINT(
            Set.of("flow_tph"),
            Set.of("Point")),
    RESTRICTION(
            Set.of("restriction_type"),
            Set.of("LineString", "MultiLineString",
                    "Polygon", "MultiPolygon"));

    private final Set<String> requiredProperties;
    private final Set<String> allowedGeometryTypes;

    ObjectType(Set<String> requiredProperties,
               Set<String> allowedGeometryTypes) {
        this.requiredProperties = requiredProperties;
        this.allowedGeometryTypes = allowedGeometryTypes;
    }

    /**
     * Преобразует строковое значение {@code object_type} в
     * константу enum без учета регистра
     * @param s значение из GeoJSON (например, {@code heat_network})
     * @return соответствующая константа или {@code null}, если
     *         тип неизвестен
     */
    public static ObjectType fromString(String s) {
        if (s == null) {
            return null;
        }
        String normalized = s.trim().toUpperCase();
        for (ObjectType t : values()) {
            if (t.name().equals(normalized)) {
                return t;
            }
        }
        return null;
    }
}
