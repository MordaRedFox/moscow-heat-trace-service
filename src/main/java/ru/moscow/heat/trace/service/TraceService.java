package ru.moscow.heat.trace.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TraceAcceptedResponse;
import ru.moscow.heat.trace.dto.TraceResult;
import ru.moscow.heat.trace.dto.TraceStatus;
import ru.moscow.heat.trace.dto.TraceStatusResponse;
import ru.moscow.heat.trace.dto.VariantResult;
import ru.moscow.heat.trace.dto.VariantSummary;
import ru.moscow.heat.trace.exception.TraceNotFoundException;
import ru.moscow.heat.trace.exception.VariantNotFoundException;
import ru.moscow.heat.trace.model.UnconnectedOks;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Сервис управления сессиями трассировки.
 *
 * <p>Хранит сессии в памяти ({@code ConcurrentHashMap}): {@code traceId → uploadId},
 * {@code traceId → TraceStatusResponse}, {@code traceId → dto.TraceResult}.
 * При перезапуске приложения сессии теряются — персистентное хранение
 * вне рамок итерации 7.
 */
@Slf4j
@Service
public class TraceService {

    private final UploadSessionRepository uploadSessionRepository;
    private final TieInCandidateService tieInCandidateService;
    private final TraceGeoJsonExporter traceGeoJsonExporter;
    private final TraceResultMapper traceResultMapper;

    private final Map<UUID, TraceStatusResponse> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> traceToUpload = new ConcurrentHashMap<>();
    private final Map<UUID, TraceResult> variantResults = new ConcurrentHashMap<>();

    public TraceService(UploadSessionRepository uploadSessionRepository,
                        TieInCandidateService tieInCandidateService,
                        TraceGeoJsonExporter traceGeoJsonExporter,
                        TraceResultMapper traceResultMapper) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.tieInCandidateService = tieInCandidateService;
        this.traceGeoJsonExporter = traceGeoJsonExporter;
        this.traceResultMapper = traceResultMapper;
    }

    public TraceAcceptedResponse createTraceSession(UUID uploadId) {
        if (uploadId == null
                || !uploadSessionRepository.existsById(uploadId)) {
            throw new UploadNotFoundException(
                    "Сессия загрузки с id=" + uploadId + " не найдена");
        }

        UUID traceId = UUID.randomUUID();
        TraceStatusResponse initialStatus = TraceStatusResponse.builder()
                .traceId(traceId)
                .status(TraceStatus.PENDING)
                .createdAt(Instant.now())
                .totalOksCount(0)
                .connectedCount(0)
                .unconnectedCount(0)
                .unconnectedOksFeatureIds(Collections.emptyList())
                .build();

        sessions.put(traceId, initialStatus);
        traceToUpload.put(traceId, uploadId);

        log.info("Создана сессия трассировки [{}] для загрузки [{}]",
                traceId, uploadId);
        return new TraceAcceptedResponse(traceId, "/api/trace/" + traceId);
    }

    public TraceStatusResponse getTraceStatus(UUID traceId) {
        TraceStatusResponse status = sessions.get(traceId);
        if (status == null) {
            throw new TraceNotFoundException(traceId);
        }
        return status;
    }

    public void markProcessing(UUID traceId) {
        update(traceId, current -> current.toBuilder()
                .status(TraceStatus.PROCESSING)
                .startedAt(Instant.now())
                .build());
    }

    /**
     * Завершает сессию трассировки: сохраняет DTO с вариантами, обновляет
     * статус и счётчики. Если маппинг падает — сохраняем сессию как
     * COMPLETED с базовыми счётчиками (без вариантов), чтобы результат
     * main-стратегии не терялся.
     *
     * @param traceId     id сессии
     * @param orchResults список результатов от оркестратора (обычно 3)
     */
    public void markCompleted(UUID traceId,
                              List<ru.moscow.heat.trace.model.TraceResult> orchResults) {
        UUID uploadId = traceToUpload.get(traceId);
        int totalOks = 0;
        int connected = 0;
        int unconnected = 0;
        List<String> unconnectedIds = Collections.emptyList();

        if (orchResults != null && !orchResults.isEmpty()) {
            ru.moscow.heat.trace.model.TraceResult main = orchResults.get(0);
            totalOks = main.getSummaryCounters().getTotalOksCount();
            connected = main.getSummaryCounters().getConnectedCount();
            unconnected = main.getSummaryCounters().getUnconnectedCount();
            unconnectedIds = main.getUnconnectedOks().stream()
                    .map(UnconnectedOks::getOksPointFeatureId)
                    .collect(Collectors.toList());
        }

        try {
            if (uploadId != null && traceResultMapper != null) {
                TraceResult dtoResult = traceResultMapper
                        .toTraceResult(uploadId, traceId, orchResults);
                variantResults.put(traceId, dtoResult);
            }
        } catch (Exception e) {
            log.error("Не удалось сформировать варианты для [{}], "
                    + "сессия будет завершена без вариантов: {}",
                    traceId, e.getMessage(), e);
        }

        final int tOks = totalOks;
        final int cOks = connected;
        final int uOks = unconnected;
        final List<String> uIds = unconnectedIds;
        update(traceId, current -> current.toBuilder()
                .status(TraceStatus.COMPLETED)
                .completedAt(Instant.now())
                .totalOksCount(tOks)
                .connectedCount(cOks)
                .unconnectedCount(uOks)
                .unconnectedOksFeatureIds(uIds)
                .build());
    }

    public void markFailed(UUID traceId, String errorMessage) {
        update(traceId, current -> current.toBuilder()
                .status(TraceStatus.FAILED)
                .completedAt(Instant.now())
                .errorMessage(errorMessage)
                .build());
    }

    public List<TieInCandidate> getCandidates(UUID traceId) {
        UUID uploadId = traceToUpload.get(traceId);
        if (uploadId == null) {
            throw new TraceNotFoundException(traceId);
        }
        Map<String, List<TieInCandidate>> byPoint =
                tieInCandidateService.findCandidatesForAllPoints(uploadId);
        List<TieInCandidate> flat = new ArrayList<>();
        for (List<TieInCandidate> list : byPoint.values()) {
            flat.addAll(list);
        }
        return flat;
    }

    /**
     * Возвращает публичный результат со списком вариантов.
     *
     * @param traceId id сессии
     * @return DTO или {@code null}, если варианты ещё не сформированы
     * @throws TraceNotFoundException если сессия не найдена
     */
    public TraceResult getVariantsTraceResult(UUID traceId) {
        if (!sessions.containsKey(traceId)) {
            throw new TraceNotFoundException(traceId);
        }
        return variantResults.get(traceId);
    }

    /**
     * Ранжированный список сводок вариантов.
     */
    public List<VariantSummary> getVariants(UUID traceId) {
        TraceResult result = getVariantsTraceResult(traceId);
        if (result == null || result.getVariants() == null) {
            return Collections.emptyList();
        }
        return result.getVariants().stream()
                .map(VariantResult::getSummary)
                .collect(Collectors.toList());
    }

    /**
     * Потоковый экспорт одного или всех вариантов в GeoJSON.
     */
    public StreamingResponseBody exportTrace(UUID traceId, String variantId) {
        TraceResult result = getVariantsTraceResult(traceId);
        if (result == null) {
            throw new TraceNotFoundException(traceId);
        }
        if (traceGeoJsonExporter == null) {
            return outputStream -> {};
        }
        if (variantId != null && !variantId.isBlank()) {
            VariantResult variant = result.getVariants().stream()
                    .filter(v -> variantId.equalsIgnoreCase(v.getVariantId()))
                    .findFirst()
                    .orElseThrow(() -> new VariantNotFoundException(
                            "Вариант [" + variantId + "] не найден для ["
                                    + traceId + "]"));
            return traceGeoJsonExporter.exportVariantStreaming(variant);
        }
        return traceGeoJsonExporter.exportAllVariantsStreaming(result);
    }

    private void update(UUID traceId,
                        java.util.function.UnaryOperator<TraceStatusResponse> mutator) {
        TraceStatusResponse updated = sessions.compute(traceId, (id, current) -> {
            if (current == null) {
                throw new TraceNotFoundException(traceId);
            }
            return mutator.apply(current);
        });
        assert updated != null;
    }
}
