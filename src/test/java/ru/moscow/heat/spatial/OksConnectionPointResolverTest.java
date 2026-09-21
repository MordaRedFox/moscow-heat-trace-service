package ru.moscow.heat.spatial;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Autowired;
import ru.moscow.heat.AbstractIntegrationTest;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.entity.RestrictionEntity;
import ru.moscow.heat.geojson.repository.OksConnectionPointRepository;
import ru.moscow.heat.geojson.repository.RestrictionRepository;
import ru.moscow.heat.geojson.service.CoordinateTransformService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Интеграционное тестирование резолвера точек подключения ОКС (OksConnectionPointResolver)")
class OksConnectionPointResolverTest extends AbstractIntegrationTest {

    @Autowired
    private OksConnectionPointResolver resolver;

    @Autowired
    private OksConnectionPointRepository oksCpRepo;

    @Autowired
    private RestrictionRepository restrictionRepo;

    @Autowired
    private CoordinateTransformService transformService;

    private final GeometryFactory gf = new GeometryFactory();
    private final ObjectMapper mapper = new ObjectMapper();

    private UUID uploadId;
    private double baseX;
    private double baseY;

    @BeforeEach
    void setUp() {
        uploadId = UUID.randomUUID();
        double[] baseUtm = transformService.transformPoint(37.6175, 55.7522);
        baseX = baseUtm[0];
        baseY = baseUtm[1];
    }

    private RestrictionEntity createOksPolygon(UUID uid, String featureId, double minX, double minY, double maxX, double maxY) {
        Coordinate[] coords = new Coordinate[]{
                new Coordinate(minX, minY),
                new Coordinate(maxX, minY),
                new Coordinate(maxX, maxY),
                new Coordinate(minX, maxY),
                new Coordinate(minX, minY)
        };
        LinearRing ring = gf.createLinearRing(coords);
        Polygon polyUtm = gf.createPolygon(ring);
        polyUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Polygon polyWgs = (Polygon) transformService.toWgs84(polyUtm);

        RestrictionEntity entity = RestrictionEntity.builder()
                .restrictionType("oks")
                .build();
        entity.setUploadId(uid);
        entity.setFeatureId(featureId);
        entity.setGeometry(polyWgs);
        entity.setGeometryUtm(polyUtm);
        entity.setProperties(mapper.createObjectNode());
        return restrictionRepo.save(entity);
    }

    private OksConnectionPointEntity createConnectionPoint(UUID uid, String featureId, double x, double y) {
        Point ptUtm = gf.createPoint(new Coordinate(x, y));
        ptUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point ptWgs = (Point) transformService.toWgs84(ptUtm);

        OksConnectionPointEntity entity = OksConnectionPointEntity.builder()
                .flowTph(15.0)
                .build();
        entity.setUploadId(uid);
        entity.setFeatureId(featureId);
        entity.setGeometry(ptWgs);
        entity.setGeometryUtm(ptUtm);
        entity.setProperties(mapper.createObjectNode());
        return oksCpRepo.save(entity);
    }

    @Test
    @DisplayName("Связывание точки, находящейся строго внутри полигона ОКС")
    void shouldResolvePointInsidePolygon() {
        createOksPolygon(uploadId, "poly_1", baseX, baseY, baseX + 100, baseY + 100);
        createConnectionPoint(uploadId, "pt_inside", baseX + 50, baseY + 50);

        Map<String, String> resolved = resolver.resolvePolygonIds(uploadId);
        assertThat(resolved)
                .hasSize(1)
                .containsEntry("pt_inside", "poly_1");

        List<String> unbound = resolver.findUnboundConnectionPoints(uploadId);
        assertThat(unbound).isEmpty();
    }

    @Test
    @DisplayName("Точка вне любого полигона ОКС должна попасть в unbound")
    void shouldMarkPointOutsidePolygonAsUnbound() {
        createOksPolygon(uploadId, "poly_1", baseX, baseY, baseX + 100, baseY + 100);
        createConnectionPoint(uploadId, "pt_outside", baseX + 250, baseY + 250);

        Map<String, String> resolved = resolver.resolvePolygonIds(uploadId);
        assertThat(resolved).isEmpty();

        List<String> unbound = resolver.findUnboundConnectionPoints(uploadId);
        assertThat(unbound).containsExactly("pt_outside");
    }

    @Test
    @DisplayName("Две точки подключения в одном полигоне ОКС связываются с одним полигоном")
    void shouldResolveMultiplePointsInSamePolygon() {
        createOksPolygon(uploadId, "poly_shared", baseX, baseY, baseX + 200, baseY + 200);
        createConnectionPoint(uploadId, "pt_1", baseX + 50, baseY + 50);
        createConnectionPoint(uploadId, "pt_2", baseX + 120, baseY + 80);

        Map<String, String> resolved = resolver.resolvePolygonIds(uploadId);
        assertThat(resolved)
                .hasSize(2)
                .containsEntry("pt_1", "poly_shared")
                .containsEntry("pt_2", "poly_shared");

        List<String> unbound = resolver.findUnboundConnectionPoints(uploadId);
        assertThat(unbound).isEmpty();
    }

    @Test
    @DisplayName("Точка ровно на границе полигона ОКС не входит в ST_Contains и попадает в unbound")
    void shouldTreatPointOnBoundaryAsUnbound() {
        // Полигон от baseX..baseX+100, baseY..baseY+100
        createOksPolygon(uploadId, "poly_boundary_test", baseX, baseY, baseX + 100, baseY + 100);
        // Точка лежит ровно на нижней границе: y = baseY, x = baseX + 50
        createConnectionPoint(uploadId, "pt_on_border", baseX + 50, baseY);

        Map<String, String> resolved = resolver.resolvePolygonIds(uploadId);
        assertThat(resolved).doesNotContainKey("pt_on_border");

        List<String> unbound = resolver.findUnboundConnectionPoints(uploadId);
        assertThat(unbound).contains("pt_on_border");
    }

    @Test
    @DisplayName("Изоляция по uploadId: объекты другой сессии не влияют на резолвинг")
    void shouldIsolateByUploadId() {
        UUID otherUploadId = UUID.randomUUID();
        createOksPolygon(otherUploadId, "poly_other", baseX, baseY, baseX + 100, baseY + 100);
        createConnectionPoint(uploadId, "pt_current", baseX + 50, baseY + 50);

        // В текущей сессии полигона нет -> точка не должна привязаться к poly_other
        Map<String, String> resolved = resolver.resolvePolygonIds(uploadId);
        assertThat(resolved).isEmpty();

        List<String> unbound = resolver.findUnboundConnectionPoints(uploadId);
        assertThat(unbound).containsExactly("pt_current");
    }
}
