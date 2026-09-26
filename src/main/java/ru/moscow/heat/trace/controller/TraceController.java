package ru.moscow.heat.trace.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TraceAcceptedResponse;
import ru.moscow.heat.trace.dto.TraceStatusResponse;
import ru.moscow.heat.trace.exception.TraceNotFoundException;
import ru.moscow.heat.trace.service.TraceAsyncProcessor;
import ru.moscow.heat.trace.service.TraceService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST-контроллер моделирования трасс подключения к тепловым сетям.
 * Предоставляет эндпоинты запуска трассировки, опроса статуса и получения
 * кандидатов на присоединение (tie-in candidates)
 * <p>
 * Обновлено для итерации 5: {@code startTrace} теперь не только регистрирует
 * сессию, но и реально запускает фоновый расчёт через
 * {@code TraceAsyncProcessor}; {@code getStatus} возвращает актуальный
 * статус (200 OK с телом, содержащим {@code TraceStatus} — PENDING,
 * PROCESSING, COMPLETED со счётчиками, либо FAILED с сообщением об ошибке),
 * а не жёсткий 501 из заглушки итерации 3
 * <p>
 * ВНИМАНИЕ: {@code startTrace} предполагает, что у {@code TraceAcceptedResponse}
 * есть геттер {@code getTraceId()} (сам класс мне не присылали — только
 * использование его 2-аргументного конструктора в {@code TraceService}).
 * Если геттер называется иначе — поправьте один вызов ниже
 */
@Slf4j
@RestController
@RequestMapping("/api/trace")
@Tag(name = "Trace Modeling",
     description = "Моделирование и построение трасс подключения "
             + "к тепловым сетям")
public class TraceController {

    private final TraceService traceService;
    private final TraceAsyncProcessor traceAsyncProcessor;

    public TraceController(TraceService traceService, TraceAsyncProcessor traceAsyncProcessor) {
        this.traceService = traceService;
        this.traceAsyncProcessor = traceAsyncProcessor;
    }

    /**
     * Запуск моделирования трассы для указанной сессии загрузки.
     * Регистрирует сессию (статус {@code PENDING}) и сразу передает ее
     * в фоновую обработку - сам HTTP-запрос не ждет завершения расчета
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
                    description = "Загрузка uploadId не найдена")
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
     * @return 200 OK с текущим статусом ({@code TraceStatus} в теле -
     * PENDING/PROCESSING/COMPLETED со счётчиками/FAILED с сообщением)
     */
    @GetMapping("/{traceId}")
    @Operation(summary = "Получить статус моделирования трасс",
               description = "Возвращает текущий статус задачи: "
                       + "PENDING, PROCESSING, COMPLETED (со счётчиками "
                       + "подключённых/неподключённых ОКС) или FAILED "
                       + "(с сообщением об ошибке)")
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
     * Отладочный эндпоинт Итерации 4 для контроля выбора точек врезки
     * @param traceId идентификатор задачи трассировки
     * @return 200 OK со списком TieInCandidate
     */
    @GetMapping("/{traceId}/candidates")
    @Operation(summary = "Получить кандидатов на присоединение",
               description = "Возвращает список кандидатов на присоединение к тепловой сети "
                       + "для точек ОКС сессии трассировки")
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

    @ExceptionHandler(UploadNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUploadNotFound(
            UploadNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(TraceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleTraceNotFound(
            TraceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }
}
