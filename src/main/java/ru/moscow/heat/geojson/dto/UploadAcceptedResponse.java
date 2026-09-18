package ru.moscow.heat.geojson.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import ru.moscow.heat.geojson.UploadStatus;

import java.util.UUID;

/**
 * Ответ 202 Accepted на загрузку файла
 * Содержит идентификатор загрузки и URL для опроса статуса
 */
@Getter
@AllArgsConstructor
public class UploadAcceptedResponse {
    private final UUID uploadId;
    private final UploadStatus status;
    private final String statusUrl;
}
