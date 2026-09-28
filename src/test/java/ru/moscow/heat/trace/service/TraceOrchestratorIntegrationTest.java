package ru.moscow.heat.trace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Autowired;
import ru.moscow.heat.AbstractIntegrationTest;
import ru.moscow.heat.geojson.entity.HeatChamberEntity;
import ru.moscow.heat.geojson.entity.HeatNetworkEntity;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.entity.RestrictionEntity;
import ru.moscow.heat.geojson.repository.HeatChamberRepository;
import ru.moscow.heat.geojson.repository.HeatNetworkRepository;
import ru.moscow.heat.geojson.repository.OksConnectionPointRepository;
import ru.moscow.heat.geojson.repository.RestrictionRepository;
import ru.moscow.heat.geojson.service.CoordinateTransformService;
import ru.moscow.heat.trace.model.RouteNodeType;
import ru.moscow.heat.trace.model.RouteSegment;
import ru.moscow.heat.trace.model.TraceResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный тест {@link TraceOrchestrator} — сквозной прогон
 * на PostGIS/Testcontainers.
 *
 * <p>Проверяются два сценария:
 * <ol>
 *   <li>один ОКС внутри своего полигона {@code restriction_type=oks},
 *       сеть рядом — одиночный линейный пайплайн;</li>
 *   <li>три ОКС над одной сетью с существующей камерой — групповой
 *       пайплайн итерации 6 (камерная группировка, дерево маршрутов,
 *       все точки подключены).</li>
 * </ol>
 *
 * <p>Проверяется <b>факт успешного подключения</b> всех ОКС группы,
 * а не конкретная топология дерева (число общих стволов, точные flow
 * на ветвях). Топология зависит от геометрии visibility graph и не
 * является контрактом оркестратора — её покрывают юнит-тесты
 * {@code TreeRouterTest}, {@code FlowAggregatorTest},
 * {@code TreeDiameterAssignerTest}, {@code TreeRouteSegmentSplitterTest}.
 */
@DisplayName("Интеграционный тест TraceOrchestrator")
class TraceOrchestratorIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TraceOrchestrator traceOrchestrator;

    @Autowired
    private OksConnectionPointRepository oksRepo;

    @Autowired
    private HeatNetworkRepository heatNetworkRepo;

    @Autowired
    private RestrictionRepository restrictionRepo;

    @Autowired
    private HeatChamberRepository heatChamberRepo;

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

    @Test
    @DisplayName("Один ОКС внутри своего полигона — маршрут до сети построен")
    void singleOksInsideItsPolygon_Connected() {
        // Полигон ОКС вокруг baseX, baseY радиусом ~60 м
        saveOksPolygon(uploadId, "oks-poly-1",
                baseX - 60, baseY - 60,
                baseX + 60, baseY + 60);

        // Точка ОКС строго внутри полигона
        saveConnectionPoint(uploadId, "oks-pt-1", 30.0,
                baseX, baseY);

        // Сеть проходит слева-вправо в 200 м от точки, вне полигона
        saveNetwork(uploadId, "net-1", 500,
                baseX + 200, baseY - 100,
                baseX + 200, baseY + 100);

        TraceResult result = traceOrchestrator.run(uploadId);

        assertThat(result.getSummaryCounters().getTotalOksCount()).isEqualTo(1);
        assertThat(result.getSummaryCounters().getConnectedCount()).isEqualTo(1);
        assertThat(result.getSummaryCounters().getUnconnectedCount()).isZero();
        assertThat(result.getUnconnectedOks()).isEmpty();

        assertThat(result.getSegments()).isNotEmpty();

        RouteSegment first = result.getSegments().get(0);
        RouteSegment last = result.getSegments()
                .get(result.getSegments().size() - 1);

        assertThat(first.getFromNode().getType())
                .isEqualTo(RouteNodeType.OKS_POINT);
        assertThat(first.getFromNode().getSourceFeatureId())
                .isEqualTo("oks-pt-1");

        // Камер нет, значит новая
        assertThat(last.getToNode().getType())
                .isEqualTo(RouteNodeType.NEW_CHAMBER);

        // flow на всех сегментах = flow_tph ОКС (одиночный маршрут)
        for (RouteSegment s : result.getSegments()) {
            assertThat(s.getFlowTph().doubleValue()).isEqualTo(30.0);
            assertThat(s.getDiameterMm()).isPositive();
        }

        assertThat(result.getNewChambers()).hasSize(1);
    }

    @Test
    @DisplayName("3 ОКС над одной сетью с камерой — все подключены")
    void threeOksToOneChamber_allConnected() {
        // Сеть — горизонтальный сегмент около baseX
        saveNetwork(uploadId, "net-group", 500,
                baseX - 100, baseY,
                baseX + 100, baseY);

        // Существующая камера на середине сети
        saveChamber(uploadId, "chamber-1", baseX, baseY);

        // Три ОКС над сетью, все в радиусе 10 м от камеры по tie-in
        // (ближайшая точка на сети к каждому ОКС лежит около камеры)
        saveConnectionPoint(uploadId, "oks-g1", 20.0,
                baseX - 5, baseY + 20);
        saveConnectionPoint(uploadId, "oks-g2", 30.0,
                baseX, baseY + 25);
        saveConnectionPoint(uploadId, "oks-g3", 15.0,
                baseX + 5, baseY + 20);

        TraceResult result = traceOrchestrator.run(uploadId);

        // Все три подключены — групповой пайплайн не упал
        assertThat(result.getSummaryCounters().getTotalOksCount()).isEqualTo(3);
        assertThat(result.getSummaryCounters().getConnectedCount()).isEqualTo(3);
        assertThat(result.getSummaryCounters().getUnconnectedCount()).isZero();
        assertThat(result.getUnconnectedOks()).isEmpty();

        // Сегменты построены и валидны
        assertThat(result.getSegments()).isNotEmpty();
        assertThat(result.getSegments()).allSatisfy(seg -> {
            assertThat(seg.getDiameterMm()).isPositive();
            assertThat(seg.getLengthM()).isPositive();
        });
    }

    // ---------- Хелперы ----------

    private void saveChamber(UUID uid, String featureId, double x, double y) {
        Point ptUtm = gf.createPoint(new Coordinate(x, y));
        ptUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point ptWgs = (Point) transformService.toWgs84(ptUtm);

        HeatChamberEntity chamber = HeatChamberEntity.builder().build();
        chamber.setUploadId(uid);
        chamber.setFeatureId(featureId);
        chamber.setGeometry(ptWgs);
        chamber.setGeometryUtm(ptUtm);
        chamber.setProperties(mapper.createObjectNode());
        heatChamberRepo.save(chamber);
    }

    private void saveOksPolygon(UUID uid, String featureId,
                                 double minX, double minY,
                                 double maxX, double maxY) {
        LinearRing ring = gf.createLinearRing(new Coordinate[]{
                new Coordinate(minX, minY),
                new Coordinate(maxX, minY),
                new Coordinate(maxX, maxY),
                new Coordinate(minX, maxY),
                new Coordinate(minX, minY)
        });
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
        restrictionRepo.save(entity);
    }

    private void saveConnectionPoint(UUID uid, String featureId,
                                      double flowTph, double x, double y) {
        Point ptUtm = gf.createPoint(new Coordinate(x, y));
        ptUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point ptWgs = (Point) transformService.toWgs84(ptUtm);

        OksConnectionPointEntity p = OksConnectionPointEntity.builder()
                .flowTph(flowTph)
                .build();
        p.setUploadId(uid);
        p.setFeatureId(featureId);
        p.setGeometry(ptWgs);
        p.setGeometryUtm(ptUtm);
        p.setProperties(mapper.createObjectNode());
        oksRepo.save(p);
    }

    private void saveNetwork(UUID uid, String featureId, int diameter,
                              double x0, double y0, double x1, double y1) {
        LineString utmLine = gf.createLineString(new Coordinate[]{
                new Coordinate(x0, y0),
                new Coordinate(x1, y1)
        });
        utmLine.setSRID(CoordinateTransformService.SRID_UTM_37N);
        LineString wgsLine = (LineString) transformService.toWgs84(utmLine);

        HeatNetworkEntity net = HeatNetworkEntity.builder()
                .diameter(diameter)
                .build();
        net.setUploadId(uid);
        net.setFeatureId(featureId);
        net.setGeometry(wgsLine);
        net.setGeometryUtm(utmLine);
        net.setProperties(mapper.createObjectNode());
        heatNetworkRepo.save(net);
    }
}
