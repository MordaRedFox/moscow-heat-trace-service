package ru.moscow.heat.trace.graph;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.repository.RestrictionRepository;
import ru.moscow.heat.spatial.RestrictionRuleRegistry;

/**
 * Строит {@link ObstacleModel} из restriction-объектов загрузки и правил
 * {@code RestrictionRuleRegistry} (план, шаг 3).
 * <p>
 * Алгоритм (план, п.2.3 и шаг 3):
 * <ol>
 *     <li>для каждого restriction взять правило по restriction_type;</li>
 *     <li>отступ = minHorizontalDistance + maxPairWidth/2 (maxPairWidth = 3.45 м для ДУ 1400);</li>
 *     <li>построить буфер геометрии (полигон или линия) в UTM;</li>
 *     <li>пометить как FORBIDDEN (RestrictionRuleType.FORBIDDEN) либо SPECIAL
 *     (RestrictionRuleType.SPECIAL_CROSSING) с записанным Kспец.</li>
 * </ol>
 * <p>
 */
@Service
@RequiredArgsConstructor
public class ObstacleModelBuilder {

    private final RestrictionRepository restrictionRepository;
    private final RestrictionRuleRegistry ruleRegistry;

    /**
     * Строит модель препятствий для загрузки.
     *
     * @param uploadId id загрузки (сессии GeoJSON)
     * @return подготовленная модель с раздутыми геометриями в UTM
     */
    public ObstacleModel build(Long uploadId) {
        // TODO:
        // 1. restrictionRepository.findByUploadId(uploadId)
        // 2. для каждого: ruleRegistry.getRule(restriction.getRestrictionType())
        // 3. отступ = rule.getMinHorizontalDistance() + MAX_PAIR_WIDTH_M / 2
        // 4. geometry.buffer(отступ) в UTM (geom_utm уже посчитан при загрузке)
        // 5. разложить по FORBIDDEN / SPECIAL согласно rule.getType()
        throw new UnsupportedOperationException("TODO: итерация 5, шаг 3");
    }
}
