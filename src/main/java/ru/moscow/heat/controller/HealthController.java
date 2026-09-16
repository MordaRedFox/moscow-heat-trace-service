package ru.moscow.heat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Health", description = "Проверка работоспособности сервиса")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Health check",
        description = "Возвращает статус сервиса и текущее время")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "heat-trace-service",
                "timestamp", OffsetDateTime.now().toString()
        ));
    }
}
