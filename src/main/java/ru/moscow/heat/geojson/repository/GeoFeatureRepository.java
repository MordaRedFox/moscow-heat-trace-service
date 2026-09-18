package ru.moscow.heat.geojson.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.moscow.heat.geojson.entity.GeoFeature;

public interface GeoFeatureRepository extends JpaRepository<GeoFeature, Long> {
}
