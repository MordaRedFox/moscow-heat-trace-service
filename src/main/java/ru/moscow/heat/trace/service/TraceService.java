package ru.moscow.heat.trace.service;

import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;
import ru.moscow.heat.trace.dto.TraceAcceptedResponse;
import ru.moscow.heat.trace.dto.TraceStatus;
import ru.moscow.heat.trace.dto.TraceStatusResponse;
import ru.moscow.heat.trace.exception.TraceNotFoundException;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис управления сессиями трассировки (заглушка для Итерации 3).
 * Хранилище сессий в оперативной памяти (ConcurrentHashMap).
 */
@Service
public class TraceService {

    private final UploadSessionRepository uploadSessionRepository;
    private final Map<UUID, TraceStatusResponse> sessions = new ConcurrentHashMap<>();

    public TraceService(UploadSessionRepository uploadSessionRepository) {
        this.uploadSessionRepository = Objects.requireNonNull(uploadSessionRepository,
                "UploadSessionRepository must not be null");
    }

    /**
     * Создает новую сессию трассировки для существующей загрузки uploadId.
     *
     * @param uploadId идентификатор загруженного набора данных
     * @return TraceAcceptedResponse со сгенерированным traceId и statusUrl
     * @throws UploadNotFoundException если uploadId не найден в базе данных
     */
    public TraceAcceptedResponse createTraceSession(UUID uploadId) {
        if (uploadId == null || !uploadSessionRepository.existsById(uploadId)) {
            throw new UploadNotFoundException("Сессия загрузки с id=" + uploadId + " не найдена");
        }

        UUID traceId = UUID.randomUUID();
        TraceStatusResponse session = new TraceStatusResponse(
                traceId,
                TraceStatus.NOT_IMPLEMENTED,
                Instant.now()
        );
        sessions.put(traceId, session);

        return new TraceAcceptedResponse(traceId, "/api/trace/" + traceId);
    }

    /**
     * Получает текущий статус сессии трассировки по traceId.
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
}
