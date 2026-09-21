package ru.moscow.heat.trace.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.trace.dto.TraceAcceptedResponse;
import ru.moscow.heat.trace.dto.TraceStatusResponse;
import ru.moscow.heat.trace.exception.TraceNotFoundException;
import ru.moscow.heat.trace.service.TraceService;

import java.util.Map;
import java.util.UUID;

/**
 * REST-контроллер моделирования трасс подключения к тепловым сетям.
 * В рамках Итерации 3 реализует контрактное API-заглушку:
 * - POST /api/trace/{uploadId} возвращает HTTP 202 Accepted и регистрирует сессию;
 * - GET /api/trace/{traceId} возвращает HTTP 501 Not Implemented (алгоритм трассировки
 *   реализуется в Итерации 4).
 */
@Slf4j
@RestController
@RequestMapping("/api/trace")
@Tag(name = "Trace Modeling",
     description = "Моделирование и построение трасс подключения к тепловым сетям")
public class TraceController {

    private final TraceService traceService;

    public TraceController(TraceService traceService) {
        this.traceService = traceService;
    }

    /**
     * Запуск моделирования трассы для указанной сессии загрузки.
     *
     * @param uploadId идентификатор сессии загрузки
     * @return 202 Accepted с traceId и ссылкой на статус
     */
    @PostMapping("/{uploadId}")
    @Operation(summary = "Запустить моделирование трасс",
               description = "Принимает задачу трассировки в обработку, регистрирует задачу и возвращает traceId")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Задача принята в обработку",
                    content = @Content(schema = @Schema(implementation = TraceAcceptedResponse.class))),
            @ApiResponse(responseCode = "404", description = "Загрузка uploadId не найдена")
    })
    public ResponseEntity<TraceAcceptedResponse> startTrace(@PathVariable UUID uploadId) {
        TraceAcceptedResponse response = traceService.createTraceSession(uploadId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Опрос статуса выполнения задачи трассировки.
     * По спецификации Итерации 3 возвращает HTTP 501 Not Implemented с телом TraceStatusResponse.
     *
     * @param traceId идентификатор задачи трассировки
     * @return 501 Not Implemented с телом статуса задачи
     */
    @GetMapping("/{traceId}")
    @Operation(summary = "Получить статус моделирования трасс",
               description = "Возвращает текущий статус задачи. В Итерации 3 возвращает HTTP 501 Not Implemented")
    @ApiResponses({
            @ApiResponse(responseCode = "501", description = "Алгоритм трассировки не реализован (заглушка Итерации 3)",
                    content = @Content(schema = @Schema(implementation = TraceStatusResponse.class))),
            @ApiResponse(responseCode = "404", description = "Задача трассировки не найдена")
    })
    public ResponseEntity<TraceStatusResponse> getStatus(@PathVariable UUID traceId) {
        // Трактовка спеки Влада: задача принимается через 202 на POST, а при опросе статуса на GET
        // возвращается HTTP 501 Not Implemented, так как ядро алгоритма трассировки разрабатывается в Итерации 4.
        TraceStatusResponse status = traceService.getTraceStatus(traceId);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(status);
    }

    @ExceptionHandler(UploadNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUploadNotFound(UploadNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(TraceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleTraceNotFound(TraceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }
}
