package ru.moscow.heat.trace.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;
import ru.moscow.heat.trace.dto.*;
import ru.moscow.heat.trace.exception.TraceNotFoundException;
import ru.moscow.heat.trace.exception.VariantNotFoundException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Сервис управления сессиями и моделированием трассировки.
 * Выполняет расчет вариантов трассировки, их ранжирование и потоковый экспорт.
 */
@Slf4j
@Service
public class TraceService {

    private final UploadSessionRepository uploadSessionRepository;
    private final TieInCandidateService tieInCandidateService;
    private final VariantGenerator variantGenerator;
    private final TraceGeoJsonExporter traceGeoJsonExporter;

    private final Map<UUID, TraceStatusResponse> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> traceUploads = new ConcurrentHashMap<>();
    private final Map<UUID, List<TieInCandidate>> candidateCache = new ConcurrentHashMap<>();
    private final Map<UUID, TraceResult> traceResults = new ConcurrentHashMap<>();

    public TraceService(
            UploadSessionRepository uploadSessionRepository,
            TieInCandidateService tieInCandidateService,
            VariantGenerator variantGenerator,
            TraceGeoJsonExporter traceGeoJsonExporter) {
        this.uploadSessionRepository = Objects.requireNonNull(
                uploadSessionRepository, "UploadSessionRepository must not be null");
        this.tieInCandidateService = Objects.requireNonNull(
                tieInCandidateService, "TieInCandidateService must not be null");
        this.variantGenerator = Objects.requireNonNull(
                variantGenerator, "VariantGenerator must not be null");
        this.traceGeoJsonExporter = Objects.requireNonNull(
                traceGeoJsonExporter, "TraceGeoJsonExporter must not be null");
    }

    /**
     * Создает новую сессию трассировки для существующей загрузки uploadId
     * и выполняет расчет кандидатов на присоединение и вариантов трассы.
     *
     * @param uploadId идентификатор загруженного набора данных
     * @return TraceAcceptedResponse со сгенерированным traceId и statusUrl
     * @throws UploadNotFoundException если uploadId не найден в базе данных
     */
    public TraceAcceptedResponse createTraceSession(UUID uploadId) {
        if (uploadId == null || !uploadSessionRepository.existsById(uploadId)) {
            throw new UploadNotFoundException(
                    "Сессия загрузки с id=" + uploadId + " не найдена");
        }

        UUID traceId = UUID.randomUUID();
        TraceStatusResponse session = new TraceStatusResponse(
                traceId,
                TraceStatus.COMPLETED,
                Instant.now()
        );
        sessions.put(traceId, session);
        traceUploads.put(traceId, uploadId);

        Map<String, List<TieInCandidate>> candidatesByPoint =
                tieInCandidateService.findCandidatesForAllPoints(uploadId);
        List<TieInCandidate> allCandidates = candidatesByPoint.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        candidateCache.put(traceId, allCandidates);

        TraceResult traceResult = variantGenerator.generateTraceResult(uploadId, traceId);
        traceResults.put(traceId, traceResult);

        log.info("Сессия трассировки [{}] для загрузки [{}]: сформировано {} вариантов трассы",
                traceId, uploadId, traceResult.getVariants().size());

        return new TraceAcceptedResponse(traceId, "/api/trace/" + traceId);
    }

    /**
     * Получает текущий статус сессии трассировки по traceId
     *
     * @param traceId идентификатор задачи трассировки
     * @return TraceStatusResponse
     * @throws TraceNotFoundException если задача с таким traceId отсутствует
     */
    public TraceStatusResponse getTraceStatus(UUID traceId) {
        TraceStatusResponse response = sessions.get(traceId);
        if (response == null) {
            throw new TraceNotFoundException(traceId);
        }
        return response;
    }

    /**
     * Получает список кандидатов на присоединение для задачи трассировки
     *
     * @param traceId идентификатор задачи трассировки
     * @return список кандидатов на присоединение
     * @throws TraceNotFoundException если задача с таким traceId отсутствует
     */
    public List<TieInCandidate> getCandidates(UUID traceId) {
        if (!sessions.containsKey(traceId)) {
            throw new TraceNotFoundException(traceId);
        }
        List<TieInCandidate> cached = candidateCache.get(traceId);
        if (cached != null) {
            return cached;
        }
        UUID uploadId = traceUploads.get(traceId);
        if (uploadId == null) {
            return Collections.emptyList();
        }
        Map<String, List<TieInCandidate>> candidatesByPoint =
                tieInCandidateService.findCandidatesForAllPoints(uploadId);
        List<TieInCandidate> allCandidates = candidatesByPoint.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        candidateCache.put(traceId, allCandidates);
        return allCandidates;
    }

    /**
     * Получает полный результат трассировки по traceId
     *
     * @param traceId идентификатор задачи трассировки
     * @return результат трассировки
     * @throws TraceNotFoundException если задача отсутствует
     */
    public TraceResult getTraceResult(UUID traceId) {
        if (!sessions.containsKey(traceId)) {
            throw new TraceNotFoundException(traceId);
        }
        TraceResult result = traceResults.get(traceId);
        if (result == null) {
            UUID uploadId = traceUploads.get(traceId);
            result = variantGenerator.generateTraceResult(uploadId, traceId);
            traceResults.put(traceId, result);
        }
        return result;
    }

    /**
     * Получает список ранжированных сводок вариантов трассировки
     *
     * @param traceId идентификатор задачи трассировки
     * @return список сводок вариантов
     */
    public List<VariantSummary> getVariants(UUID traceId) {
        TraceResult result = getTraceResult(traceId);
        return result.getVariants().stream()
                .map(VariantResult::getSummary)
                .collect(Collectors.toList());
    }

    /**
     * Выполняет экспорт одного или всех вариантов трассировки в виде потокового ответа StreamingResponseBody
     *
     * @param traceId   идентификатор задачи трассировки
     * @param variantId идентификатор конкретного варианта (опционально)
     * @return StreamingResponseBody с GeoJSON
     */
    public StreamingResponseBody exportTrace(UUID traceId, String variantId) {
        TraceResult result = getTraceResult(traceId);
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
}
