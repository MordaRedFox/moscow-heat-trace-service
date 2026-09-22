package ru.moscow.heat.trace.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TraceAcceptedResponse;
import ru.moscow.heat.trace.dto.TraceStatus;
import ru.moscow.heat.trace.dto.TraceStatusResponse;
import ru.moscow.heat.trace.exception.TraceNotFoundException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Сервис управления сессиями трассировки.
 * Хранилище сессий в оперативной памяти (ConcurrentHashMap).
 * В рамках Итерации 4 выполняет расчет и хранение кандидатов на присоединение (tie-in candidates).
 */
@Slf4j
@Service
public class TraceService {

    private final UploadSessionRepository uploadSessionRepository;
    private final TieInCandidateService tieInCandidateService;
    private final Map<UUID, TraceStatusResponse> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> traceUploads = new ConcurrentHashMap<>();
    private final Map<UUID, List<TieInCandidate>> candidateCache = new ConcurrentHashMap<>();

    public TraceService(
            UploadSessionRepository uploadSessionRepository,
            TieInCandidateService tieInCandidateService) {
        this.uploadSessionRepository = Objects.requireNonNull(
                uploadSessionRepository, "UploadSessionRepository must not be null");
        this.tieInCandidateService = Objects.requireNonNull(
                tieInCandidateService, "TieInCandidateService must not be null");
    }

    /**
     * Создает новую сессию трассировки для существующей загрузки uploadId
     * и выполняет расчет кандидатов на присоединение.
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
                TraceStatus.NOT_IMPLEMENTED,
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

        log.info("Сессия трассировки [{}] для загрузки [{}]: вычислено {} кандидатов на присоединение для {} точек ОКС",
                traceId, uploadId, allCandidates.size(), candidatesByPoint.size());

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
}
