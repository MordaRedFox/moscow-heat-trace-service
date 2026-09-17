package prototype.geojson_archive.dto;

import lombok.Getter;
import lombok.Setter;
import prototype.geojson_archive.ObjectType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class GeoJsonUploadSummary {
    private final Map<ObjectType, Integer> countsByType = new EnumMap<>(ObjectType.class);
    private double[] bbox; // [minX, minY, maxX, maxY]
    private final List<FeatureError> errors = new ArrayList<>();

    public void incrementCount(ObjectType type) {
        countsByType.merge(type, 1, Integer::sum);
    }

    public void addError(int index, String id, List<String> messages) {
        errors.add(new FeatureError(index, id, messages));
    }
}

