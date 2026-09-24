package ru.moscow.heat.trace.service;

import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TraceAcceptedResponse;
import ru.moscow.heat.trace.dto.TraceStatus;
import ru.moscow.heat.trace.dto.TraceStatusResponse;
import ru.moscow.heat.trace.exception.TraceNotFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис управления сессиями трассировки
 * <p>Хранилище сессий в оперативной памяти (ConcurrentHashMap).
 * При перезапуске приложения все сессии теряются; персистентное
 * хранение появится вместе с реальной трассировкой
 * <p>Помимо статуса каждой задачи сервис хранит связку
 * {@code traceId → uploadId}, чтобы по идентификатору задачи
 * можно было получить кандидатов на присоединение, рассчитанных
 * для исходной загрузки
 */
@Service
public class TraceService {

    private final UploadSessionRepository uploadSessionRepository;
    private final TieInCandidateService tieInCandidateService;

    private final Map<UUID, TraceStatusResponse> sessions =
            new ConcurrentHashMap<>();
    private final Map<UUID, UUID> traceToUpload =
            new ConcurrentHashMap<>();

    public TraceService(UploadSessionRepository uploadSessionRepository,
                        TieInCandidateService tieInCandidateService) {
        this.uploadSessionRepository = Objects.requireNonNull(
                uploadSessionRepository,
                "UploadSessionRepository must not be null");
        this.tieInCandidateService = Objects.requireNonNull(
                tieInCandidateService,
                "TieInCandidateService must not be null");
    }

    /**
     * Создает новую сессию трассировки для существующей загрузки
     * @param uploadId идентификатор загруженного набора данных
     * @return ответ со сгенерированным traceId и URL статуса
     * @throws UploadNotFoundException если uploadId не найден
     */
    public TraceAcceptedResponse createTraceSession(UUID uploadId) {
        if (uploadId == null
                || !uploadSessionRepository.existsById(uploadId)) {
            throw new UploadNotFoundException(
                    "Сессия загрузки с id=" + uploadId
                            + " не найдена");
        }

        UUID traceId = UUID.randomUUID();
        TraceStatusResponse session = new TraceStatusResponse(
                traceId,
                TraceStatus.NOT_IMPLEMENTED,
                Instant.now()
        );
        sessions.put(traceId, session);
        traceToUpload.put(traceId, uploadId);

        return new TraceAcceptedResponse(
                traceId, "/api/trace/" + traceId);
    }

    /**
     * Получает текущий статус сессии трассировки по traceId
     * @param traceId идентификатор задачи трассировки
     * @return статус задачи
     * @throws TraceNotFoundException если задача не найдена
     */
    public TraceStatusResponse getTraceStatus(UUID traceId) {
        TraceStatusResponse response = sessions.get(traceId);
        if (response == null) {
            throw new TraceNotFoundException(traceId);
        }
        return response;
    }

    /**
     * Возвращает список кандидатов на присоединение для всех
     * точек подключения ОКС, относящихся к сессии трассировки
     * <p>Отладочный метод Итерации 4: используется эндпоинтом
     * {@code GET /api/trace/{traceId}/candidates}
     * @param traceId идентификатор задачи трассировки
     * @return плоский список кандидатов по всем точкам подключения
     * @throws TraceNotFoundException если задача не найдена
     */
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
}
