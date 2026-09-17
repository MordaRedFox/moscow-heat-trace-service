package ru.moscow.heat.geojson.dto;

import lombok.Getter;
import ru.moscow.heat.geojson.FeatureError;
import ru.moscow.heat.geojson.ObjectType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Getter
public class GeoJsonUploadResponse {
    private final Map<ObjectType, Integer> countsByType = new EnumMap<>(ObjectType.class);
    private final List<FeatureError> errors = new ArrayList<>();

    public void incrementCount(ObjectType type) {
        countsByType.merge(type, 1, Integer::sum);
    }

    public void addError(String featureId, String message) {
        errors.add(new FeatureError(featureId, message));
    }
}
