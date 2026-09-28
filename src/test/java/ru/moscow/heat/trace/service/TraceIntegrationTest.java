package ru.moscow.heat.trace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ru.moscow.heat.AbstractIntegrationTest;
import ru.moscow.heat.geojson.entity.HeatChamberEntity;
import ru.moscow.heat.geojson.entity.HeatNetworkEntity;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.entity.UploadSession;
import ru.moscow.heat.geojson.repository.HeatChamberRepository;
import ru.moscow.heat.geojson.repository.HeatNetworkRepository;
import ru.moscow.heat.geojson.repository.OksConnectionPointRepository;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;
import ru.moscow.heat.geojson.service.CoordinateTransformService;
import ru.moscow.heat.trace.dto.TraceAcceptedResponse;
import ru.moscow.heat.trace.dto.TraceStatus;
import ru.moscow.heat.trace.dto.TraceStatusResponse;
import ru.moscow.heat.trace.dto.VariantSummary;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Интеграционный smoke-тест полного цикла трассировки и экспорта в GeoJSON")
class TraceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TraceService traceService;

    @Autowired
    private UploadSessionRepository uploadSessionRepo;

    @Autowired
    private HeatNetworkRepository heatNetworkRepo;

    @Autowired
    private HeatChamberRepository heatChamberRepo;

    @Autowired
    private OksConnectionPointRepository oksCpRepo;

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

        // 4. Запускаем моделирование трассы
        TraceAcceptedResponse response = traceService.createTraceSession(uploadId);
        assertThat(response).isNotNull();
        UUID traceId = response.getTraceId();

        // 5. Проверяем статус (после создания задачи статус PENDING)
        TraceStatusResponse status = traceService.getTraceStatus(traceId);
        assertThat(status.getStatus()).isEqualTo(TraceStatus.PENDING);

        // 6. Получаем варианты трассировки (должен быть сформирован минимум 1 вариант)
        List<VariantSummary> variants = traceService.getVariants(traceId);
        assertThat(variants).isNotEmpty();
        VariantSummary topVariant = variants.get(0);
        assertThat(topVariant.getRank()).isEqualTo(1);
        assertThat(topVariant.getScore()).isGreaterThan(0.0);
        assertThat(topVariant.getNewNetworkLength()).isGreaterThan(0.0);
        assertThat(topVariant.getCalculatedCost()).isGreaterThan(java.math.BigDecimal.ZERO);

        // 7. Потоковый экспорт в GeoJSON
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

        // Проверяем наличие объектов heat_network, heat_chamber, variant_summary
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
}
