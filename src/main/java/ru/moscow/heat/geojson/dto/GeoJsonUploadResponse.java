package ru.moscow.heat.geojson.dto;

import lombok.Getter;
import ru.moscow.heat.geojson.FeatureError;
import ru.moscow.heat.geojson.ObjectType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Внутренний результат потокового парсинга GeoJSON
 * Собирает счетчики по типам, bbox и список ошибок валидации
 */
@Getter
public class GeoJsonUploadResponse {

    private static final int MAX_ERRORS = 1000;

    private final Map<ObjectType, Integer> countsByType =
            new EnumMap<>(ObjectType.class);
    private final List<FeatureError> errors = new ArrayList<>();

    private int totalCount = 0;
    private int totalErrorsCount = 0;

    private Double minX;
    private Double minY;
    private Double maxX;
    private Double maxY;

    /**
     * Увеличивает счетчик объектов указанного типа
     * @param type тип объекта входного GeoJSON
     */
    public void incrementCount(ObjectType type) {
        countsByType.merge(type, 1, Integer::sum);
        totalCount++;
    }

    /**
     * Добавляет ошибку валидации. Список обрезается по {@link #MAX_ERRORS};
     * общее число ошибок при этом продолжает расти
     * @param featureId идентификатор проблемного объекта
     * @param message   текст ошибки
     */
    public void addError(String featureId, String message) {
        totalErrorsCount++;
        if (errors.size() < MAX_ERRORS) {
            errors.add(new FeatureError(featureId, message));
        }
    }

    /**
     * Признак того, что список ошибок был обрезан по лимиту
     * @return {@code true}, если сохранено меньше ошибок, чем
     *         зафиксировано в {@link #totalErrorsCount}
     */
    public boolean isErrorsTruncated() {
        return totalErrorsCount > errors.size();
    }

    /**
     * Расширяет bbox по очередной координате
     * @param x долгота
     * @param y широта
     */
    public void updateBbox(double x, double y) {
        if (minX == null || x < minX) minX = x;
        if (minY == null || y < minY) minY = y;
        if (maxX == null || x > maxX) maxX = x;
        if (maxY == null || y > maxY) maxY = y;
    }

    /**
     * Признак того, что bbox заполнен хотя бы одной точкой
     * @return {@code true}, если все четыре границы заданы
     */
    public boolean hasBbox() {
        return minX != null && minY != null
                && maxX != null && maxY != null;
    }

    /**
     * Возвращает bbox в порядке [minX, minY, maxX, maxY]
     * @return список из четырёх координат или {@code null},
     *         если bbox пуст
     */
    public List<Double> getBbox() {
        if (!hasBbox()) {
            return null;
        }
        return List.of(minX, minY, maxX, maxY);
    }
}
