package prototype.geojson_archive;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public final class RequiredAttributes {

    private static final Map<ObjectType, Set<String>> REQUIRED = new EnumMap<>(ObjectType.class);

    static {
        REQUIRED.put(ObjectType.SOURCE, Set.of());
        REQUIRED.put(ObjectType.HEAT_NETWORK, Set.of("diameter", "flow_tph", "upstream_object_id"));
        REQUIRED.put(ObjectType.HEAT_CHAMBER, Set.of("diameter", "upstream_object_id"));
        REQUIRED.put(ObjectType.OKS_FUTURE, Set.of("flow_tph", "heat_load"));
        REQUIRED.put(ObjectType.OKS_CONNECTION_POINT, Set.of("oks_id"));
        REQUIRED.put(ObjectType.OKS_EXISTING, Set.of());
        REQUIRED.put(ObjectType.RESTRICTION, Set.of("restriction_type"));
    }

    private RequiredAttributes() {}

    public static Set<String> forType(ObjectType type) {
        return REQUIRED.getOrDefault(type, Set.of());
    }
}