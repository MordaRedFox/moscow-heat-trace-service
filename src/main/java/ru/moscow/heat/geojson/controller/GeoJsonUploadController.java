package ru.moscow.heat.geojson.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.moscow.heat.geojson.dto.GeoJsonUploadResponse;
import ru.moscow.heat.geojson.exception.GeoJsonParseException;
import ru.moscow.heat.geojson.service.GeoJsonUploadService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/geojson")
@Tag(name = "Upload GeoJson", description = "Загрузка GeoJson")
public class GeoJsonUploadController {

    private final GeoJsonUploadService service;

    public GeoJsonUploadController(GeoJsonUploadService service) {
        this.service = service;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GeoJsonUploadResponse> upload(@RequestParam("file") MultipartFile file)
            throws IOException {
        try (InputStream is = file.getInputStream()) {
            return ResponseEntity.ok(service.processStream(is));
        }
    }

    @ExceptionHandler(GeoJsonParseException.class)
    public ResponseEntity<Map<String, String>> handleParseError(GeoJsonParseException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> handleIoError(IOException ex) {
        log.error("IO error while processing GeoJSON", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Ошибка чтения файла: " + ex.getMessage()));
    }
}
