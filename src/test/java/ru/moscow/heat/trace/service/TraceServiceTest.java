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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Модульные тесты сервиса {@link TraceService}.
 * Репозиторий сессий и сервис кандидатов подменяются моками
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
