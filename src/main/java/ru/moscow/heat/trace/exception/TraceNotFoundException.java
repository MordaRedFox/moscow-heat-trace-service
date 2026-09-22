package ru.moscow.heat.trace.exception;

import java.util.UUID;

/**
 * Исключение, выбрасываемое при обращении к неизвестной задаче
 * трассировки (HTTP 404)
 */
public class TraceNotFoundException extends RuntimeException {

    public TraceNotFoundException(UUID traceId) {
        super("Задача трассировки с traceId=" + traceId + " не найдена");
    }
}
