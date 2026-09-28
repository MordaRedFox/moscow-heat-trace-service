package ru.moscow.heat.trace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ru.moscow.heat.geojson.service.CoordinateTransformService;
import ru.moscow.heat.trace.dto.ExistingChamberTieIn;
import ru.moscow.heat.trace.dto.TraceResult;
import ru.moscow.heat.trace.dto.VariantResult;
import ru.moscow.heat.trace.dto.VariantSummary;
import ru.moscow.heat.trace.model.NewChamber;
import ru.moscow.heat.trace.model.RouteNode;
import ru.moscow.heat.trace.model.RouteSegment;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Интеграционный тест потокового экспорта GeoJSON")
class TraceGeoJsonExporterIntegrationTest {

    private final CoordinateTransformService transformService = new CoordinateTransformService();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TraceGeoJsonExporter exporter = new TraceGeoJsonExporter(transformService, objectMapper);
    private final GeometryFactory gf = new GeometryFactory();

    @Test
    @DisplayName("StreamingResponseBody отдает валидный GeoJSON с корректными типами полей (числа vs строки) и null-геометрией")
    void testStreamingGeoJsonExportWithCorrectTypes() throws Exception {
        // Координаты в UTM 37N (Москва ~ 410000, 6180000)
        Point pUtm1 = gf.createPoint(new Coordinate(414000.0, 6180000.0));
        Point pUtm2 = gf.createPoint(new Coordinate(414100.0, 6180000.0));

        // Узел с числовым ID (например 1001)
        RouteNode numericNode = new RouteNode("auto-id-1", "1001", pUtm1, "technical_node");
        // Узел со строковым ID (например oks-alpha)
        RouteNode stringNode = new RouteNode("auto-id-2", "oks-alpha", pUtm2, "oks");

        LineString utmLine = gf.createLineString(new Coordinate[]{
                pUtm1.getCoordinate(), pUtm2.getCoordinate()
        });

        RouteSegment segment = new RouteSegment(
                "12345", // Числовой ID сегмента
                numericNode,
                stringNode,
                utmLine,
                100.0,
                150,
                25.0,
                1.0,
                1.0,
                BigDecimal.valueOf(10_550_700L)
        );

        NewChamber chamber = new NewChamber(
                "ch-beta", // Строковый ID камеры
                pUtm2,
                150,
                BigDecimal.valueOf(3_000_000L)
        );

        ExistingChamberTieIn tieIn = new ExistingChamberTieIn("1001", List.of("12345"));

        VariantSummary summary = new VariantSummary(
                "v1",
                1,
                BigDecimal.valueOf(18_550_700L),
                BigDecimal.valueOf(3_000_000L),
                1,
                BigDecimal.valueOf(5_000_000L),
                BigDecimal.ZERO,
                BigDecimal.valueOf(18_550_700L),
                100.0,
                0.8194,
                List.of("1001", "oks-alpha")
        );

        VariantResult variant = new VariantResult(
                "v1",
                List.of(segment),
                List.of(chamber),
                List.of(tieIn),
                List.of(),
                summary
        );

        StreamingResponseBody body = exporter.exportVariantStreaming(variant);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        body.writeTo(baos);

        byte[] jsonBytes = baos.toByteArray();
        assertThat(jsonBytes).isNotEmpty();

        JsonNode root = objectMapper.readTree(jsonBytes);
        assertThat(root.get("type").asText()).isEqualTo("FeatureCollection");
        JsonNode features = root.get("features");
        assertThat(features.isArray()).isTrue();
        assertThat(features.size()).isGreaterThanOrEqualTo(4);

        // 1. Проверяем сегмент heat_network
        JsonNode networkFeature = findFeatureByObjectType(features, "heat_network");
        assertThat(networkFeature).isNotNull();
        // ID сегмента "12345" должно быть числом в JSON!
        assertThat(networkFeature.get("id").isNumber()).isTrue();
        assertThat(networkFeature.get("id").asLong()).isEqualTo(12345L);

        JsonNode netProps = networkFeature.get("properties");
        // start_node_id и from_node с sourceFeatureId="1001" должны быть числами
        assertThat(netProps.get("start_node_id").isNumber()).isTrue();
        assertThat(netProps.get("start_node_id").asLong()).isEqualTo(1001L);
        assertThat(netProps.get("from_node").isNumber()).isTrue();
        assertThat(netProps.get("from_node").asLong()).isEqualTo(1001L);

        // end_node_id и to_node с sourceFeatureId="oks-alpha" должны быть строками
        assertThat(netProps.get("end_node_id").isTextual()).isTrue();
        assertThat(netProps.get("end_node_id").asText()).isEqualTo("oks-alpha");
        assertThat(netProps.get("to_node").isTextual()).isTrue();
        assertThat(netProps.get("to_node").asText()).isEqualTo("oks-alpha");

        // flow_tph, laying_method, depth_start, depth_end
        assertThat(netProps.get("flow_tph").asDouble()).isEqualTo(25.0);
        assertThat(netProps.get("laying_method").asText()).isEqualTo("base");
        assertThat(netProps.get("depth_start").isNull()).isTrue();
        assertThat(netProps.get("depth_end").isNull()).isTrue();

        // Геометрия LineString трансформирована в WGS84 (lon в районе ~37 град, lat в районе ~55 град)
        JsonNode netGeom = networkFeature.get("geometry");
        assertThat(netGeom.get("type").asText()).isEqualTo("LineString");
        JsonNode coords = netGeom.get("coordinates");
        double lon0 = coords.get(0).get(0).asDouble();
        double lat0 = coords.get(0).get(1).asDouble();
        assertThat(lon0).isBetween(36.0, 39.0);
        assertThat(lat0).isBetween(54.0, 57.0);

        // 2. Проверяем узел technical_node с числовым id 1001
        JsonNode techNodeFeature = findFeatureByObjectType(features, "technical_node");
        assertThat(techNodeFeature).isNotNull();
        assertThat(techNodeFeature.get("id").isNumber()).isTrue();
        assertThat(techNodeFeature.get("id").asLong()).isEqualTo(1001L);

        // 3. Проверяем камеру heat_chamber со строковым id "ch-beta"
        JsonNode chamberFeature = findFeatureByObjectType(features, "heat_chamber");
        assertThat(chamberFeature).isNotNull();
        assertThat(chamberFeature.get("id").isTextual()).isTrue();
        assertThat(chamberFeature.get("id").asText()).isEqualTo("ch-beta");

        // 4. Проверяем variant_summary: geometry должно быть строго null!
        JsonNode summaryFeature = findFeatureByObjectType(features, "variant_summary");
        assertThat(summaryFeature).isNotNull();
        assertThat(summaryFeature.get("geometry").isNull()).isTrue();
        JsonNode summaryProps = summaryFeature.get("properties");
        assertThat(summaryProps.get("rank").asInt()).isEqualTo(1);
        assertThat(summaryProps.get("construction_cost").asDouble()).isEqualTo(18_550_700.0);
        assertThat(summaryProps.get("calculated_cost").asDouble()).isEqualTo(18_550_700.0);
        assertThat(summaryProps.get("new_network_length").asDouble()).isEqualTo(100.0);

        // Проверяем типы элементов в unconnected_oks_ids: число сохраняется числом, строка строкой
        JsonNode unconnectedArray = summaryProps.get("unconnected_oks_ids");
        assertThat(unconnectedArray.isArray()).isTrue();
        assertThat(unconnectedArray.get(0).isNumber()).isTrue();
        assertThat(unconnectedArray.get(0).asLong()).isEqualTo(1001L);
        assertThat(unconnectedArray.get(1).isTextual()).isTrue();
        assertThat(unconnectedArray.get(1).asText()).isEqualTo("oks-alpha");

        // Проверяем, что variant_id присутствует у всех объектов
        for (JsonNode f : features) {
            assertThat(f.get("properties").has("variant_id")).isTrue();
            assertThat(f.get("properties").get("variant_id").asText()).isEqualTo("v1");
        }
    }

    @Test
    @DisplayName("Потоковый экспорт всех вариантов TraceResult")
    void testExportAllVariantsStreaming() throws Exception {
        VariantSummary summary = new VariantSummary(
                "v1", 1, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, 0.0, List.of()
        );
        VariantResult variant = new VariantResult("v1", List.of(), List.of(), List.of(), List.of(), summary);
        TraceResult traceResult = new TraceResult(UUID.randomUUID(), UUID.randomUUID(), List.of(variant), List.of());

        StreamingResponseBody body = exporter.exportAllVariantsStreaming(traceResult);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        body.writeTo(baos);

        JsonNode root = objectMapper.readTree(baos.toByteArray());
        assertThat(root.get("type").asText()).isEqualTo("FeatureCollection");
        assertThat(root.get("features").isArray()).isTrue();
    }

    private JsonNode findFeatureByObjectType(JsonNode features, String objectType) {
        for (JsonNode f : features) {
            JsonNode props = f.get("properties");
            if (props != null && props.has("object_type") && objectType.equals(props.get("object_type").asText())) {
                return f;
            }
        }
        return null;
    }
}
