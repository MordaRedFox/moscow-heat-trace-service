package ru.moscow.heat.geojson;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ошибка валидации одного объекта GeoJSON: идентификатор проблемного feature
 * и текст сообщения. Jackson-совместимый (десериализуется из jsonb-сводки)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FeatureError {
    private String featureId;
    private String message;
}
