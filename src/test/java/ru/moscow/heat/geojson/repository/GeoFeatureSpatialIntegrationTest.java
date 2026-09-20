package ru.moscow.heat.geojson.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.springframework.beans.factory.annotation.Autowired;
import ru.moscow.heat.AbstractIntegrationTest;
import ru.moscow.heat.geojson.ObjectType;
import ru.moscow.heat.geojson.entity.GeoFeature;
import ru.moscow.heat.geojson.service.CoordinateTransformService;
import ru.moscow.heat.geojson.service.GeometryConverterService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты: сохранение геометрий geom/geom_utm
 * и пространственные запросы PostGIS (bbox, ST_Intersects, ST_DWithin)
 */
class GeoFeatureSpatialIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private GeoFeatureRepository repository;

    @Autowired
    private GeometryConverterService converter;

    @Autowired
    private CoordinateTransformService transformService;

    @Autowired
    private ObjectMapper objectMapper;

    private GeoFeature feature(UUID uploadId, String featureId,
                               String objectType, String geometryJson) throws Exception {
        JsonNode geometryNode = objectMapper.readTree(geometryJson);
        Geometry geometry = converter.fromGeoJson(geometryNode);
        geometry.setSRID(CoordinateTransformService.SRID_WGS84);
        return GeoFeature.builder()
                .uploadId(uploadId)
                .featureId(featureId)
                .objectType(ObjectType.fromString(objectType))
                .geometryType(geometryNode.get("type").asText())
                .geometry(geometryNode)
                .properties(objectMapper.createObjectNode().put("id", featureId))
                .geom(geometry)
                .geomUtm(transformService.toUtm(geometry))
                .build();
    }

    @Test
    void persistedFeatureHasBothSpatialColumns() throws Exception {
        UUID uploadId = UUID.randomUUID();
        repository.saveAndFlush(feature(uploadId, "p1", "source",
                "{\"type\":\"Point\",\"coordinates\":[37.6344054,55.6994811]}"));

        GeoFeature loaded = repository.findAll().stream()
                .filter(f -> f.getUploadId().equals(uploadId))
                .findFirst().orElseThrow();

        assertNotNull(loaded.getGeom(), "колонка geom должна быть заполнена");
        assertNotNull(loaded.getGeomUtm(), "колонка geom_utm должна быть заполнена");
        assertEquals(4326, loaded.getGeom().getSRID());
        assertEquals(32637, loaded.getGeomUtm().getSRID());
        // Метрические координаты UTM 37N для точки датасета
        assertTrue(loaded.getGeomUtm().getCoordinate().x > 400_000);
        assertTrue(loaded.getGeomUtm().getCoordinate().y > 6_170_000);
    }

    @Test
    void findWithinBboxReturnsOnlyInside() throws Exception {
        UUID uploadId = UUID.randomUUID();
        repository.saveAndFlush(feature(uploadId, "in", "source",
                "{\"type\":\"Point\",\"coordinates\":[37.6344,55.6995]}"));
        repository.saveAndFlush(feature(uploadId, "out", "source",
                "{\"type\":\"Point\",\"coordinates\":[37.70,55.75]}"));

        List<GeoFeature> found = repository.findWithinBbox(
                uploadId, 37.63, 55.69, 37.64, 55.70);

        assertEquals(List.of("in"), ids(found));
    }

    @Test
    void findWithinDistanceMetersUsesUtm() throws Exception {
        UUID uploadId = UUID.randomUUID();
        repository.saveAndFlush(feature(uploadId, "near", "source",
                "{\"type\":\"Point\",\"coordinates\":[37.6344,55.6995]}"));
        repository.saveAndFlush(feature(uploadId, "far", "source",
                "{\"type\":\"Point\",\"coordinates\":[37.70,55.75]}"));

        String centerWkt = "POINT(37.6344 55.6995)";
        List<GeoFeature> found = repository.findWithinDistanceMeters(
                uploadId, centerWkt, 200.0);

        assertEquals(List.of("near"), ids(found));
    }

    @Test
    void findIntersectingAndByObjectType() throws Exception {
        UUID uploadId = UUID.randomUUID();
        repository.saveAndFlush(feature(uploadId, "inside", "heat_chamber",
                "{\"type\":\"Point\",\"coordinates\":[37.635,55.6995]}"));
        repository.saveAndFlush(feature(uploadId, "outside", "heat_chamber",
                "{\"type\":\"Point\",\"coordinates\":[37.70,55.75]}"));

        String polygonWkt = "POLYGON((37.63 55.69, 37.64 55.69, 37.64 55.70, "
                + "37.63 55.70, 37.63 55.69))";

        assertEquals(List.of("inside"), ids(repository.findIntersecting(uploadId, polygonWkt)));
        assertEquals(List.of("inside"),
                ids(repository.findByObjectTypeAndGeometryIntersects(
                        uploadId, ObjectType.fromString("heat_chamber").name(), polygonWkt)));
        assertTrue(repository.findByObjectTypeAndGeometryIntersects(
                uploadId, ObjectType.fromString("source").name(), polygonWkt).isEmpty());
    }

    private List<String> ids(List<GeoFeature> features) {
        return features.stream()
                .map(GeoFeature::getFeatureId)
                .sorted()
                .collect(Collectors.toList());
    }
}