package prototype.geojson_archive.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class FeatureError {
    private final int index;
    private final String featureId;
    private final List<String> messages;

    public FeatureError(int index, String featureId, List<String> messages) {
        this.index = index;
        this.featureId = featureId;
        this.messages = messages;
    }
}
