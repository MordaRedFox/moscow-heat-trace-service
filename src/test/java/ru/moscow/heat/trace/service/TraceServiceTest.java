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

/**
 * Модульные тесты сервиса {@link TraceService}
 * <p>Используется {@link MockitoExtension}:
 * {@link UploadSessionRepository} подменяется моком. Сессии
 * трассировки хранятся в памяти самого сервиса, поэтому
 * {@code TraceService} создается через конструктор с моком
 * репозитория
 * <p>Проверяются:
 * <ul>
 *   <li>успешное создание сессии для существующей загрузки,
 *       включая регистрацию в хранилище и корректный URL
 *       статуса;</li>
 *   <li>отказ с {@link UploadNotFoundException} для
 *       несуществующей загрузки;</li>
 *   <li>отказ с {@link TraceNotFoundException} при опросе
 *       статуса неизвестной задачи.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Модульные тесты сервиса трассировки (TraceService)")
class TraceServiceTest {

    @Mock
    private UploadSessionRepository uploadSessionRepository;

    private TraceService traceService;

    /**
     * Создает сервис с моком репозитория перед каждым тестом
     */
    @BeforeEach
    void setUp() {
        traceService = new TraceService(uploadSessionRepository);
    }

    /**
     * Создание сессии для существующей загрузки проходит
     * успешно: возвращается непустой идентификатор задачи,
     * URL статуса формируется по идентификатору, а сама
     * сессия доступна через {@code getTraceStatus} со статусом
     * {@code NOT_IMPLEMENTED}
     */
    @Test
    @DisplayName("Создание сессии для существующей загрузки")
    void shouldCreateTraceSessionWhenUploadExists() {
        UUID uploadId = UUID.randomUUID();
        when(uploadSessionRepository.existsById(uploadId))
                .thenReturn(true);

        TraceAcceptedResponse response =
                traceService.createTraceSession(uploadId);

        assertThat(response.getTraceId()).isNotNull();
        assertThat(response.getStatusUrl())
                .isEqualTo("/api/trace/" + response.getTraceId());

        TraceStatusResponse status = traceService
                .getTraceStatus(response.getTraceId());
        assertThat(status.getTraceId())
                .isEqualTo(response.getTraceId());
        assertThat(status.getStatus())
                .isEqualTo(TraceStatus.NOT_IMPLEMENTED);
        assertThat(status.getCreatedAt()).isNotNull();
    }

    /**
     * Создание сессии для несуществующей загрузки приводит
     * к {@link UploadNotFoundException}: репозиторий сообщает,
     * что записи нет
     */
    @Test
    @DisplayName("Создание сессии для несуществующей загрузки")
    void shouldThrowWhenUploadNotFound() {
        UUID uploadId = UUID.randomUUID();
        when(uploadSessionRepository.existsById(uploadId))
                .thenReturn(false);

        assertThatThrownBy(
                () -> traceService.createTraceSession(uploadId))
                .isInstanceOf(UploadNotFoundException.class)
                .hasMessageContaining("не найдена");
    }

    /**
     * Опрос статуса для неизвестного {@code traceId} приводит
     * к {@link TraceNotFoundException}
     */
    @Test
    @DisplayName("Статус неизвестной задачи")
    void shouldThrowWhenTraceNotFound() {
        UUID unknownTraceId = UUID.randomUUID();

        assertThatThrownBy(
                () -> traceService.getTraceStatus(unknownTraceId))
                .isInstanceOf(TraceNotFoundException.class)
                .hasMessageContaining("не найдена");
    }
}
