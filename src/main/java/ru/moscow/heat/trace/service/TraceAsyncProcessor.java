package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Асинхронная обработка трассировки — аналог {@code GeoJsonAsyncProcessor}
 * из итерации 2 (план, п.3, "Пакет trace/service").
 * <p>
 * Обновляет статус сессии трассировки (PENDING -&gt; PROCESSING -&gt;
 * COMPLETED/FAILED) и сохраняет {@code TraceResult} по завершении.
 * Использует тот же пул потоков, что и GeoJSON-парсинг
 * ({@code AsyncConfig}), либо отдельный — на усмотрение реализации.
 * <p>
 * TODO: внедрить зависимости: {@code TraceOrchestrator}, {@code TraceService}
 * (или репозиторий сессий трассировки, если она переедет из in-memory в БД).
 */
@Service
@RequiredArgsConstructor
public class TraceAsyncProcessor {

    private final TraceOrchestrator traceOrchestrator;
    private final TraceService traceService;

    /**
     * Запускает расчёт трассировки в отдельном потоке.
     *
     * @param traceId  id сессии трассировки (для обновления статуса)
     * @param uploadId id загрузки, по которой считаем маршрут
     */
    @Async
    public void process(String traceId, Long uploadId) {
        // TODO:
        // 1. traceService.markProcessing(traceId);
        // 2. try { TraceResult result = traceOrchestrator.run(uploadId);
        //          traceService.markCompleted(traceId, result); }
        // 3. catch (Exception e) { traceService.markFailed(traceId, e); }
        //    (гарантированная обработка ошибок — по аналогии с
        //    GeoJsonAsyncProcessor, который гарантирует cleanup)
        throw new UnsupportedOperationException("TODO: итерация 5, п.1");
    }
}
