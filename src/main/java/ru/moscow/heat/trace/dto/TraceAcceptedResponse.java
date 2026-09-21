package ru.moscow.heat.trace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

/**
 * Ответ при успешном приеме задачи трассировки (HTTP 202 Accepted).
 */
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ответ при приеме задачи трассировки")
public class TraceAcceptedResponse {

    @Schema(description = "Уникальный идентификатор задачи трассировки",
            example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID traceId;

    @Schema(description = "URL для опроса статуса задачи",
            example = "/api/trace/550e8400-e29b-41d4-a716-446655440000")
    private String statusUrl;
}
