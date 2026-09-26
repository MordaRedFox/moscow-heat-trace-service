package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.moscow.heat.trace.model.TraceResult;

import java.util.UUID;

/**
 * Асинхронная обработка трассировки — по образцу {@code GeoJsonAsyncProcessor}:
 * статус сессии обновляется на каждом переходе, ошибки не теряются молча.
 * <p>
 * Пул потоков переиспользует {@code geoJsonTaskExecutor} из
 * {@code AsyncConfig} (2-4 потока). Трассировка (A*, O(n^2) видимость)
 * потенциально тяжелее по CPU, чем парсинг GeoJSON — если в профилировании
 * окажется, что расчёты трассировки блокируют очередь загрузок (или
 * наоборот), стоит завести отдельный executor "traceTaskExecutor" в
 * {@code AsyncConfig} и переключить аннотацию ниже на него.
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
     * @param traceId  id сессии трассировки (для обновления статуса)
     * @param uploadId id загрузки, по которой считаем маршрут
     */
    @Async("geoJsonTaskExecutor")
    public void process(UUID traceId, UUID uploadId) {
        MDC.put("traceId", traceId.toString());
        MDC.put("uploadId", uploadId.toString());
        try {
            traceService.markProcessing(traceId);

            TraceResult result = traceOrchestrator.run(uploadId);

            traceService.markCompleted(traceId, result);
            log.info("Трассировка traceId={} завершена: {} сегментов, {} неподключённых ОКС",
                    traceId, result.getSegments().size(), result.getUnconnectedOks().size());
        } catch (Exception e) {
            log.error("Ошибка трассировки traceId={}", traceId, e);
            traceService.markFailed(traceId, truncate(e.getMessage(), 3900));
        } finally {
            MDC.remove("traceId");
            MDC.remove("uploadId");
        }
    }

    /**
     * Обрезает сообщение об ошибке до максимальной длины (тот же приём,
     * что и в {@code GeoJsonAsyncProcessor.truncate}, чтобы влезало в
     * колонку/поле {@code error_message}, если оно когда-нибудь появится
     * в персистентном хранилище сессий трассировки).
     */
    private String truncate(String s, int max) {
        if (s == null) {
            return "Трассировка завершилась с ошибкой без сообщения (" + "см. логи по traceId)";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
