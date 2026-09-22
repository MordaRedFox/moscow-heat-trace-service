package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TieInType;
import ru.moscow.heat.trace.dto.TraceAcceptedResponse;
import ru.moscow.heat.trace.dto.TraceStatus;
import ru.moscow.heat.trace.dto.TraceStatusResponse;
import ru.moscow.heat.trace.exception.TraceNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Модульные тесты сервиса {@link TraceService}
 * <p>Используется {@link MockitoExtension}:
 * {@link UploadSessionRepository} и {@link TieInCandidateService} подменяются моками.
 * Сессии трассировки и кандидаты хранятся в памяти сервиса.
 * <p>Проверяются:
 * <ul>
 *   <li>успешное создание сессии для существующей загрузки,
 *       включая расчет кандидатов и корректный URL статуса;</li>
 *   <li>отказ с {@link UploadNotFoundException} для несуществующей загрузки;</li>
 *   <li>отказ с {@link TraceNotFoundException} при опросе статуса неизвестной задачи;</li>
 *   <li>получение списка кандидатов по идентификатору задачи трассировки;</li>
 *   <li>отказ с {@link TraceNotFoundException} при запросе кандидатов для неизвестной задачи.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Модульные тесты сервиса трассировки (TraceService)")
class TraceServiceTest {

    @Mock
    private UploadSessionRepository uploadSessionRepository;

    @Mock
    private TieInCandidateService tieInCandidateService;

    private TraceService traceService;
    private final GeometryFactory gf = new GeometryFactory();

    /**
     * Создает сервис с моками перед каждым тестом
     */
    @BeforeEach
    void setUp() {
        traceService = new TraceService(uploadSessionRepository, tieInCandidateService);
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
        when(tieInCandidateService.findCandidatesForAllPoints(uploadId))
                .thenReturn(Map.of());

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

    /**
     * Получение кандидатов для существующей задачи трассировки
     */
    @Test
    @DisplayName("Получение кандидатов для существующей задачи")
    void shouldReturnCandidatesWhenTraceExists() {
        UUID uploadId = UUID.randomUUID();
        when(uploadSessionRepository.existsById(uploadId)).thenReturn(true);

        TieInCandidate candidate = TieInCandidate.builder()
                .connectionPointId("pt-1")
                .heatNetworkId("net-1")
                .tieInType(TieInType.NEW_CHAMBER)
                .tieInPoint(gf.createPoint(new Coordinate(37.6, 55.7)))
                .distanceToChamberM(0.0)
                .cost(3_000_000.0)
                .requiredChamberDiameter(200)
                .build();

        when(tieInCandidateService.findCandidatesForAllPoints(uploadId))
                .thenReturn(Map.of("pt-1", List.of(candidate)));

        TraceAcceptedResponse response = traceService.createTraceSession(uploadId);
        List<TieInCandidate> candidates = traceService.getCandidates(response.getTraceId());

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getConnectionPointId()).isEqualTo("pt-1");
        assertThat(candidates.get(0).getTieInType()).isEqualTo(TieInType.NEW_CHAMBER);
        assertThat(candidates.get(0).getCost()).isEqualTo(3_000_000.0);
    }

    /**
     * Запрос кандидатов для неизвестного traceId приводит к {@link TraceNotFoundException}
     */
    @Test
    @DisplayName("Получение кандидатов для неизвестной задачи")
    void shouldThrowWhenGettingCandidatesForUnknownTrace() {
        UUID unknownTraceId = UUID.randomUUID();

        assertThatThrownBy(() -> traceService.getCandidates(unknownTraceId))
                .isInstanceOf(TraceNotFoundException.class)
                .hasMessageContaining("не найдена");
    }
}
