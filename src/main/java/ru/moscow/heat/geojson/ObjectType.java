package ru.moscow.heat.geojson;

import lombok.Getter;

import java.util.Set;

@Getter
public enum ObjectType {
    SOURCE(Set.of()),
    HEAT_NETWORK(Set.of("diameter", "flow_tph", "upstream_object_id")),
    HEAT_CHAMBER(Set.of("diameter", "upstream_object_id")),
    OKS_FUTURE(Set.of("flow_tph", "heat_load")),
    OKS_CONNECTION_POINT(Set.of("oks_id")),
    OKS_EXISTING(Set.of()),
    RESTRICTION(Set.of("restriction_type"));

    private final Set<String> requiredProperties;

    ObjectType(Set<String> requiredProperties) {
        this.requiredProperties = requiredProperties;
    }

    public static ObjectType fromString(String s) {
        for (ObjectType t : values()) {
            if (t.toString().equals(s.toUpperCase())) return t;
        }
        return null;
    }
}