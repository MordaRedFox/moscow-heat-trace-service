package ru.moscow.heat.trace.service;

import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TraceAcceptedResponse;
import ru.moscow.heat.trace.dto.TraceStatus;
import ru.moscow.heat.trace.dto.TraceStatusResponse;
import ru.moscow.heat.trace.exception.TraceNotFoundException;
import ru.moscow.heat.trace.model.TraceResult;
import ru.moscow.heat.trace.model.UnconnectedOks;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Сервис управления сессиями трассировки.
 * <p>
 * Обновлено для итерации 5: раньше {@code createTraceSession} сразу
 * записывал статус {@code NOT_IMPLEMENTED} (заглушка итерации 3). Теперь
 * сессия стартует в {@code PENDING}, а реальные переходы статуса —
 * {@code PROCESSING}/{@code COMPLETED}/{@code FAILED} — выполняют новые
 * методы {@link #markProcessing}/{@link #markCompleted}/{@link #markFailed},
 * которые вызывает {@code TraceAsyncProcessor} по ходу фоновой обработки.
 * <p>
 * Хранилище сессий по-прежнему в оперативной памяти
 * ({@code ConcurrentHashMap}) — при перезапуске приложения все сессии
 * теряются. Персистентное хранение (БД) — отдельная задача, не в этой
 * итерации.
 * <p>
 * {@code traceResults} хранит полный {@link TraceResult} по завершённым
 * сессиям — сейчас наружу отдаются только счётчики через
 * {@link TraceStatusResponse}, но полный результат понадобится итерациям
 * 6-7 (выгрузка GeoJSON, стоимость, варианты), поэтому не выбрасываем его.
 */
@Service
public class TraceService {

    private final UploadSessionRepository uploadSessionRepository;
    private final TieInCandidateService tieInCandidateService;

    private final Map<UUID, TraceStatusResponse> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> traceToUpload = new ConcurrentHashMap<>();
    private final Map<UUID, TraceResult> traceResults = new ConcurrentHashMap<>();

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
     * Создаёт новую сессию трассировки для существующей загрузки.
     * Сессия сразу регистрируется со статусом {@link TraceStatus#PENDING} —
     * фактический запуск расчёта (перевод в {@code PROCESSING}) выполняет
     * вызывающий код через {@code TraceAsyncProcessor.process(traceId, uploadId)}
     * сразу после получения ответа (см. {@code TraceController.startTrace}).
     *
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
        TraceStatusResponse session = TraceStatusResponse.builder()
                .traceId(traceId)
                .status(TraceStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        sessions.put(traceId, session);
        traceToUpload.put(traceId, uploadId);

        return new TraceAcceptedResponse(
                traceId, "/api/trace/" + traceId);
    }

    /**
     * Возвращает {@code uploadId}, связанный с сессией трассировки.
     * Используется {@code TraceController}, чтобы передать его в
     * {@code TraceAsyncProcessor.process(traceId, uploadId)} без повторного
     * похода в {@link #getTraceStatus}.
     *
     * @param traceId идентификатор задачи трассировки
     * @return идентификатор загрузки
     * @throws TraceNotFoundException если задача не найдена
     */
    public UUID getUploadIdForTrace(UUID traceId) {
        UUID uploadId = traceToUpload.get(traceId);
        if (uploadId == null) {
            throw new TraceNotFoundException(traceId);
        }
        return uploadId;
    }

    /**
     * Получает текущий статус сессии трассировки по traceId.
     *
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
     * Переводит сессию в {@link TraceStatus#PROCESSING}. Вызывается
     * {@code TraceAsyncProcessor} перед запуском {@code TraceOrchestrator.run()}.
     *
     * @param traceId идентификатор задачи трассировки
     * @throws TraceNotFoundException если задача не найдена
     */
    public void markProcessing(UUID traceId) {
        update(traceId, current -> current.toBuilder()
                .status(TraceStatus.PROCESSING)
                .startedAt(Instant.now())
                .build());
    }

    /**
     * Переводит сессию в {@link TraceStatus#COMPLETED} и сохраняет счётчики
     * из {@link TraceResult} (ТЗ, п.2.9-2.10: явный перечень ОКС без
     * маршрута — часть результата, а не деталь диагностики).
     *
     * @param traceId идентификатор задачи трассировки
     * @param result  результат работы {@code TraceOrchestrator}
     * @throws TraceNotFoundException если задача не найдена
     */
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

    /**
     * Переводит сессию в {@link TraceStatus#FAILED} с сообщением об ошибке.
     *
     * @param traceId      идентификатор задачи трассировки
     * @param errorMessage сообщение об ошибке (усечённое при необходимости
     *                     на стороне вызывающего кода)
     * @throws TraceNotFoundException если задача не найдена
     */
    public void markFailed(UUID traceId, String errorMessage) {
        update(traceId, current -> current.toBuilder()
                .status(TraceStatus.FAILED)
                .completedAt(Instant.now())
                .errorMessage(errorMessage)
                .build());
    }

    /**
     * Возвращает полный результат завершённой трассировки (для будущих
     * итераций — выгрузка GeoJSON, расчёт стоимости и вариантов).
     * В текущем API наружу не отдаётся ни одним эндпоинтом.
     *
     * @param traceId идентификатор задачи трассировки
     * @return результат или {@code null}, если сессия ещё не завершена
     * либо результат не сохранялся (устарел/сброшен)
     * @throws TraceNotFoundException если сессия вообще не найдена
     */
    public TraceResult getTraceResult(UUID traceId) {
        if (!sessions.containsKey(traceId)) {
            throw new TraceNotFoundException(traceId);
        }
        return traceResults.get(traceId);
    }

    /**
     * Возвращает список кандидатов на присоединение для всех
     * точек подключения ОКС, относящихся к сессии трассировки.
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

    private void update(UUID traceId, java.util.function.UnaryOperator<TraceStatusResponse> mutator) {
        TraceStatusResponse updated = sessions.compute(traceId, (id, current) -> {
            if (current == null) {
                throw new TraceNotFoundException(traceId);
            }
            return mutator.apply(current);
        });
        // compute() выше уже бросит TraceNotFoundException синхронно при current == null,
        // updated используется только чтобы избежать предупреждения о неиспользуемой переменной.
        assert updated != null;
    }
}
