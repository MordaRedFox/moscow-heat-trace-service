package ru.moscow.heat.trace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Ответ с текущим статусом задачи трассировки.
 */
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ответ со статусом задачи трассировки")
public class TraceStatusResponse {

    @Schema(description = "Уникальный идентификатор задачи трассировки",
            example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID traceId;

    @Schema(description = "Текущий статус выполнения задачи",
            example = "NOT_IMPLEMENTED")
    private TraceStatus status;

    @Schema(description = "Время создания задачи",
            example = "2026-09-22T00:00:00Z")
    private Instant createdAt;
}
