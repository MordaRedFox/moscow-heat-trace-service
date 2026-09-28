package ru.moscow.heat.trace.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ru.moscow.heat.geojson.service.CoordinateTransformService;
import ru.moscow.heat.trace.dto.TraceResult;
import ru.moscow.heat.trace.dto.VariantResult;
import ru.moscow.heat.trace.dto.VariantSummary;
import ru.moscow.heat.trace.model.NewChamber;
import ru.moscow.heat.trace.model.RouteNode;
import ru.moscow.heat.trace.model.RouteSegment;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Сервис потокового экспорта результатов трассировки в формат GeoJSON.
 * Выполняет трансформацию координат из UTM zone 37N (EPSG:32637) в WGS 84 (EPSG:4326).
 * Записывает объекты: heat_network, heat_chamber, technical_node, variant_summary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceGeoJsonExporter {

    private final CoordinateTransformService coordinateTransformService;
    private final ObjectMapper objectMapper;

    /**
     * Создает StreamingResponseBody для потокового экспорта одного варианта трассировки.
     *
     * @param variant результат варианта трассировки
     * @return StreamingResponseBody
     */
    public StreamingResponseBody exportVariantStreaming(VariantResult variant) {
        return outputStream -> exportVariantToStream(variant, outputStream);
    }

    /**
     * Создает StreamingResponseBody для потокового экспорта всех вариантов трассировки.
     *
     * @param traceResult итоговый результат трассировки со всеми вариантами
     * @return StreamingResponseBody
     */
    public StreamingResponseBody exportAllVariantsStreaming(TraceResult traceResult) {
        return outputStream -> exportAllVariantsToStream(traceResult, outputStream);
    }

    /**
     * Потоковая запись одного варианта в выходной поток.
     */
    public void exportVariantToStream(VariantResult variant, OutputStream outputStream) throws IOException {
        JsonFactory factory = objectMapper.getFactory();
        try (JsonGenerator gen = factory.createGenerator(outputStream)) {
            gen.writeStartObject();
            gen.writeStringField("type", "FeatureCollection");
            gen.writeArrayFieldStart("features");

            if (variant != null) {
                writeVariantFeatures(variant, gen);
            }

            gen.writeEndArray();
            gen.writeEndObject();
            gen.flush();
        }
    }

    /**
     * Потоковая запись всех вариантов в единый FeatureCollection.
     */
    public void exportAllVariantsToStream(TraceResult traceResult, OutputStream outputStream) throws IOException {
        JsonFactory factory = objectMapper.getFactory();
        try (JsonGenerator gen = factory.createGenerator(outputStream)) {
            gen.writeStartObject();
            gen.writeStringField("type", "FeatureCollection");
            gen.writeArrayFieldStart("features");

            if (traceResult != null && traceResult.getVariants() != null) {
                for (VariantResult variant : traceResult.getVariants()) {
                    writeVariantFeatures(variant, gen);
                }
            }

            gen.writeEndArray();
            gen.writeEndObject();
            gen.flush();
        }
    }

    private void writeVariantFeatures(VariantResult variant, JsonGenerator gen) throws IOException {
        Set<String> writtenNodeIds = new HashSet<>();

        // 1. Линейные участки сети (heat_network)
        if (variant.getSegments() != null) {
            for (RouteSegment segment : variant.getSegments()) {
                writeNetworkSegmentFeature(segment, variant.getVariantId(), gen);
                collectAndWriteNodes(segment, variant.getVariantId(), writtenNodeIds, gen);
            }
        }

        // 2. Новые тепловые камеры (heat_chamber)
        if (variant.getChambers() != null) {
            for (NewChamber chamber : variant.getChambers()) {
                writeChamberFeature(chamber, variant.getVariantId(), gen);
            }
        }

        // 3. Сводка варианта (variant_summary с null-геометрией)
        if (variant.getSummary() != null) {
            writeSummaryFeature(variant.getSummary(), gen);
        }
    }

    private void writeNetworkSegmentFeature(
            RouteSegment segment, String variantId, JsonGenerator gen) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("type", "Feature");
        writeParsedId(gen, "id", segment.getId());

        // Геометрия LineString в WGS84
        LineString wgs84Line = (LineString) coordinateTransformService.toWgs84(segment.getGeometry());
        gen.writeObjectFieldStart("geometry");
        gen.writeStringField("type", "LineString");
        gen.writeArrayFieldStart("coordinates");
        for (Coordinate coord : wgs84Line.getCoordinates()) {
            gen.writeStartArray();
            gen.writeNumber(coord.getX());
            gen.writeNumber(coord.getY());
            gen.writeEndArray();
        }
        gen.writeEndArray(); // coordinates
        gen.writeEndObject(); // geometry

        // Свойства
        gen.writeObjectFieldStart("properties");
        gen.writeStringField("object_type", "heat_network");
        gen.writeStringField("variant_id", variantId);
        writeNodeRef(gen, "start_node_id", segment.getFromNode());
        writeNodeRef(gen, "end_node_id", segment.getToNode());
        writeNodeRef(gen, "from_node", segment.getFromNode());
        writeNodeRef(gen, "to_node", segment.getToNode());
        gen.writeNumberField("flow_tph", segment.getFlowTph());
        gen.writeNumberField("flow", segment.getFlowTph());
        gen.writeNumberField("diameter", segment.getDiameterMm());
        gen.writeNumberField("length", segment.getLengthM());
        gen.writeStringField("laying_method", segment.getKspets() > 1.0 ? "special" : "base");
        gen.writeNullField("depth_start");
        gen.writeNullField("depth_end");
        if (segment.getCost() != null) {
            gen.writeNumberField("cost", segment.getCost());
        }
        gen.writeEndObject(); // properties

        gen.writeEndObject(); // feature
    }

    private void writeChamberFeature(
            NewChamber chamber, String variantId, JsonGenerator gen) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("type", "Feature");
        writeParsedId(gen, "id", chamber.getId());

        Point wgs84Point = (Point) coordinateTransformService.toWgs84(chamber.getGeometry());
        writePointGeometry(gen, wgs84Point);

        gen.writeObjectFieldStart("properties");
        gen.writeStringField("object_type", "heat_chamber");
        gen.writeStringField("variant_id", variantId);
        gen.writeNumberField("diameter", chamber.getDiameterMm());
        if (chamber.getCost() != null) {
            gen.writeNumberField("cost", chamber.getCost());
        }
        gen.writeEndObject();

        gen.writeEndObject();
    }

    private void collectAndWriteNodes(
            RouteSegment segment, String variantId, Set<String> writtenIds, JsonGenerator gen) throws IOException {
        writeNodeIfAbsent(segment.getFromNode(), variantId, writtenIds, gen);
        writeNodeIfAbsent(segment.getToNode(), variantId, writtenIds, gen);
    }

    private void writeNodeIfAbsent(
            RouteNode node, String variantId, Set<String> writtenIds, JsonGenerator gen) throws IOException {
        if (node == null) {
            return;
        }
        String effectiveId = getEffectiveNodeId(node);
        if (!writtenIds.add(effectiveId)) {
            return; // Узел уже записан
        }

        // Если узел является новой камерой, он будет записан из списка камер
        if ("new_chamber".equals(node.getNodeType())) {
            return;
        }

        gen.writeStartObject();
        gen.writeStringField("type", "Feature");
        writeParsedId(gen, "id", effectiveId);

        Point wgs84Point = (Point) coordinateTransformService.toWgs84(node.getPoint());
        writePointGeometry(gen, wgs84Point);

        gen.writeObjectFieldStart("properties");
        gen.writeStringField("variant_id", variantId);
        if ("existing_chamber".equals(node.getNodeType())) {
            gen.writeStringField("object_type", "heat_chamber");
        } else {
            gen.writeStringField("object_type", "technical_node");
            gen.writeStringField("node_type", node.getNodeType());
        }
        gen.writeEndObject();

        gen.writeEndObject();
    }

    private void writeSummaryFeature(VariantSummary summary, JsonGenerator gen) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("type", "Feature");
        gen.writeStringField("id", summary.getVariantId() + "-summary");
        gen.writeNullField("geometry");

        gen.writeObjectFieldStart("properties");
        gen.writeStringField("object_type", "variant_summary");
        gen.writeStringField("variant_id", summary.getVariantId());
        if (summary.getRank() != null) {
            gen.writeNumberField("rank", summary.getRank());
        }
        gen.writeNumberField("construction_cost", summary.getConstructionCost());
        gen.writeNumberField("chamber_construction_cost", summary.getChamberConstructionCost());
        gen.writeNumberField("existing_chamber_tie_in_count", summary.getExistingChamberTieInCount());
        gen.writeNumberField("existing_chamber_tie_in_cost", summary.getExistingChamberTieInCost());
        gen.writeNumberField("unconnected_penalty", summary.getUnconnectedPenalty());
        gen.writeNumberField("calculated_cost", summary.getCalculatedCost());
        gen.writeNumberField("new_network_length", summary.getNewNetworkLength());
        gen.writeNumberField("score", summary.getScore());

        gen.writeArrayFieldStart("unconnected_oks_ids");
        if (summary.getUnconnectedOksIds() != null) {
            for (String oksId : summary.getUnconnectedOksIds()) {
                writeParsedArrayValue(gen, oksId);
            }
        }
        gen.writeEndArray();

        gen.writeEndObject(); // properties
        gen.writeEndObject(); // feature
    }

    private void writePointGeometry(JsonGenerator gen, Point point) throws IOException {
        gen.writeObjectFieldStart("geometry");
        gen.writeStringField("type", "Point");
        gen.writeArrayFieldStart("coordinates");
        gen.writeNumber(point.getX());
        gen.writeNumber(point.getY());
        gen.writeEndArray();
        gen.writeEndObject();
    }

    private void writeNodeRef(JsonGenerator gen, String fieldName, RouteNode node) throws IOException {
        if (node == null) {
            return;
        }
        String effectiveId = getEffectiveNodeId(node);
        writeParsedId(gen, fieldName, effectiveId);
    }

    private String getEffectiveNodeId(RouteNode node) {
        if (node.getSourceFeatureId() != null && !node.getSourceFeatureId().isBlank()) {
            return node.getSourceFeatureId();
        }
        if (node.getId() != null && !node.getId().isBlank()) {
            return node.getId();
        }
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * Записывает идентификатор в JSON. Если строка парсится как целое число (Long) —
     * записывается как number, иначе как string.
     */
    private void writeParsedId(JsonGenerator gen, String fieldName, String rawId) throws IOException {
        if (rawId == null) {
            return;
        }
        try {
            long numericValue = Long.parseLong(rawId);
            gen.writeNumberField(fieldName, numericValue);
        } catch (NumberFormatException e) {
            gen.writeStringField(fieldName, rawId);
        }
    }

    private void writeParsedArrayValue(JsonGenerator gen, String rawId) throws IOException {
        if (rawId == null) {
            return;
        }
        try {
            long numericValue = Long.parseLong(rawId);
            gen.writeNumber(numericValue);
        } catch (NumberFormatException e) {
            gen.writeString(rawId);
        }
    }
}
