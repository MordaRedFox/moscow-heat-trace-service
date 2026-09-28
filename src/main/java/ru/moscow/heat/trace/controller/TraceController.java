package ru.moscow.heat.trace.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TraceAcceptedResponse;
import ru.moscow.heat.trace.dto.TraceStatusResponse;
import ru.moscow.heat.trace.dto.VariantSummary;
import ru.moscow.heat.trace.exception.TraceNotFoundException;
import ru.moscow.heat.trace.exception.VariantNotFoundException;
import ru.moscow.heat.trace.service.TraceAsyncProcessor;
import ru.moscow.heat.trace.service.TraceService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST-контроллер моделирования трасс тепловых сетей.
 */
@Slf4j
@RestController
@RequestMapping("/api/trace")
@RequiredArgsConstructor
@Tag(name = "Trace", description = "Моделирование трасс тепловых сетей")
public class TraceController {

    public static final String GEO_JSON_MEDIA_TYPE = "application/geo+json";

    private final TraceService traceService;
    private final TraceAsyncProcessor traceAsyncProcessor;

    /**
     * Запуск моделирования трасс для загруженного набора данных.
     *
     * @param uploadId идентификатор сессии загрузки
     * @return 202 Accepted с traceId и ссылкой на статус
     */
    @PostMapping("/{uploadId}")
    @Operation(summary = "Запустить моделирование трасс",
               description = "Принимает задачу трассировки в обработку, "
                       + "регистрирует задачу, запускает фоновый расчёт "
                       + "и возвращает traceId")
    @ApiResponses({
            @ApiResponse(responseCode = "202",
                    description = "Задача принята в обработку",
                    content = @Content(schema = @Schema(
                            implementation = TraceAcceptedResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Сессия загрузки не найдена")
    })
    public ResponseEntity<TraceAcceptedResponse> startTrace(
            @PathVariable UUID uploadId) {
        TraceAcceptedResponse response =
                traceService.createTraceSession(uploadId);
        traceAsyncProcessor.process(response.getTraceId(), uploadId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Опрос статуса выполнения задачи трассировки
     * @param traceId идентификатор задачи трассировки
     * @return 200 OK с текущим статусом
     */
    @GetMapping("/{traceId}")
    @Operation(summary = "Получить статус моделирования трасс",
               description = "Возвращает текущий статус задачи: "
                       + "PENDING, PROCESSING, COMPLETED или FAILED")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Текущий статус задачи",
                    content = @Content(schema = @Schema(
                            implementation = TraceStatusResponse.class))),
            @ApiResponse(responseCode = "404",
                    description = "Задача трассировки не найдена")
    })
    public ResponseEntity<TraceStatusResponse> getStatus(
            @PathVariable UUID traceId) {
        TraceStatusResponse status = traceService.getTraceStatus(traceId);
        return ResponseEntity.ok(status);
    }

    /**
     * Получение списка кандидатов на присоединение для сессии трассировки.
     *
     * @param traceId идентификатор задачи трассировки
     * @return 200 OK со списком TieInCandidate
     */
    @GetMapping("/{traceId}/candidates")
    @Operation(summary = "Получить кандидатов на присоединение",
               description = "Возвращает список подобранных кандидатов на врезку в существующую сеть для всех точек ОКС")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Список кандидатов на присоединение",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = TieInCandidate.class)))),
            @ApiResponse(responseCode = "404",
                    description = "Задача трассировки не найдена")
    })
    public ResponseEntity<List<TieInCandidate>> getCandidates(
            @PathVariable UUID traceId) {
        List<TieInCandidate> candidates = traceService.getCandidates(traceId);
        return ResponseEntity.ok(candidates);
    }

    /**
     * Получение списка сводок вариантов трассировки.
     *
     * @param traceId идентификатор задачи трассировки
     * @return 200 OK со списком VariantSummary
     */
    @GetMapping("/{traceId}/variants")
    @Operation(summary = "Получить варианты трассировки",
               description = "Возвращает ранжированный список сводок сформированных вариантов трассировки")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Список вариантов трассировки",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = VariantSummary.class)))),
            @ApiResponse(responseCode = "404",
                    description = "Задача трассировки не найдена")
    })
    public ResponseEntity<List<VariantSummary>> getVariants(
            @PathVariable UUID traceId) {
        List<VariantSummary> variants = traceService.getVariants(traceId);
        return ResponseEntity.ok(variants);
    }

    /**
     * Потоковый экспорт одного или всех вариантов трассировки в GeoJSON.
     *
     * @param traceId   идентификатор задачи трассировки
     * @param variantId идентификатор варианта (например "v1", опционально)
     * @return StreamingResponseBody с GeoJSON и заголовком Content-Disposition
     */
    @GetMapping(value = "/{traceId}/export", produces = GEO_JSON_MEDIA_TYPE)
    @Operation(summary = "Экспорт вариантов трассировки в GeoJSON",
               description = "Потоковый экспорт одного варианта или всех вариантов в формате GeoJSON")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Успешный экспорт GeoJSON"),
            @ApiResponse(responseCode = "404", description = "Задача трассировки или вариант не найдены")
    })
    public ResponseEntity<StreamingResponseBody> exportGeoJson(
            @PathVariable UUID traceId,
            @RequestParam(required = false) String variantId) {
        StreamingResponseBody body = traceService.exportTrace(traceId, variantId);
        String filename = (variantId != null && !variantId.isBlank())
                ? String.format("trace-%s-%s.geojson", traceId, variantId)
                : String.format("trace-%s.geojson", traceId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(GEO_JSON_MEDIA_TYPE + ";charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    @ExceptionHandler(UploadNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUploadNotFound(
            UploadNotFoundException ex) {
        log.warn("Сессия загрузки не найдена: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(TraceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleTraceNotFound(
            TraceNotFoundException ex) {
        log.warn("Задача трассировки не найдена: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(VariantNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleVariantNotFound(
            VariantNotFoundException ex) {
        log.warn("Вариант трассировки не найден: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }
}
