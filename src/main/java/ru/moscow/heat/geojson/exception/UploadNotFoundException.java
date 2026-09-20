package ru.moscow.heat.geojson.exception;

/**
 * Ошибка «загрузка не найдена». Обрабатывается контроллером
 * и приводит к HTTP 404, в отличие от {@link GeoJsonParseException},
 * которая возвращает HTTP 400
 */
public class UploadNotFoundException extends RuntimeException {

    /**
     * Создает исключение с текстовым описанием
     * @param message описание ошибки
     */
    public UploadNotFoundException(String message) {
        super(message);
    }
}
