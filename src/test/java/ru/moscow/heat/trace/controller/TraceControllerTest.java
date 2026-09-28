package ru.moscow.heat.trace.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.trace.dto.*;
import ru.moscow.heat.trace.exception.TraceNotFoundException;
import ru.moscow.heat.trace.exception.VariantNotFoundException;
import ru.moscow.heat.trace.service.TraceService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WebMvc-тесты REST-контроллера {@link TraceController}
 */
@WebMvcTest(TraceController.class)
@DisplayName("WebMvc-тесты REST API трассировки (TraceController)")
class TraceControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TraceService traceService;

    private final GeometryFactory gf = new GeometryFactory();

    @Test
    @DisplayName("POST при существующей загрузке возвращает 202")
    void shouldReturn202WhenUploadExists() throws Exception {
        UUID uploadId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        String statusUrl = "/api/trace/" + traceId;

        when(traceService.createTraceSession(uploadId))
                .thenReturn(new TraceAcceptedResponse(
                        traceId, statusUrl));

        mvc.perform(post("/api/trace/{uploadId}", uploadId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.traceId")
                        .value(traceId.toString()))
                .andExpect(jsonPath("$.statusUrl")
                        .value(statusUrl));
    }

    @Test
    @DisplayName("POST при неизвестной загрузке возвращает 404")
    void shouldReturn404WhenUploadDoesNotExist() throws Exception {
        UUID uploadId = UUID.randomUUID();
        String message = "Сессия загрузки с id="
                + uploadId + " не найдена";

        when(traceService.createTraceSession(uploadId))
                .thenThrow(new UploadNotFoundException(message));

        mvc.perform(post("/api/trace/{uploadId}", uploadId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(message));
    }

    @Test
    @DisplayName("GET известной задачи возвращает 501 для статуса NOT_IMPLEMENTED")
    void shouldReturn501WhenTraceExists() throws Exception {
        UUID traceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-22T00:00:00Z");

        when(traceService.getTraceStatus(traceId))
                .thenReturn(new TraceStatusResponse(
                        traceId, TraceStatus.NOT_IMPLEMENTED, now));

        mvc.perform(get("/api/trace/{traceId}", traceId))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.traceId")
                        .value(traceId.toString()))
                .andExpect(jsonPath("$.status")
                        .value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.createdAt")
                        .value("2026-09-22T00:00:00Z"));
    }

    @Test
    @DisplayName("GET известной выполненной задачи возвращает 200 OK")
    void shouldReturn200WhenTraceCompleted() throws Exception {
        UUID traceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-28T12:00:00Z");

        when(traceService.getTraceStatus(traceId))
                .thenReturn(new TraceStatusResponse(
                        traceId, TraceStatus.COMPLETED, now));

        mvc.perform(get("/api/trace/{traceId}", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value(traceId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET неизвестной задачи возвращает 404")
    void shouldReturn404WhenTraceDoesNotExist() throws Exception {
        UUID traceId = UUID.randomUUID();

        when(traceService.getTraceStatus(traceId))
                .thenThrow(new TraceNotFoundException(traceId));

        mvc.perform(get("/api/trace/{traceId}", traceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("Задача трассировки с traceId="
                                + traceId + " не найдена"));
    }

    @Test
    @DisplayName("GET /candidates известной задачи возвращает 200 и список кандидатов")
    void shouldReturnCandidatesWhenTraceExists() throws Exception {
        UUID traceId = UUID.randomUUID();
        TieInCandidate candidate = TieInCandidate.builder()
                .connectionPointId("oks-point-1")
                .heatNetworkId("net-section-1")
                .tieInType(TieInType.EXISTING_CHAMBER)
                .existingChamberId("chamber-10")
                .tieInPoint(gf.createPoint(new Coordinate(37.6175, 55.7522)))
                .distanceToChamberM(2.45)
                .currentChamberConnections(2)
                .cost(5_000_000.0)
                .build();

        when(traceService.getCandidates(traceId))
                .thenReturn(List.of(candidate));

        mvc.perform(get("/api/trace/{traceId}/candidates", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].connectionPointId").value("oks-point-1"))
                .andExpect(jsonPath("$[0].heatNetworkId").value("net-section-1"))
                .andExpect(jsonPath("$[0].tieInType").value("EXISTING_CHAMBER"))
                .andExpect(jsonPath("$[0].existingChamberId").value("chamber-10"))
                .andExpect(jsonPath("$[0].tieInPoint.type").value("Point"))
                .andExpect(jsonPath("$[0].tieInPoint.coordinates[0]").value(37.6175))
                .andExpect(jsonPath("$[0].tieInPoint.coordinates[1]").value(55.7522))
                .andExpect(jsonPath("$[0].distanceToChamberM").value(2.45))
                .andExpect(jsonPath("$[0].currentChamberConnections").value(2))
                .andExpect(jsonPath("$[0].cost").value(5_000_000.0));
    }

    @Test
    @DisplayName("GET /candidates неизвестной задачи возвращает 404")
    void shouldReturn404WhenGettingCandidatesForUnknownTrace() throws Exception {
        UUID traceId = UUID.randomUUID();

        when(traceService.getCandidates(traceId))
                .thenThrow(new TraceNotFoundException(traceId));

        mvc.perform(get("/api/trace/{traceId}/candidates", traceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("Задача трассировки с traceId="
                                + traceId + " не найдена"));
    }

    @Test
    @DisplayName("GET /variants возвращает список сводок вариантов трассировки")
    void shouldReturnVariantsList() throws Exception {
        UUID traceId = UUID.randomUUID();
        VariantSummary summary = new VariantSummary(
                "v1",
                1,
                BigDecimal.valueOf(10_000_000L),
                BigDecimal.valueOf(3_000_000L),
                1,
                BigDecimal.valueOf(5_000_000L),
                BigDecimal.ZERO,
                BigDecimal.valueOf(18_000_000L),
                150.0,
                0.954,
                List.of()
        );

        when(traceService.getVariants(traceId)).thenReturn(List.of(summary));

        mvc.perform(get("/api/trace/{traceId}/variants", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].variantId").value("v1"))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].constructionCost").value(10000000.0))
                .andExpect(jsonPath("$[0].chamberConstructionCost").value(3000000.0))
                .andExpect(jsonPath("$[0].existingChamberTieInCount").value(1))
                .andExpect(jsonPath("$[0].existingChamberTieInCost").value(5000000.0))
                .andExpect(jsonPath("$[0].calculatedCost").value(18000000.0))
                .andExpect(jsonPath("$[0].newNetworkLength").value(150.0))
                .andExpect(jsonPath("$[0].score").value(0.954));
    }

    @Test
    @DisplayName("GET /export?variantId=v1 возвращает StreamingResponseBody конкретного варианта")
    void shouldExportSingleVariantStreaming() throws Exception {
        UUID traceId = UUID.randomUUID();
        String fakeGeoJson = "{\"type\":\"FeatureCollection\",\"features\":[]}";
        StreamingResponseBody body = outputStream ->
                outputStream.write(fakeGeoJson.getBytes(StandardCharsets.UTF_8));

        when(traceService.exportTrace(traceId, "v1")).thenReturn(body);

        mvc.perform(get("/api/trace/{traceId}/export", traceId).param("variantId", "v1"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"trace-" + traceId + "-v1.geojson\""))
                .andExpect(content().contentType("application/geo+json;charset=UTF-8"));
    }

    @Test
    @DisplayName("GET /export без параметров возвращает StreamingResponseBody всех вариантов")
    void shouldExportAllVariantsStreaming() throws Exception {
        UUID traceId = UUID.randomUUID();
        String fakeGeoJson = "{\"type\":\"FeatureCollection\",\"features\":[]}";
        StreamingResponseBody body = outputStream ->
                outputStream.write(fakeGeoJson.getBytes(StandardCharsets.UTF_8));

        when(traceService.exportTrace(traceId, null)).thenReturn(body);

        mvc.perform(get("/api/trace/{traceId}/export", traceId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"trace-" + traceId + ".geojson\""))
                .andExpect(content().contentType("application/geo+json;charset=UTF-8"));
    }

    @Test
    @DisplayName("GET /export несуществующего варианта возвращает 404")
    void shouldReturn404WhenExportVariantNotFound() throws Exception {
        UUID traceId = UUID.randomUUID();
        when(traceService.exportTrace(traceId, "v99"))
                .thenThrow(new VariantNotFoundException("Вариант v99 не найден"));

        mvc.perform(get("/api/trace/{traceId}/export", traceId).param("variantId", "v99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Вариант v99 не найден"));
    }
}
