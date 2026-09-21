package ru.moscow.heat.geojson.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.moscow.heat.geojson.ObjectType;
import ru.moscow.heat.geojson.entity.AbstractGeoObject;
import ru.moscow.heat.geojson.entity.GeoFeature;
import ru.moscow.heat.geojson.entity.HeatChamberEntity;
import ru.moscow.heat.geojson.entity.HeatNetworkEntity;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.entity.RestrictionEntity;
import ru.moscow.heat.geojson.entity.SourceEntity;
import ru.moscow.heat.geojson.repository.HeatChamberRepository;
import ru.moscow.heat.geojson.repository.HeatNetworkRepository;
import ru.moscow.heat.geojson.repository.OksConnectionPointRepository;
import ru.moscow.heat.geojson.repository.RestrictionRepository;
import ru.moscow.heat.geojson.repository.SourceRepository;

import java.util.UUID;

/**
 * Раскладывает {@link GeoFeature} по типизированным таблицам
 * ({@code source}, {@code heat_network}, {@code heat_chamber},
 * {@code oks_connection_point}, {@code restriction}).
 * <p>Состав таблиц соответствует актуальной модели: 5 типов вместо
 * прежних 7. {@code oks_future} и {@code oks_existing} удалены -
 * полигоны ОКС передаются как {@code restriction_type = oks},
 * а цель подключения — как {@code oks_connection_point}.
 * <p>Работает в рамках уже открытой транзакции: вызывающий код
 * ({@code GeoFeatureBatchWriter}) открывает ее через {@code @Transactional}
 */
@Service
@RequiredArgsConstructor
public class GeoObjectPersister {

    private final SourceRepository sourceRepo;
    private final HeatNetworkRepository heatNetworkRepo;
    private final HeatChamberRepository heatChamberRepo;
    private final OksConnectionPointRepository oksCpRepo;
    private final RestrictionRepository restrictionRepo;

    /**
     * Сохраняет фичу в типизированную таблицу согласно ее {@code object_type}
     * @param feature валидная фича с заполненными {@code geom} и {@code geomUtm}
     * @throws IllegalArgumentException если тип объекта неизвестен
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void persist(GeoFeature feature) {
        ObjectType type = feature.getObjectType();
        UUID uploadId = feature.getUploadId();
        String featureId = feature.getFeatureId();
        Geometry geom4326 = feature.getGeom();
        Geometry geomUtm = feature.getGeomUtm();
        JsonNode properties = feature.getProperties();

        switch (type) {
            case SOURCE: {
                SourceEntity e = build(new SourceEntity(),
                        uploadId, featureId, geom4326, geomUtm,
                        properties);
                sourceRepo.save(e);
                break;
            }
            case HEAT_NETWORK: {
                HeatNetworkEntity e = build(new HeatNetworkEntity(),
                        uploadId, featureId, geom4326, geomUtm,
                        properties);
                e.setDiameter(properties.path("diameter")
                        .asInt(0));
                heatNetworkRepo.save(e);
                break;
            }
            case HEAT_CHAMBER: {
                HeatChamberEntity e = build(new HeatChamberEntity(),
                        uploadId, featureId, geom4326, geomUtm,
                        properties);
                heatChamberRepo.save(e);
                break;
            }
            case OKS_CONNECTION_POINT: {
                OksConnectionPointEntity e = build(
                        new OksConnectionPointEntity(),
                        uploadId, featureId, geom4326, geomUtm,
                        properties);
                e.setFlowTph(properties.path("flow_tph")
                        .asDouble(0.0));
                oksCpRepo.save(e);
                break;
            }
            case RESTRICTION: {
                RestrictionEntity e = build(new RestrictionEntity(),
                        uploadId, featureId, geom4326, geomUtm,
                        properties);
                e.setRestrictionType(properties
                        .path("restriction_type").asText(null));
                restrictionRepo.save(e);
                break;
            }
            default:
                throw new IllegalArgumentException(
                        "Неизвестный тип объекта: " + type);
        }
    }

    /**
     * Общий билдер для всех наследников {@link AbstractGeoObject}:
     * заполняет идентификацию, геометрию и properties
     */
    private <T extends AbstractGeoObject> T build(
            T entity,
            UUID uploadId,
            String featureId,
            Geometry geom4326,
            Geometry geomUtm,
            JsonNode properties) {
        entity.setUploadId(uploadId);
        entity.setFeatureId(featureId);
        entity.setGeometry(geom4326);
        entity.setGeometryUtm(geomUtm);
        entity.setProperties(properties);
        return entity;
    }
}
