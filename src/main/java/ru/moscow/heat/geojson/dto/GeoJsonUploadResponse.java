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

    private int totalCount = 0;

    private Double minX;
    private Double minY;
    private Double maxX;
    private Double maxY;

    public void incrementCount(ObjectType type) {
        countsByType.merge(type, 1, Integer::sum);
        totalCount++;
    }

    public void addError(String featureId, String message) {
        errors.add(new FeatureError(featureId, message));
    }

    public void updateBbox(double x, double y) {
        if (minX == null || x < minX) minX = x;
        if (minY == null || y < minY) minY = y;
        if (maxX == null || x > maxX) maxX = x;
        if (maxY == null || y > maxY) maxY = y;
    }

    public boolean hasBbox() {
        return minX != null && minY != null && maxX != null && maxY != null;
    }

    public List<Double> getBbox() {
        if (!hasBbox()) {
            return null;
        }
        return List.of(minX, minY, maxX, maxY);
    }
}
