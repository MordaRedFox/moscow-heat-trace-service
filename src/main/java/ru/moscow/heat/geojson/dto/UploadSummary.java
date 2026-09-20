package ru.moscow.heat.geojson.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.moscow.heat.geojson.FeatureError;
import ru.moscow.heat.geojson.ObjectType;

import java.util.List;
import java.util.Map;

/**
 * Компактная сводка по загрузке, сериализуется в jsonb-колонку
 * {@code upload_session.summary}. Используется, чтобы не читать
 * всю таблицу geo_feature при GET статусе
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadSummary {
    private Map<ObjectType, Integer> countsByType;
    private List<Double> bbox;
    private int totalErrorsCount;
    private List<FeatureError> errors;
    private boolean errorsTruncated;
}
