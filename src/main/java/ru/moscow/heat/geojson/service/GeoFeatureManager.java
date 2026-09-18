package ru.moscow.heat.geojson.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.entity.GeoFeature;
import ru.moscow.heat.geojson.repository.GeoFeatureRepository;

@Service
@RequiredArgsConstructor
public class GeoFeatureManager {
    private final GeoFeatureRepository geoFeatureRepository;

    public GeoFeature save(GeoFeature geoFeature) {
        return geoFeatureRepository.save(geoFeature);
    }
}
