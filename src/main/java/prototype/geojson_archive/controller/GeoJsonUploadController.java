package prototype.geojson_archive.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import prototype.geojson_archive.dto.GeoJsonUploadSummary;
import prototype.geojson_archive.exception.GeoJsonParseException;
import prototype.geojson_archive.service.GeoJsonUploadService;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/geojson")
@Tag(name = "Upload GeoJson", description = "Загрузка GeoJson")
public class GeoJsonUploadController {
    private final GeoJsonUploadService service;

    public GeoJsonUploadController(GeoJsonUploadService service) {
        this.service = service;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GeoJsonUploadSummary> upload(@RequestParam("file") MultipartFile file) throws IOException, GeoJsonParseException {
        try (InputStream is = file.getInputStream()) {
            return ResponseEntity.ok(service.processStream(is));
        }
    }

    @ExceptionHandler(GeoJsonParseException.class)
    public ResponseEntity<String> handleParseError(GeoJsonParseException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

}
