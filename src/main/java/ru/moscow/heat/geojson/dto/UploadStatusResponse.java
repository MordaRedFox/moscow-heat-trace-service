package ru.moscow.heat.geojson.dto;

import lombok.Builder;
import lombok.Getter;
import ru.moscow.heat.geojson.FeatureError;
import ru.moscow.heat.geojson.ObjectType;
import ru.moscow.heat.geojson.UploadStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Публичный ответ GET /uploads/{id}
 * Возвращает статус, временные метки, счетчики, bbox и ошибки валидации
 */
@Getter
@Builder
public class UploadStatusResponse {
    private UUID uploadId;
    private UploadStatus status;
    private String fileName;
    private long fileSize;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private String errorMessage;
    private Integer totalCount;
    private Integer totalErrorsCount;
    private Map<ObjectType, Integer> countsByType;
    private List<Double> bbox;
    private List<FeatureError> errors;
    private boolean errorsTruncated;
}
