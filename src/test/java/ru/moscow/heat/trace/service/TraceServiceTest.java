package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;
import ru.moscow.heat.trace.dto.*;
import ru.moscow.heat.trace.exception.TraceNotFoundException;
import ru.moscow.heat.trace.exception.VariantNotFoundException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Модульные тесты сервиса TraceService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Модульные тесты сервиса трассировки (TraceService)")
class TraceServiceTest {

    @Mock
    private UploadSessionRepository uploadSessionRepository;

    @Mock
    private TieInCandidateService tieInCandidateService;

    @Mock
    private VariantGenerator variantGenerator;

    @Mock
    private TraceGeoJsonExporter traceGeoJsonExporter;

    private TraceService traceService;
    private final GeometryFactory gf = new GeometryFactory();

    @BeforeEach
    void setUp() {
        traceService = new TraceService(
                uploadSessionRepository,
                tieInCandidateService,
                variantGenerator,
                traceGeoJsonExporter
        );
    }

    @Test
    @DisplayName("Создание сессии для существующей загрузки")
    void shouldCreateTraceSessionWhenUploadExists() {
        UUID uploadId = UUID.randomUUID();
        when(uploadSessionRepository.existsById(uploadId)).thenReturn(true);
        when(tieInCandidateService.findCandidatesForAllPoints(uploadId)).thenReturn(Map.of());
        when(variantGenerator.generateTraceResult(eq(uploadId), any(UUID.class)))
                .thenAnswer(inv -> new TraceResult(inv.getArgument(1), uploadId, List.of(), List.of()));

        TraceAcceptedResponse response = traceService.createTraceSession(uploadId);

        assertThat(response.getTraceId()).isNotNull();
        assertThat(response.getStatusUrl()).isEqualTo("/api/trace/" + response.getTraceId());

        TraceStatusResponse status = traceService.getTraceStatus(response.getTraceId());
        assertThat(status.getTraceId()).isEqualTo(response.getTraceId());
        assertThat(status.getStatus()).isEqualTo(TraceStatus.COMPLETED);
        assertThat(status.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Создание сессии для несуществующей загрузки")
    void shouldThrowWhenUploadNotFound() {
        UUID uploadId = UUID.randomUUID();
        when(uploadSessionRepository.existsById(uploadId)).thenReturn(false);

        assertThatThrownBy(() -> traceService.createTraceSession(uploadId))
                .isInstanceOf(UploadNotFoundException.class)
                .hasMessageContaining("не найдена");
    }

    @Test
    @DisplayName("Статус неизвестной задачи")
    void shouldThrowWhenTraceNotFound() {
        UUID unknownTraceId = UUID.randomUUID();

        assertThatThrownBy(() -> traceService.getTraceStatus(unknownTraceId))
                .isInstanceOf(TraceNotFoundException.class)
                .hasMessageContaining("не найдена");
    }

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
        when(variantGenerator.generateTraceResult(eq(uploadId), any(UUID.class)))
                .thenAnswer(inv -> new TraceResult(inv.getArgument(1), uploadId, List.of(), List.of()));

        TraceAcceptedResponse response = traceService.createTraceSession(uploadId);
        List<TieInCandidate> candidates = traceService.getCandidates(response.getTraceId());

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getConnectionPointId()).isEqualTo("pt-1");
    }

    @Test
    @DisplayName("Получение списка вариантов для выполненной задачи")
    void shouldReturnVariantsWhenTraceExists() {
        UUID uploadId = UUID.randomUUID();
        when(uploadSessionRepository.existsById(uploadId)).thenReturn(true);

        VariantSummary summary = new VariantSummary(
                "v1", 1, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 50.0, 1.0, List.of()
        );
        VariantResult variant = new VariantResult("v1", List.of(), List.of(), List.of(), List.of(), summary);

        when(variantGenerator.generateTraceResult(eq(uploadId), any(UUID.class)))
                .thenAnswer(inv -> new TraceResult(inv.getArgument(1), uploadId, List.of(variant), List.of()));

        TraceAcceptedResponse response = traceService.createTraceSession(uploadId);
        List<VariantSummary> summaries = traceService.getVariants(response.getTraceId());

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).getVariantId()).isEqualTo("v1");
        assertThat(summaries.get(0).getRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("Потоковый экспорт конкретного варианта и всех вариантов")
    void shouldExportVariantsStreaming() {
        UUID uploadId = UUID.randomUUID();
        when(uploadSessionRepository.existsById(uploadId)).thenReturn(true);

        VariantSummary summary = new VariantSummary(
                "v1", 1, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 50.0, 1.0, List.of()
        );
        VariantResult variant = new VariantResult("v1", List.of(), List.of(), List.of(), List.of(), summary);

        when(variantGenerator.generateTraceResult(eq(uploadId), any(UUID.class)))
                .thenAnswer(inv -> new TraceResult(inv.getArgument(1), uploadId, List.of(variant), List.of()));
        StreamingResponseBody mockBody = out -> {};
        when(traceGeoJsonExporter.exportVariantStreaming(variant)).thenReturn(mockBody);
        when(traceGeoJsonExporter.exportAllVariantsStreaming(any())).thenReturn(mockBody);

        TraceAcceptedResponse response = traceService.createTraceSession(uploadId);

        // Экспорт одного варианта
        StreamingResponseBody vBody = traceService.exportTrace(response.getTraceId(), "v1");
        assertThat(vBody).isNotNull();

        // Экспорт всех вариантов
        StreamingResponseBody allBody = traceService.exportTrace(response.getTraceId(), null);
        assertThat(allBody).isNotNull();

        // Несуществующий вариант
        assertThatThrownBy(() -> traceService.exportTrace(response.getTraceId(), "v99"))
                .isInstanceOf(VariantNotFoundException.class);
    }
}
