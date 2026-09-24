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

/**
 * Интеграционные тесты резолвера {@link OksConnectionPointResolver}.
 * <p>Проверяется связывание точек подключения ОКС с полигонами
 * {@code restriction_type = oks} через {@code ST_Contains} по
 * колонке {@code geometry_utm}.
 * <p>Покрываются сценарии:
 * <ul>
 *   <li>точка строго внутри полигона;</li>
 *   <li>точка вне любого полигона;</li>
 *   <li>несколько точек в одном полигоне;</li>
 *   <li>точка на границе полигона (не входит в ST_Contains);</li>
 *   <li>изоляция по upload_id между разными сессиями.</li>
 * </ul>
 */
@DisplayName("Тестирование резолвера точек подключения ОКС")
class OksConnectionPointResolverTest
        extends AbstractIntegrationTest {

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

    /**
     * Готовит новый uploadId и опорную точку в UTM для каждого
     * теста. Опорная точка - Красная площадь Москвы
     */
    @BeforeEach
    void setUp() {
        uploadId = UUID.randomUUID();
        double[] baseUtm = transformService.transformPoint(
                37.6175, 55.7522);
        baseX = baseUtm[0];
        baseY = baseUtm[1];
    }

    /**
     * Создает и сохраняет полигон ОКС в колонках geometry и
     * geometry_utm по заданным границам в UTM
     * @param uid       идентификатор загрузки
     * @param featureId идентификатор полигона
     * @param minX      минимальная координата X в UTM
     * @param minY      минимальная координата Y в UTM
     * @param maxX      максимальная координата X в UTM
     * @param maxY      максимальная координата Y в UTM
     * @return сохраненная сущность ограничения
     */
    private RestrictionEntity createOksPolygon(UUID uid,
                                               String featureId,
                                               double minX,
                                               double minY,
                                               double maxX,
                                               double maxY) {
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
        Polygon polyWgs = (Polygon) transformService
                .toWgs84(polyUtm);

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

    /**
     * Создает и сохраняет точку подключения ОКС с заданными
     * координатами в UTM и фиксированным расходом
     * @param uid       идентификатор загрузки
     * @param featureId идентификатор точки
     * @param x         координата X в UTM
     * @param y         координата Y в UTM
     * @return сохраненная сущность точки подключения
     */
    private OksConnectionPointEntity createConnectionPoint(
            UUID uid, String featureId, double x, double y) {
        Point ptUtm = gf.createPoint(new Coordinate(x, y));
        ptUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point ptWgs = (Point) transformService.toWgs84(ptUtm);

        OksConnectionPointEntity entity =
                OksConnectionPointEntity.builder()
                        .flowTph(15.0)
                        .build();
        entity.setUploadId(uid);
        entity.setFeatureId(featureId);
        entity.setGeometry(ptWgs);
        entity.setGeometryUtm(ptUtm);
        entity.setProperties(mapper.createObjectNode());
        return oksCpRepo.save(entity);
    }

    /**
     * Точка внутри полигона связывается с этим полигоном,
     * список непривязанных пуст
     */
    @Test
    @DisplayName("Точка внутри полигона связывается с полигоном")
    void shouldResolvePointInsidePolygon() {
        createOksPolygon(uploadId, "poly_1",
                baseX, baseY, baseX + 100, baseY + 100);
        createConnectionPoint(uploadId, "pt_inside",
                baseX + 50, baseY + 50);

        Map<String, String> resolved =
                resolver.resolvePolygonIds(uploadId);
        assertThat(resolved)
                .hasSize(1)
                .containsEntry("pt_inside", "poly_1");

        List<String> unbound =
                resolver.findUnboundConnectionPoints(uploadId);
        assertThat(unbound).isEmpty();
    }

    /**
     * Точка вне любого полигона не связывается ни с чем и
     * попадает в список непривязанных
     */
    @Test
    @DisplayName("Точка вне полигона попадает в unbound")
    void shouldMarkPointOutsidePolygonAsUnbound() {
        createOksPolygon(uploadId, "poly_1",
                baseX, baseY, baseX + 100, baseY + 100);
        createConnectionPoint(uploadId, "pt_outside",
                baseX + 250, baseY + 250);

        Map<String, String> resolved =
                resolver.resolvePolygonIds(uploadId);
        assertThat(resolved).isEmpty();

        List<String> unbound =
                resolver.findUnboundConnectionPoints(uploadId);
        assertThat(unbound).containsExactly("pt_outside");
    }

    /**
     * Две точки в одном полигоне связываются с одним и тем же
     * полигоном
     */
    @Test
    @DisplayName("Две точки в одном полигоне - один полигон")
    void shouldResolveMultiplePointsInSamePolygon() {
        createOksPolygon(uploadId, "poly_shared",
                baseX, baseY, baseX + 200, baseY + 200);
        createConnectionPoint(uploadId, "pt_1",
                baseX + 50, baseY + 50);
        createConnectionPoint(uploadId, "pt_2",
                baseX + 120, baseY + 80);

        Map<String, String> resolved =
                resolver.resolvePolygonIds(uploadId);
        assertThat(resolved)
                .hasSize(2)
                .containsEntry("pt_1", "poly_shared")
                .containsEntry("pt_2", "poly_shared");

        List<String> unbound =
                resolver.findUnboundConnectionPoints(uploadId);
        assertThat(unbound).isEmpty();
    }

    /**
     * Точка ровно на границе полигона не входит в ST_Contains
     * по семантике OGC и потому попадает в список непривязанных
     */
    @Test
    @DisplayName("Точка на границе полигона - unbound")
    void shouldTreatPointOnBoundaryAsUnbound() {
        createOksPolygon(uploadId, "poly_boundary_test",
                baseX, baseY, baseX + 100, baseY + 100);
        createConnectionPoint(uploadId, "pt_on_border",
                baseX + 50, baseY);

        Map<String, String> resolved =
                resolver.resolvePolygonIds(uploadId);
        assertThat(resolved).doesNotContainKey("pt_on_border");

        List<String> unbound =
                resolver.findUnboundConnectionPoints(uploadId);
        assertThat(unbound).contains("pt_on_border");
    }

    /**
     * Изоляция по upload_id: полигон из другой сессии не должен
     * связываться с точкой текущей сессии
     */
    @Test
    @DisplayName("Изоляция резолвера по upload_id")
    void shouldIsolateByUploadId() {
        UUID otherUploadId = UUID.randomUUID();
        createOksPolygon(otherUploadId, "poly_other",
                baseX, baseY, baseX + 100, baseY + 100);
        createConnectionPoint(uploadId, "pt_current",
                baseX + 50, baseY + 50);

        Map<String, String> resolved =
                resolver.resolvePolygonIds(uploadId);
        assertThat(resolved).isEmpty();

        List<String> unbound =
                resolver.findUnboundConnectionPoints(uploadId);
        assertThat(unbound).containsExactly("pt_current");
    }
}
