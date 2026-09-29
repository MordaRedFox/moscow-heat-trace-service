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
 * Строит {@link ObstacleModel} из restriction-объектов загрузки.
 *
 * <p>Отступ от ОКС зависит от ДУ прокладываемой трубы (5/7/9 м по
 * разъяснениям п.3), но на этапе построения графа конкретный ДУ маршрута
 * неизвестен. Используется максимальный отступ
 * ({@link #MAX_DIAMETER_MM} = 1400 → 9 м) — консервативно и безопасно:
 * маршрут, проходящий граф, гарантированно соблюдает отступ для любого
 * допустимого ДУ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObstacleModelBuilder {

    /** Максимальный ДУ справочника — даёт максимальный отступ 9 м. */
    private static final int MAX_DIAMETER_MM = 1400;

    /** Допуск упрощения буферов, метры. */
    private static final double BUFFER_SIMPLIFY_TOLERANCE_M = 1.0;

    private final RestrictionRepository restrictionRepository;
    private final RestrictionRuleRegistry ruleRegistry;

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
                    ? ruleRegistry.minDistanceForDiameter(MAX_DIAMETER_MM)
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

    private boolean isOks(String restrictionType) {
        return "oks".equalsIgnoreCase(restrictionType);
    }
}
