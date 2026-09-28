package ru.moscow.heat.trace.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TraceAcceptedResponse;
import ru.moscow.heat.trace.dto.TraceResult as TraceDtoResult;
import ru.moscow.heat.trace.dto.TraceStatus;
import ru.moscow.heat.trace.dto.TraceStatusResponse;
import ru.moscow.heat.trace.dto.VariantResult;
import ru.moscow.heat.trace.dto.VariantSummary;
import ru.moscow.heat.trace.exception.TraceNotFoundException;
import ru.moscow.heat.trace.exception.VariantNotFoundException;
import ru.moscow.heat.trace.model.TraceResult;
import ru.moscow.heat.trace.model.UnconnectedOks;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Сервис управления сессиями моделирования трасс тепловых сетей.
 */
@Slf4j
@Service
public class TraceService {

    private final UploadSessionRepository uploadSessionRepository;
    private final TieInCandidateService tieInCandidateService;
    private final VariantGenerator variantGenerator;
    private final TraceGeoJsonExporter traceGeoJsonExporter;

    private final Map<UUID, TraceStatusResponse> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> traceToUpload = new ConcurrentHashMap<>();
    private final Map<UUID, TraceResult> traceResults = new ConcurrentHashMap<>();
    private final Map<UUID, ru.moscow.heat.trace.dto.TraceResult> variantResults = new ConcurrentHashMap<>();

    @Autowired
    public TraceService(
            UploadSessionRepository uploadSessionRepository,
            TieInCandidateService tieInCandidateService,
            @Autowired(required = false) VariantGenerator variantGenerator,
            @Autowired(required = false) TraceGeoJsonExporter traceGeoJsonExporter) {
        this.uploadSessionRepository = uploadSessionRepository;
        this.tieInCandidateService = tieInCandidateService;
        this.variantGenerator = variantGenerator;
        this.traceGeoJsonExporter = traceGeoJsonExporter;
    }

    public TraceService(
            UploadSessionRepository uploadSessionRepository,
            TieInCandidateService tieInCandidateService) {
        this(uploadSessionRepository, tieInCandidateService, null, null);
    }

    /**
     * Создает новую сессию трассировки в статусе {@link TraceStatus#PENDING}.
     *
     * @param uploadId идентификатор сессии загрузки
     * @return ответ о принятии задачи
     * @throws UploadNotFoundException если сессия загрузки не найдена
     */
    public TraceAcceptedResponse createTraceSession(UUID uploadId) {
        if (!uploadSessionRepository.existsById(uploadId)) {
            throw new UploadNotFoundException(uploadId);
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

        log.info("Создана сессия трассировки [{}] для загрузки [{}]", traceId, uploadId);
        return new TraceAcceptedResponse(traceId, "/api/trace/" + traceId);
    }

    /**
     * Возвращает текущий статус задачи моделирования трасс.
     *
     * @param traceId идентификатор задачи трассировки
     * @return статус задачи
     * @throws TraceNotFoundException если задача не найдена
     */
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

    public void markCompleted(UUID traceId, TraceResult result) {
        traceResults.put(traceId, result);
        List<String> unconnectedIds = result.getUnconnectedOks().stream()
                .map(UnconnectedOks::getOksPointFeatureId)
                .collect(Collectors.toList());

        update(traceId, current -> current.toBuilder()
                .status(TraceStatus.COMPLETED)
                .completedAt(Instant.now())
                .totalOksCount(result.getSummaryCounters().getTotalOksCount())
                .connectedCount(result.getSummaryCounters().getConnectedCount())
                .unconnectedCount(result.getSummaryCounters().getUnconnectedCount())
                .unconnectedOksFeatureIds(unconnectedIds)
                .build());
    }

    public void markFailed(UUID traceId, String errorMessage) {
        update(traceId, current -> current.toBuilder()
                .status(TraceStatus.FAILED)
                .completedAt(Instant.now())
                .errorMessage(errorMessage)
                .build());
    }

    public TraceResult getTraceResult(UUID traceId) {
        if (!sessions.containsKey(traceId)) {
            throw new TraceNotFoundException(traceId);
        }
        return traceResults.get(traceId);
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
     * Получает результат трассировки с вариантами (Итерация 7)
     */
    public ru.moscow.heat.trace.dto.TraceResult getVariantsTraceResult(UUID traceId) {
        if (!sessions.containsKey(traceId)) {
            throw new TraceNotFoundException(traceId);
        }
        ru.moscow.heat.trace.dto.TraceResult result = variantResults.get(traceId);
        if (result == null && variantGenerator != null) {
            UUID uploadId = traceToUpload.get(traceId);
            result = variantGenerator.generateTraceResult(uploadId, traceId);
            variantResults.put(traceId, result);
        }
        return result;
    }

    /**
     * Получает список ранжированных сводок вариантов трассировки
     */
    public List<VariantSummary> getVariants(UUID traceId) {
        ru.moscow.heat.trace.dto.TraceResult result = getVariantsTraceResult(traceId);
        if (result == null || result.getVariants() == null) {
            return Collections.emptyList();
        }
        return result.getVariants().stream()
                .map(VariantResult::getSummary)
                .collect(Collectors.toList());
    }

    /**
     * Выполняет потоковый экспорт одного или всех вариантов в GeoJSON
     */
    public StreamingResponseBody exportTrace(UUID traceId, String variantId) {
        ru.moscow.heat.trace.dto.TraceResult result = getVariantsTraceResult(traceId);
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
                            "Вариант трассировки [" + variantId + "] не найден для задачи [" + traceId + "]"));
            return traceGeoJsonExporter.exportVariantStreaming(variant);
        }
        return traceGeoJsonExporter.exportAllVariantsStreaming(result);
    }

    private void update(UUID traceId, java.util.function.UnaryOperator<TraceStatusResponse> mutator) {
        TraceStatusResponse updated = sessions.compute(traceId, (id, current) -> {
            if (current == null) {
                throw new TraceNotFoundException(traceId);
            }
            return mutator.apply(current);
        });
        assert updated != null;
    }
}
