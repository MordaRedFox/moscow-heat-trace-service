package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;
import ru.moscow.heat.trace.dto.TraceAcceptedResponse;
import ru.moscow.heat.trace.dto.TraceStatus;
import ru.moscow.heat.trace.dto.TraceStatusResponse;
import ru.moscow.heat.trace.exception.TraceNotFoundException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Модульное тестирование сервиса трассировки (TraceService)")
class TraceServiceTest {

    @Mock
    private UploadSessionRepository uploadSessionRepository;

    private TraceService traceService;

    @BeforeEach
    void setUp() {
        traceService = new TraceService(uploadSessionRepository);
    }

    @Test
    @DisplayName("Успешное создание сессии трассировки для существующей загрузки")
    void shouldCreateTraceSessionWhenUploadExists() {
        UUID uploadId = UUID.randomUUID();
        when(uploadSessionRepository.existsById(uploadId)).thenReturn(true);

        TraceAcceptedResponse response = traceService.createTraceSession(uploadId);

        assertThat(response.getTraceId()).isNotNull();
        assertThat(response.getStatusUrl()).isEqualTo("/api/trace/" + response.getTraceId());

        TraceStatusResponse status = traceService.getTraceStatus(response.getTraceId());
        assertThat(status.getTraceId()).isEqualTo(response.getTraceId());
        assertThat(status.getStatus()).isEqualTo(TraceStatus.NOT_IMPLEMENTED);
        assertThat(status.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Создание сессии для несуществующей загрузки выбрасывает UploadNotFoundException")
    void shouldThrowWhenUploadNotFound() {
        UUID uploadId = UUID.randomUUID();
        when(uploadSessionRepository.existsById(uploadId)).thenReturn(false);

        assertThatThrownBy(() -> traceService.createTraceSession(uploadId))
                .isInstanceOf(UploadNotFoundException.class)
                .hasMessageContaining("не найдена");
    }

    @Test
    @DisplayName("Запрос статуса для неизвестного traceId выбрасывает TraceNotFoundException")
    void shouldThrowWhenTraceNotFound() {
        UUID unknownTraceId = UUID.randomUUID();

        assertThatThrownBy(() -> traceService.getTraceStatus(unknownTraceId))
                .isInstanceOf(TraceNotFoundException.class)
                .hasMessageContaining("не найдена");
    }
}
