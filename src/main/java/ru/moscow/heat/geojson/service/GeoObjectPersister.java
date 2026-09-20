package ru.moscow.heat.geojson.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Geometry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.moscow.heat.geojson.ObjectType;
import ru.moscow.heat.geojson.entity.*;
import ru.moscow.heat.geojson.repository.*;
import ru.moscow.heat.geojson.util.GeoJsonGeometryMapper;

@Service
@RequiredArgsConstructor
public class GeoObjectPersister {

    private final GeoJsonGeometryMapper geometryMapper;
    private final CoordinateTransformService transformService;

    private final SourceRepository             sourceRepo;
    private final HeatNetworkRepository        heatNetworkRepo;
    private final HeatChamberRepository        heatChamberRepo;
    private final OksFutureRepository          oksFutureRepo;
    private final OksConnectionPointRepository oksCpRepo;
    private final OksExistingRepository        oksExistingRepo;
    private final RestrictionRepository        restrictionRepo;

    /**
     * Сохраняет одну фичу в типизированную таблицу.
     * @throws DataIntegrityViolationException при дубликате feature_id
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(ObjectType type,
                        String featureId,
                        JsonNode geometryNode,
                        JsonNode properties) {

        Geometry geom4326 = geometryMapper.toJts(geometryNode);
        geom4326.setSRID(4326);
        Geometry geomUtm = transformService.toUtm(geom4326);

        switch (type) {
            case SOURCE -> sourceRepo.save(build(new SourceEntity(), featureId, geom4326, geomUtm, properties));
            case HEAT_NETWORK -> {
                HeatNetworkEntity e = build(new HeatNetworkEntity(), featureId, geom4326, geomUtm, properties);
                e.setDiameter(properties.path("diameter").asDouble());
                e.setFlowTph(properties.path("flow_tph").asDouble());
                e.setUpstreamObjectId(properties.path("upstream_object_id").asText(null));
                heatNetworkRepo.save(e);
            }
            case HEAT_CHAMBER -> {
                HeatChamberEntity e = build(new HeatChamberEntity(), featureId, geom4326, geomUtm, properties);
                e.setDiameter(properties.path("diameter").asDouble());
                e.setUpstreamObjectId(properties.path("upstream_object_id").asText(null));
                heatChamberRepo.save(e);
            }
            case OKS_FUTURE -> {
                OksFutureEntity e = build(new OksFutureEntity(), featureId, geom4326, geomUtm, properties);
                e.setFlowTph(properties.path("flow_tph").asDouble());
                e.setHeatLoad(properties.path("heat_load").asDouble());
                oksFutureRepo.save(e);
            }
            case OKS_CONNECTION_POINT -> {
                OksConnectionPointEntity e = build(new OksConnectionPointEntity(), featureId, geom4326, geomUtm, properties);
                e.setOksId(properties.path("oks_id").asText(null));
                oksCpRepo.save(e);
            }
            case OKS_EXISTING -> oksExistingRepo.save(build(new OksExistingEntity(), featureId, geom4326, geomUtm, properties));
            case RESTRICTION -> {
                RestrictionEntity e = build(new RestrictionEntity(), featureId, geom4326, geomUtm, properties);
                e.setRestrictionType(properties.path("restriction_type").asText(null));
                restrictionRepo.save(e);
            }
        }
    }

    private <T extends AbstractGeoObject> T build(T entity,
                                                  String featureId,
                                                  Geometry geom4326,
                                                  Geometry geomUtm,
                                                  JsonNode properties) {
        entity.setFeatureId(featureId);
        entity.setGeometry(geom4326);
        entity.setGeometryUtm(geomUtm);
        entity.setProperties(properties);
        return entity;
    }
}