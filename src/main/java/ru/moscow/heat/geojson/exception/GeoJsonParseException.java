package ru.moscow.heat.geojson.exception;

/**
 * Ошибка парсинга или структурной валидации GeoJSON. Обрабатывается
 * контроллером и приводит к HTTP 400
 */
public class GeoJsonParseException extends RuntimeException {

    /**
     * Создает исключение с текстовым описанием
     * @param message описание ошибки
     */
    public GeoJsonParseException(String message) {
        super(message);
    }

    /**
     * Создает исключение с описанием и первопричиной
     * @param message описание ошибки
     * @param cause   исходное исключение
     */
    public GeoJsonParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
