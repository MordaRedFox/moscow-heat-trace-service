package ru.moscow.heat.geojson;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class FeatureError {
    private final String featureId;
    private final String message;
}
