package ru.moscow.heat.trace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ru.moscow.heat.AbstractIntegrationTest;
import ru.moscow.heat.geojson.entity.HeatChamberEntity;
import ru.moscow.heat.geojson.entity.HeatNetworkEntity;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.entity.RestrictionEntity;
import ru.moscow.heat.geojson.entity.UploadSession;
import ru.moscow.heat.geojson.repository.HeatChamberRepository;
import ru.moscow.heat.geojson.repository.HeatNetworkRepository;
import ru.moscow.heat.geojson.repository.OksConnectionPointRepository;
import ru.moscow.heat.geojson.repository.RestrictionRepository;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;
import ru.moscow.heat.geojson.service.CoordinateTransformService;
import ru.moscow.heat.trace.dto.TraceAcceptedResponse;
import ru.moscow.heat.trace.dto.TraceStatus;
import ru.moscow.heat.trace.dto.TraceStatusResponse;
import ru.moscow.heat.trace.dto.VariantSummary;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционный smoke-тест полного цикла трассировки и экспорта в GeoJSON.
 *
 * <p>Актуальная реализация: {@code TraceService.createTraceSession(...)}
 * только регистрирует сессию; асинхронный расчёт запускается вызовом
 * {@link TraceAsyncProcessor#process(UUID, UUID)} (как это делает
 * {@code TraceController}). После этого оркестратор прогоняет три стратегии,
 * результат маппится {@link TraceResultMapper} и сохраняется в in-memory
 * сессии. Эти шаги явно воспроизводятся в тесте, включая ожидание
 * перехода статуса в {@code COMPLETED}.
 */
@DisplayName("Интеграционный smoke-тест полного цикла трассировки и экспорта в GeoJSON")
class TraceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TraceService traceService;

    @Autowired
    private TraceAsyncProcessor traceAsyncProcessor;

    @Autowired
    private UploadSessionRepository uploadSessionRepo;

    @Autowired
    private HeatNetworkRepository heatNetworkRepo;

    @Autowired
    private HeatChamberRepository heatChamberRepo;

    @Autowired
    private OksConnectionPointRepository oksCpRepo;

    @Autowired
    private RestrictionRepository restrictionRepo;

    @Autowired
    private CoordinateTransformService transformService;

    private final GeometryFactory gf = new GeometryFactory();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Полный цикл: расчет вариантов на синтетическом наборе и экспорт в валидный GeoJSON")
    void fullTraceAndExportSmokeTest() throws Exception {
        UUID uploadId = UUID.randomUUID();

        // 1. Создаем сессию загрузки
        UploadSession session = UploadSession.builder()
                .id(uploadId)
                .fileName("synthetic-test.geojson")
                .fileSize(1024L)
                .status(ru.moscow.heat.geojson.UploadStatus.COMPLETED)
                .createdAt(java.time.OffsetDateTime.now())
                .build();
        uploadSessionRepo.save(session);

        double[] baseUtm = transformService.transformPoint(37.6175, 55.7522);
        double baseX = baseUtm[0];
        double baseY = baseUtm[1];

        // 2. Создаем существующую сеть и камеру
        Point chPoint = gf.createPoint(new Coordinate(baseX, baseY));
        chPoint.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point chWgs = (Point) transformService.toWgs84(chPoint);

        HeatChamberEntity chamber = HeatChamberEntity.builder().build();
        chamber.setUploadId(uploadId);
        chamber.setFeatureId("chamber-smoke-1");
        chamber.setGeometry(chWgs);
        chamber.setGeometryUtm(chPoint);
        chamber.setProperties(mapper.createObjectNode());
        heatChamberRepo.save(chamber);

        LineString netLine = gf.createLineString(new Coordinate[]{
                new Coordinate(baseX - 50.0, baseY),
                new Coordinate(baseX, baseY),
                new Coordinate(baseX + 50.0, baseY)
        });
        netLine.setSRID(CoordinateTransformService.SRID_UTM_37N);
        LineString netWgs = (LineString) transformService.toWgs84(netLine);

        HeatNetworkEntity network = HeatNetworkEntity.builder()
                .diameter(200)
                .build();
        network.setUploadId(uploadId);
        network.setFeatureId("net-smoke-1");
        network.setGeometry(netWgs);
        network.setGeometryUtm(netLine);
        network.setProperties(mapper.createObjectNode());
        heatNetworkRepo.save(network);

        // 3. Создаем точку подключения ОКС (в 30 м от камеры)
        Point oksPoint = gf.createPoint(new Coordinate(baseX, baseY + 30.0));
        oksPoint.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point oksWgs = (Point) transformService.toWgs84(oksPoint);

        OksConnectionPointEntity oks = OksConnectionPointEntity.builder()
                .flowTph(15.0)
                .build();
        oks.setUploadId(uploadId);
        oks.setFeatureId("oks-smoke-1");
        oks.setGeometry(oksWgs);
        oks.setGeometryUtm(oksPoint);
        oks.setProperties(mapper.createObjectNode());
        oksCpRepo.save(oks);

        // 4. Регистрируем сессию трассировки
        TraceAcceptedResponse response = traceService.createTraceSession(uploadId);
        assertThat(response).isNotNull();
        UUID traceId = response.getTraceId();

        // 5. Проверяем исходный статус
        TraceStatusResponse initialStatus = traceService.getTraceStatus(traceId);
        assertThat(initialStatus.getStatus()).isEqualTo(TraceStatus.PENDING);

        // 6. Запускаем async-обработку (как это делает TraceController)
        //    и дожидаемся перехода в COMPLETED
        traceAsyncProcessor.process(traceId, uploadId);
        long start = System.currentTimeMillis();
        while (traceService.getTraceStatus(traceId).getStatus() != TraceStatus.COMPLETED
                && System.currentTimeMillis() - start < 15_000) {
            Thread.sleep(100);
        }
        assertThat(traceService.getTraceStatus(traceId).getStatus())
                .isEqualTo(TraceStatus.COMPLETED);

        // 7. Получаем варианты трассировки (маппинг через TraceResultMapper,
        //    без дополнительного ранжирования — rank остаётся null)
        List<VariantSummary> variants = traceService.getVariants(traceId);
        assertThat(variants).isNotEmpty();
        VariantSummary topVariant = variants.get(0);
        assertThat(topVariant.getScore()).isGreaterThan(0.0);
        assertThat(topVariant.getNewNetworkLength()).isGreaterThan(0.0);
        assertThat(topVariant.getCalculatedCost())
                .isGreaterThan(BigDecimal.ZERO);

        // 8. Потоковый экспорт в GeoJSON
        StreamingResponseBody body = traceService.exportTrace(traceId, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        body.writeTo(baos);

        byte[] bytes = baos.toByteArray();
        assertThat(bytes).isNotEmpty();

        JsonNode root = mapper.readTree(bytes);
        assertThat(root.get("type").asText()).isEqualTo("FeatureCollection");
        JsonNode features = root.get("features");
        assertThat(features.isArray()).isTrue();
        assertThat(features.size()).isGreaterThanOrEqualTo(3);

        boolean hasNetwork = false;
        boolean hasChamber = false;
        boolean hasSummary = false;

        for (JsonNode f : features) {
            JsonNode props = f.get("properties");
            assertThat(props.has("variant_id")).isTrue();
            String objType = props.get("object_type").asText();
            if ("heat_network".equals(objType)) {
                hasNetwork = true;
                assertThat(props.has("diameter")).isTrue();
                assertThat(props.has("length")).isTrue();
                assertThat(props.has("cost")).isTrue();
            } else if ("heat_chamber".equals(objType)) {
                hasChamber = true;
            } else if ("variant_summary".equals(objType)) {
                hasSummary = true;
                assertThat(f.get("geometry").isNull()).isTrue();
                assertThat(props.has("score")).isTrue();
                assertThat(props.has("calculated_cost")).isTrue();
            }
        }

        assertThat(hasNetwork).isTrue();
        assertThat(hasChamber).isTrue();
        assertThat(hasSummary).isTrue();
    }

    @Test
    @DisplayName("Трассировка через TraceService с препятствием: в variants попадает результат A* с обходом")
    void obstacleAvoidanceAStarWiredToVariants() throws Exception {
        UUID uploadId = UUID.randomUUID();

        UploadSession session = UploadSession.builder()
                .id(uploadId)
                .fileName("obstacle-avoidance-test.geojson")
                .fileSize(1024L)
                .status(ru.moscow.heat.geojson.UploadStatus.COMPLETED)
                .createdAt(java.time.OffsetDateTime.now())
                .build();
        uploadSessionRepo.save(session);

        double[] baseUtm = transformService.transformPoint(37.6175, 55.7522);
        double baseX = baseUtm[0];
        double baseY = baseUtm[1];

        // 1. Полигон ОКС вокруг точки подключения (y: +90..+120)
        savePolygon(uploadId, "oks-poly-obs", "oks",
                baseX - 20, baseY + 90,
                baseX + 20, baseY + 120);

        // 2. Точка подключения ОКС в (baseX, baseY + 100)
        Point oksPoint = gf.createPoint(new Coordinate(baseX, baseY + 100.0));
        oksPoint.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point oksWgs = (Point) transformService.toWgs84(oksPoint);
        OksConnectionPointEntity oks = OksConnectionPointEntity.builder()
                .flowTph(15.0)
                .build();
        oks.setUploadId(uploadId);
        oks.setFeatureId("oks-obs-1");
        oks.setGeometry(oksWgs);
        oks.setGeometryUtm(oksPoint);
        oks.setProperties(mapper.createObjectNode());
        oksCpRepo.save(oks);

        // 3. Сеть и камера в (baseX, baseY)
        Point chPoint = gf.createPoint(new Coordinate(baseX, baseY));
        chPoint.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point chWgs = (Point) transformService.toWgs84(chPoint);
        HeatChamberEntity chamber = HeatChamberEntity.builder().build();
        chamber.setUploadId(uploadId);
        chamber.setFeatureId("chamber-obs-1");
        chamber.setGeometry(chWgs);
        chamber.setGeometryUtm(chPoint);
        chamber.setProperties(mapper.createObjectNode());
        heatChamberRepo.save(chamber);

        LineString netLine = gf.createLineString(new Coordinate[]{
                new Coordinate(baseX - 50.0, baseY),
                new Coordinate(baseX + 50.0, baseY)
        });
        netLine.setSRID(CoordinateTransformService.SRID_UTM_37N);
        LineString netWgs = (LineString) transformService.toWgs84(netLine);
        HeatNetworkEntity network = HeatNetworkEntity.builder()
                .diameter(200)
                .build();
        network.setUploadId(uploadId);
        network.setFeatureId("net-obs-1");
        network.setGeometry(netWgs);
        network.setGeometryUtm(netLine);
        network.setProperties(mapper.createObjectNode());
        heatNetworkRepo.save(network);

        // 4. Запретное препятствие (prohibited_site) строго между ОКС и камерой:
        //    x от baseX-20 до baseX+20, y от baseY+40 до baseY+60
        savePolygon(uploadId, "obs-poly-1", "prohibited_site",
                baseX - 20, baseY + 40,
                baseX + 20, baseY + 60);

        // 5. Запуск сессии трассировки и async-обработки
        TraceAcceptedResponse response = traceService.createTraceSession(uploadId);
        UUID traceId = response.getTraceId();

        traceAsyncProcessor.process(traceId, uploadId);
        long start = System.currentTimeMillis();
        while (traceService.getTraceStatus(traceId).getStatus() != TraceStatus.COMPLETED
                && System.currentTimeMillis() - start < 15_000) {
            Thread.sleep(100);
        }
        assertThat(traceService.getTraceStatus(traceId).getStatus())
                .isEqualTo(TraceStatus.COMPLETED);

        // 6. Получаем результат через API вариантов
        ru.moscow.heat.trace.dto.TraceResult variantsResult =
                traceService.getVariantsTraceResult(traceId);
        assertThat(variantsResult).isNotNull();
        assertThat(variantsResult.getVariants()).isNotEmpty();

        ru.moscow.heat.trace.dto.VariantResult v1 =
                variantsResult.getVariants().get(0);
        assertThat(v1.getSegments()).isNotEmpty();

        double totalLength = v1.getSegments().stream()
                .mapToDouble(ru.moscow.heat.trace.model.RouteSegment::getLengthM)
                .sum();
        assertThat(totalLength).isGreaterThan(100.5);

        for (ru.moscow.heat.trace.model.RouteSegment seg : v1.getSegments()) {
            assertThat(seg.getCost()).isNotNull();
            assertThat(seg.getCost()).isGreaterThan(BigDecimal.ZERO);
        }
    }

    private void savePolygon(UUID uid, String featureId, String restrictionType,
                             double minX, double minY, double maxX, double maxY) {
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
                .restrictionType(restrictionType)
                .build();
        entity.setUploadId(uid);
        entity.setFeatureId(featureId);
        entity.setGeometry(polyWgs);
        entity.setGeometryUtm(polyUtm);
        entity.setProperties(mapper.createObjectNode());
        restrictionRepo.save(entity);
    }
}
