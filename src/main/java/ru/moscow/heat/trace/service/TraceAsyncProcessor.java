package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.moscow.heat.trace.model.TraceResult;

import java.util.List;
import java.util.UUID;

/**
 * Асинхронная обработка трассировки: запускает все три стратегии
 * ({@link TraceOrchestrator#runAll(UUID)}), передаёт список результатов
 * в {@link TraceService#markCompleted(UUID, List)}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceAsyncProcessor {

    private final TraceOrchestrator traceOrchestrator;
    private final TraceService traceService;

    /**
     * Запускает расчёт трассировки в отдельном потоке.
     *
     * @param traceId  id сессии трассировки
     * @param uploadId id загрузки
     */
    @Async("geoJsonTaskExecutor")
    public void process(UUID traceId, UUID uploadId) {
        MDC.put("traceId", traceId.toString());
        MDC.put("uploadId", uploadId.toString());
        try {
            traceService.markProcessing(traceId);

            List<TraceResult> results = traceOrchestrator.runAll(uploadId);

            traceService.markCompleted(traceId, results);

            TraceResult main = results.isEmpty() ? null : results.get(0);
            int segs = main != null ? main.getSegments().size() : 0;
            int unconn = main != null ? main.getUnconnectedOks().size() : 0;
            log.info("Трассировка traceId={} завершена: {} вариантов, "
                            + "main: {} сегментов, {} неподключённых",
                    traceId, results.size(), segs, unconn);
        } catch (Exception e) {
            log.error("Ошибка трассировки traceId={}", traceId, e);
            traceService.markFailed(traceId, truncate(e.getMessage(), 3900));
        } finally {
            MDC.remove("traceId");
            MDC.remove("uploadId");
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "Трассировка завершилась с ошибкой без сообщения";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
