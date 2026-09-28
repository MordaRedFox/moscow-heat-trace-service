package ru.moscow.heat.trace.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.simplify.TopologyPreservingSimplifier;
import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.entity.RestrictionEntity;
import ru.moscow.heat.geojson.repository.RestrictionRepository;
import ru.moscow.heat.spatial.RestrictionRule;
import ru.moscow.heat.spatial.RestrictionRuleRegistry;
import ru.moscow.heat.spatial.RestrictionRuleType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Строит {@link ObstacleModel} из restriction-объектов загрузки
 * <p>Работает в UTM через {@code RestrictionEntity.getGeometryUtm()}
 * <p>После буферизации геометрия упрощается {@link TopologyPreservingSimplifier}
 * с допуском {@link #BUFFER_SIMPLIFY_TOLERANCE_M}. Это критично для
 * производительности: полигоны ОКС в конкурсном наборе содержат
 * 500-1500 точек; без упрощения visibility graph получает тысячи углов
 * и строится десятки минут. Упрощение до 1 м сохраняет все значимые
 * изгибы контура и снижает число углов в разы
 * <p>Используется именно TopologyPreserving, а не обычный
 * Douglas-Peucker: первый сохраняет топологию полигона и не создаёт
 * самопересечений после упрощения, что критично для последующих
 * проверок {@code crosses} и {@code intersects}
 * <p>Отступ у всех препятствий — по максимальному ДУ (риск R3 плана)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObstacleModelBuilder {

    /** Базовый ДУ для расчета минимального отступа от ОКС (5 м для ДУ < 500 мм по п. 3 разъяснений ТП) */
    private static final int BASE_DIAMETER_MM = 400;

    /**
     * Допуск упрощения буферов (метры). 1 м - безопасный компромисс:
     * значимые изгибы контура сохраняются, мелкие зазубрины
     * (в т.ч. артефакты буферизации) уходят
     */
    private static final double BUFFER_SIMPLIFY_TOLERANCE_M = 1.0;

    private final RestrictionRepository restrictionRepository;
    private final RestrictionRuleRegistry ruleRegistry;

    /**
     * Строит модель препятствий для загрузки
     * @param uploadId id загрузки
     * @return подготовленная модель с раздутыми геометриями (UTM)
     */
    public ObstacleModel build(UUID uploadId) {
        List<RestrictionEntity> restrictions =
                restrictionRepository.findByUploadId(uploadId);

        List<ObstacleModel.ForbiddenZone> forbidden = new ArrayList<>();
        List<ObstacleModel.SpecialZone> special = new ArrayList<>();

        for (RestrictionEntity restriction : restrictions) {
            Optional<RestrictionRule> ruleOpt =
                    ruleRegistry.ruleFor(restriction.getRestrictionType());
            if (ruleOpt.isEmpty()) {
                log.warn("Неизвестный restriction_type='{}' (feature_id={}) — пропущен",
                        restriction.getRestrictionType(),
                        restriction.getFeatureId());
                continue;
            }
            RestrictionRule rule = ruleOpt.get();

            double clearanceM = isOks(restriction.getRestrictionType())
                    ? ruleRegistry.minDistanceForDiameter(BASE_DIAMETER_MM)
                    : rule.getMinHorizontalDistanceM();

            Geometry sourceGeom = restriction.getGeometryUtm();
            Geometry bufferedUtm = TopologyPreservingSimplifier.simplify(
                    sourceGeom.buffer(clearanceM),
                    BUFFER_SIMPLIFY_TOLERANCE_M);

            if (rule.getRuleType() == RestrictionRuleType.FORBIDDEN) {
                forbidden.add(new ObstacleModel.ForbiddenZone(
                        restriction.getId(), bufferedUtm, sourceGeom));
            } else {
                double kspets = rule.getKspets() != null ? rule.getKspets() : 1.0;
                special.add(new ObstacleModel.SpecialZone(
                        bufferedUtm, sourceGeom, kspets,
                        restriction.getRestrictionType(),
                        rule.getMinCrossingAngleDeg()));
            }
        }

        log.info("ObstacleModel для uploadId={} построена: "
                        + "{} запретных зон, {} зон спецперехода",
                uploadId, forbidden.size(), special.size());

        return new ObstacleModel(forbidden, special);
    }

    /**
     * @param restrictionType строковый тип ограничения
     * @return {@code true}, если тип соответствует полигону ОКС
     */
    private boolean isOks(String restrictionType) {
        return "oks".equalsIgnoreCase(restrictionType);
    }
}
