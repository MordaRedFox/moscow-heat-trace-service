package ru.moscow.heat.trace.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.trace.dto.TraceAcceptedResponse;
import ru.moscow.heat.trace.dto.TraceStatus;
import ru.moscow.heat.trace.dto.TraceStatusResponse;
import ru.moscow.heat.trace.exception.TraceNotFoundException;
import ru.moscow.heat.trace.service.TraceService;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TraceController.class)
@DisplayName("WebMvc тестирование REST API трассировки (TraceController)")
class TraceControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TraceService traceService;

    @Test
    @DisplayName("POST /api/trace/{uploadId} при существующей загрузке возвращает 202 Accepted")
    void shouldReturn202WhenUploadExists() throws Exception {
        UUID uploadId = UUID.randomUUID();
        UUID traceId = UUID.randomUUID();
        String statusUrl = "/api/trace/" + traceId;

        when(traceService.createTraceSession(uploadId))
                .thenReturn(new TraceAcceptedResponse(traceId, statusUrl));

        mvc.perform(post("/api/trace/{uploadId}", uploadId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.traceId").value(traceId.toString()))
                .andExpect(jsonPath("$.statusUrl").value(statusUrl));
    }

    @Test
    @DisplayName("POST /api/trace/{uploadId} при неизвестной загрузке возвращает 404 Not Found")
    void shouldReturn404WhenUploadDoesNotExist() throws Exception {
        UUID uploadId = UUID.randomUUID();

        when(traceService.createTraceSession(uploadId))
                .thenThrow(new UploadNotFoundException("Сессия загрузки с id=" + uploadId + " не найдена"));

        mvc.perform(post("/api/trace/{uploadId}", uploadId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Сессия загрузки с id=" + uploadId + " не найдена"));
    }

    @Test
    @DisplayName("GET /api/trace/{traceId} при известной задаче возвращает 501 Not Implemented со статусом NOT_IMPLEMENTED")
    void shouldReturn501WhenTraceExists() throws Exception {
        UUID traceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-22T00:00:00Z");

        when(traceService.getTraceStatus(traceId))
                .thenReturn(new TraceStatusResponse(traceId, TraceStatus.NOT_IMPLEMENTED, now));

        mvc.perform(get("/api/trace/{traceId}", traceId))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.traceId").value(traceId.toString()))
                .andExpect(jsonPath("$.status").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.createdAt").value("2026-09-22T00:00:00Z"));
    }

    @Test
    @DisplayName("GET /api/trace/{traceId} при неизвестной задаче возвращает 404 Not Found")
    void shouldReturn404WhenTraceDoesNotExist() throws Exception {
        UUID traceId = UUID.randomUUID();

        when(traceService.getTraceStatus(traceId))
                .thenThrow(new TraceNotFoundException(traceId));

        mvc.perform(get("/api/trace/{traceId}", traceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Задача трассировки с traceId=" + traceId + " не найдена"));
    }
}
