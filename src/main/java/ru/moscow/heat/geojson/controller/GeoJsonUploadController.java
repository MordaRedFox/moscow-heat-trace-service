package ru.moscow.heat.geojson.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.moscow.heat.geojson.dto.UploadAcceptedResponse;
import ru.moscow.heat.geojson.dto.UploadStatusResponse;
import ru.moscow.heat.geojson.exception.GeoJsonParseException;
import ru.moscow.heat.geojson.service.GeoJsonUploadService;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * REST-контроллер для загрузки GeoJSON и отслеживания статуса обработки
 */
@Slf4j
@RestController
@RequestMapping("/api/geojson")
@Tag(name = "Upload GeoJson",
     description = "Загрузка и обработка GeoJSON")
public class GeoJsonUploadController {

    private final GeoJsonUploadService service;

    public GeoJsonUploadController(GeoJsonUploadService service) {
        this.service = service;
    }

    /**
     * Принимает файл, сохраняет во временное хранилище и
     * запускает асинхронную обработку
     * @param file загружаемый GeoJSON (до 3 ГБ)
     * @return 202 Accepted с идентификатором загрузки и URL статуса
     */
    @PostMapping(value = "/upload",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Загрузить файл",
            description = "Принимает файл, ставит задачу в очередь "
                    + "и возвращает uploadId")
    public ResponseEntity<UploadAcceptedResponse> upload(
            @RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.accepted()
                .body(service.acceptUpload(file));
    }

    /**
     * Возвращает текущий статус обработки загрузки
     * @param id идентификатор загрузки
     * @return статус, счётчики, bbox и ошибки валидации
     */
    @GetMapping("/uploads/{id}")
    @Operation(summary = "Статус обработки загрузки",
            description = "Возвращает статус, счётчики, bbox "
                    + "и ошибки валидации")
    public ResponseEntity<UploadStatusResponse> status(
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.getStatus(id));
    }

    /** Ошибка парсинга или структурной валидации - HTTP 400 */
    @ExceptionHandler(GeoJsonParseException.class)
    public ResponseEntity<Map<String, String>> handleParseError(
            GeoJsonParseException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }

    /** Некорректный JSON во входном файле - HTTP 400 */
    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<Map<String, String>> handleJsonError(
            JsonProcessingException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error",
                        "Некорректный JSON: " + ex.getOriginalMessage()));
    }

    /** Ошибка ввода-вывода при чтении файла - HTTP 500 */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> handleIoError(
            IOException ex) {
        log.error("IO error while processing GeoJSON", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error",
                        "Ошибка чтения файла: " + ex.getMessage()));
    }
}
