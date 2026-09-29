package ru.moscow.heat.trace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Статус задачи моделирования трасс подключения.
 */
@Schema(description = "Статус задачи трассировки")
public enum TraceStatus {
    @Schema(description = "Задача поставлена в очередь")
    PENDING,

    @Schema(description = "Выполняется алгоритм трассировки")
    PROCESSING,

    @Schema(description = "Трассировка успешно завершена")
    COMPLETED,

    @Schema(description = "Ошибка при выполнении трассировки")
    FAILED
}
