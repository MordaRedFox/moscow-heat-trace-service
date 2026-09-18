package ru.moscow.heat.geojson;

import lombok.Getter;

import java.util.Set;

@Getter
public enum ObjectType {
    SOURCE(
            Set.of(),
            Set.of("Point")),
    HEAT_NETWORK(
            Set.of("diameter", "flow_tph", "upstream_object_id"),
            Set.of("LineString")),
    HEAT_CHAMBER(
            Set.of("diameter", "upstream_object_id"),
            Set.of("Point")),
    OKS_FUTURE(
            Set.of("flow_tph", "heat_load"),
            Set.of("Polygon", "MultiPolygon")),
    OKS_CONNECTION_POINT(
            Set.of("oks_id"),
            Set.of("Point")),
    OKS_EXISTING(
            Set.of(),
            Set.of("Polygon", "MultiPolygon")),
    RESTRICTION(
            Set.of("restriction_type"),
            Set.of("Point", "LineString", "Polygon", "MultiPolygon"));

    private final Set<String> requiredProperties;
    private final Set<String> allowedGeometryTypes;

    ObjectType(Set<String> requiredProperties, Set<String> allowedGeometryTypes) {
        this.requiredProperties = requiredProperties;
        this.allowedGeometryTypes = allowedGeometryTypes;
    }

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
