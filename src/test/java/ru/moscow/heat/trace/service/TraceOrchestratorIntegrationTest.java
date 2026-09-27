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
import ru.moscow.heat.geojson.entity.HeatNetworkEntity;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.entity.RestrictionEntity;
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
 * Интеграционный тест {@link TraceOrchestrator} - сквозной прогон
 * на PostGIS/Testcontainers
 * <p>
 * Сценарий: точка ОКС лежит внутри своего полигона {@code restriction_type=oks},
 * рядом проходит существующая сеть без камер. Ожидается:
 * <ul>
 *   <li>трассировка не падает;</li>
 *   <li>ОКС подключён (обход своего полигона через ignore-set);</li>
 *   <li>тип новой камеры = {@link RouteNodeType#NEW_CHAMBER};</li>
 *   <li>первый узел маршрута = {@link RouteNodeType#OKS_POINT};</li>
 *   <li>все сегменты имеют одинаковый flow (один ОКС);</li>
 *   <li>flow на сегментах соответствует flow_tph ОКС;</li>
 *   <li>счётчики корректны (total=1, connected=1, unconnected=0).</li>
 * </ul>
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
        RouteSegment last = result.getSegments().get(result.getSegments().size() - 1);

        assertThat(first.getFromNode().getType()).isEqualTo(RouteNodeType.OKS_POINT);
        assertThat(first.getFromNode().getSourceFeatureId()).isEqualTo("oks-pt-1");

        // Тип конечного узла: NEW_CHAMBER — камер нет, значит новая
        assertThat(last.getToNode().getType()).isEqualTo(RouteNodeType.NEW_CHAMBER);

        // flow на всех сегментах = flow_tph ОКС
        for (RouteSegment s : result.getSegments()) {
            assertThat(s.getFlowTph().doubleValue()).isEqualTo(30.0);
            assertThat(s.getDiameterMm()).isPositive();
        }

        // Одна новая камера создана
        assertThat(result.getNewChambers()).hasSize(1);
    }

    // Хелперы

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
