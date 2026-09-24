package ru.moscow.heat.trace.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TieInType;
import ru.moscow.heat.trace.dto.TraceAcceptedResponse;
import ru.moscow.heat.trace.dto.TraceStatus;
import ru.moscow.heat.trace.dto.TraceStatusResponse;
import ru.moscow.heat.trace.exception.TraceNotFoundException;
import ru.moscow.heat.trace.service.TraceService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvc-тесты контроллера {@link TraceController}.
 * Используется срез {@link WebMvcTest}: поднимается только веб-слой,
 * {@link TraceService} подменяется моком
 */
@WebMvcTest(TraceController.class)
@DisplayName("WebMvc-тесты TraceController")
class TraceControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TraceService traceService;

    @Test
    @DisplayName("POST существующей загрузки -> 202")
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
    @DisplayName("POST неизвестной загрузки -> 404")
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
    @DisplayName("GET статуса известной задачи -> 501")
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
    @DisplayName("GET статуса неизвестной задачи -> 404")
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
    @DisplayName("GET /candidates -> 200 и список")
    void shouldReturnCandidatesWhenTraceExists() throws Exception {
        UUID traceId = UUID.randomUUID();
        TieInCandidate candidate = TieInCandidate.builder()
                .connectionPointId("oks-point-1")
                .heatNetworkId("net-section-1")
                .type(TieInType.EXISTING_CHAMBER)
                .existingChamberId("chamber-10")
                .tieInLongitude(37.6175)
                .tieInLatitude(55.7522)
                .distanceToNetworkM(3.5)
                .distanceToChamberM(2.45)
                .currentAttachments(2)
                .cost(5_000_000L)
                .build();

        when(traceService.getCandidates(traceId))
                .thenReturn(List.of(candidate));

        mvc.perform(get("/api/trace/{traceId}/candidates", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].connectionPointId")
                        .value("oks-point-1"))
                .andExpect(jsonPath("$[0].heatNetworkId")
                        .value("net-section-1"))
                .andExpect(jsonPath("$[0].type")
                        .value("EXISTING_CHAMBER"))
                .andExpect(jsonPath("$[0].existingChamberId")
                        .value("chamber-10"))
                .andExpect(jsonPath("$[0].tieInLongitude")
                        .value(37.6175))
                .andExpect(jsonPath("$[0].tieInLatitude")
                        .value(55.7522))
                .andExpect(jsonPath("$[0].distanceToChamberM")
                        .value(2.45))
                .andExpect(jsonPath("$[0].currentAttachments")
                        .value(2))
                .andExpect(jsonPath("$[0].cost")
                        .value(5_000_000L));
    }

    @Test
    @DisplayName("GET /candidates неизвестной задачи -> 404")
    void shouldReturn404WhenGettingCandidatesForUnknownTrace()
            throws Exception {
        UUID traceId = UUID.randomUUID();

        when(traceService.getCandidates(traceId))
                .thenThrow(new TraceNotFoundException(traceId));

        mvc.perform(get("/api/trace/{traceId}/candidates", traceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("Задача трассировки с traceId="
                                + traceId + " не найдена"));
    }
}
