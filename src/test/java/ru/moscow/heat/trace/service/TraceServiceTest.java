package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import ru.moscow.heat.trace.model.TraceResult;
import ru.moscow.heat.trace.model.UnconnectedOks;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Модульные тесты сервиса {@link TraceService}.
 * Репозиторий сессий и сервис кандидатов подменяются моками
 * <p>
 * Для итерации 5 добавлены проверки новых переходов статуса:
 * {@link TraceService#markProcessing}, {@link TraceService#markCompleted},
 * {@link TraceService#markFailed}. Сессия создаётся сразу в {@code PENDING} -
 * заглушка {@code NOT_IMPLEMENTED} из итерации 3 больше не используется
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Модульные тесты TraceService")
class TraceServiceTest {

    @Mock
    private UploadSessionRepository uploadSessionRepository;

    @Mock
    private TieInCandidateService tieInCandidateService;

    private TraceService traceService;

    @BeforeEach
    void setUp() {
        traceService = new TraceService(
                uploadSessionRepository,
                tieInCandidateService);
    }

    @Test
    @DisplayName("Создание сессии: статус PENDING")
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
                .isEqualTo(TraceStatus.PENDING);
        assertThat(status.getCreatedAt()).isNotNull();
    }

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

    @Test
    @DisplayName("Статус неизвестной задачи")
    void shouldThrowWhenTraceNotFound() {
        UUID unknownTraceId = UUID.randomUUID();

        assertThatThrownBy(
                () -> traceService.getTraceStatus(unknownTraceId))
                .isInstanceOf(TraceNotFoundException.class)
                .hasMessageContaining("не найдена");
    }

    @Test
    @DisplayName("markProcessing переводит PENDING -> PROCESSING")
    void markProcessingSetsStatus() {
        UUID uploadId = UUID.randomUUID();
        when(uploadSessionRepository.existsById(uploadId))
                .thenReturn(true);
        UUID traceId = traceService.createTraceSession(uploadId)
                .getTraceId();

        traceService.markProcessing(traceId);

        TraceStatusResponse status = traceService.getTraceStatus(traceId);
        assertThat(status.getStatus()).isEqualTo(TraceStatus.PROCESSING);
        assertThat(status.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("markCompleted сохраняет счётчики и список неподключённых")
    void markCompletedSavesCountersAndUnconnected() {
        UUID uploadId = UUID.randomUUID();
        when(uploadSessionRepository.existsById(uploadId))
                .thenReturn(true);
        UUID traceId = traceService.createTraceSession(uploadId)
                .getTraceId();

        TraceResult.SummaryCounters counters =
                new TraceResult.SummaryCounters(5, 4, 1);
        UnconnectedOks unconnected = new UnconnectedOks(
                "oks-5", UnconnectedOks.Reason.NO_PATH_IN_GRAPH, "нет пути");
        TraceResult result = new TraceResult(
                List.of(), List.of(), List.of(),
                List.of(unconnected), counters);

        traceService.markCompleted(traceId, result);

        TraceStatusResponse status = traceService.getTraceStatus(traceId);
        assertThat(status.getStatus()).isEqualTo(TraceStatus.COMPLETED);
        assertThat(status.getTotalOksCount()).isEqualTo(5);
        assertThat(status.getConnectedCount()).isEqualTo(4);
        assertThat(status.getUnconnectedCount()).isEqualTo(1);
        assertThat(status.getUnconnectedOksFeatureIds())
                .containsExactly("oks-5");
        assertThat(status.getCompletedAt()).isNotNull();

        assertThat(traceService.getTraceResult(traceId)).isSameAs(result);
    }

    @Test
    @DisplayName("markFailed сохраняет сообщение об ошибке")
    void markFailedStoresMessage() {
        UUID uploadId = UUID.randomUUID();
        when(uploadSessionRepository.existsById(uploadId))
                .thenReturn(true);
        UUID traceId = traceService.createTraceSession(uploadId)
                .getTraceId();

        traceService.markFailed(traceId, "boom");

        TraceStatusResponse status = traceService.getTraceStatus(traceId);
        assertThat(status.getStatus()).isEqualTo(TraceStatus.FAILED);
        assertThat(status.getErrorMessage()).isEqualTo("boom");
        assertThat(status.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Получение кандидатов для существующей задачи")
    void shouldReturnCandidatesWhenTraceExists() {
        UUID uploadId = UUID.randomUUID();
        when(uploadSessionRepository.existsById(uploadId))
                .thenReturn(true);

        TieInCandidate candidate = TieInCandidate.builder()
                .connectionPointId("pt-1")
                .heatNetworkId("net-1")
                .type(TieInType.NEW_CHAMBER)
                .tieInLongitude(37.6)
                .tieInLatitude(55.7)
                .targetLongitude(37.6)
                .targetLatitude(55.7)
                .distanceToNetworkM(5.0)
                .distanceToChamberM(0.0)
                .currentAttachments(0)
                .cost(3_000_000L)
                .newChamberDiameter(200)
                .build();

        when(tieInCandidateService
                .findCandidatesForAllPoints(uploadId))
                .thenReturn(Map.of("pt-1", List.of(candidate)));

        TraceAcceptedResponse response =
                traceService.createTraceSession(uploadId);
        List<TieInCandidate> candidates = traceService
                .getCandidates(response.getTraceId());

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getConnectionPointId())
                .isEqualTo("pt-1");
        assertThat(candidates.get(0).getType())
                .isEqualTo(TieInType.NEW_CHAMBER);
        assertThat(candidates.get(0).getCost())
                .isEqualTo(3_000_000L);
    }

    @Test
    @DisplayName("Кандидаты для неизвестной задачи")
    void shouldThrowWhenGettingCandidatesForUnknownTrace() {
        UUID unknownTraceId = UUID.randomUUID();

        assertThatThrownBy(
                () -> traceService.getCandidates(unknownTraceId))
                .isInstanceOf(TraceNotFoundException.class)
                .hasMessageContaining("не найдена");
    }
}
