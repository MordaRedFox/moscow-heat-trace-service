package ru.moscow.heat.trace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Тип присоединения новой сети к существующей
 */
@Schema(description = "Тип точки присоединения")
public enum TieInType {

    /** Присоединение к существующей тепловой камере */
    EXISTING_CHAMBER,

    /** Присоединение через новую тепловую камеру */
    NEW_CHAMBER
}
