package ru.moscow.heat.trace.graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Geometry;
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
 * Строит {@link ObstacleModel} из restriction-объектов загрузки и правил
 * {@link RestrictionRuleRegistry} (план, шаг 3).
 * <p>
 * Работает напрямую в UTM через {@code RestrictionEntity.getGeometryUtm()}
 * (колонка {@code geometry_utm} уже посчитана и хранится в БД — см.
 * {@code AbstractGeoObject}), поэтому {@code GeometryUtils}/трансформация
 * координат здесь не нужны: {@code Geometry.buffer(distanceMeters)} в UTM
 * даёт ровно нужный результат (полигон/линия, раздутые на distanceMeters
 * во все стороны).
 * <p>
 * Алгоритм:
 * <ol>
 *     <li>для каждого restriction найти правило по {@code restrictionType};
 *     если тип неизвестен реестру — объект пропускается с предупреждением
 *     в лог (данные конкурсного набора не должны такого содержать, но
 *     сервис не должен падать на незнакомом типе);</li>
 *     <li>определить одностороннее нормативное расстояние (clearance):
 *     <ul>
 *         <li>для {@code oks} — {@link RestrictionRuleRegistry#minDistanceForDiameter(int)}
 *         по максимальному ДУ из справочника (MVP-допущение риска R3:
 *         единый отступ по максимальному ДУ, без пересчёта графа под
 *         диаметр конкретного маршрута);</li>
 *         <li>для остальных типов — {@code rule.getMinHorizontalDistanceM()};</li>
 *     </ul></li>
 *     <li>{@code restriction.getGeometryUtm().buffer(clearanceM)} — буфер
 *     в UTM (в отличие от {@code GeometryUtils.envelopeAround}, здесь
 *     расстояние НЕ удваивается: {@code Geometry.buffer(d)} уже откладывает
 *     {@code d} наружу по всему периметру полигона/линии);</li>
 *     <li>разложить по FORBIDDEN / SPECIAL_CROSSING согласно {@code rule.getRuleType()}.</li>
 * </ol>
 * <p>
 * MVP-допущение: для SPECIAL_CROSSING-типов та же буферизация по
 * {@code minHorizontalDistanceM} используется как единая зона, в которой
 * пересечение разрешено с коэффициентом Kспец. Разделение "требование
 * бокового сближения вне точки пересечения" и "условия непосредственно
 * в точке пересечения" (специальный проход + specialZonePaddingM) в
 * этой версии не делается — см. риск в сопроводительной документации.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObstacleModelBuilder {

    /**
     * Максимальный ДУ справочника (табл. 1 ТП), используемый для расчёта
     * единого (не зависящего от диаметра конкретного маршрута) отступа
     * от полигонов ОКС при построении графа видимости.
     */
    private static final int MAX_DIAMETER_MM = 1400;

    private final RestrictionRepository restrictionRepository;
    private final RestrictionRuleRegistry ruleRegistry;

    /**
     * Строит модель препятствий для загрузки.
     *
     * @param uploadId id загрузки (сессии GeoJSON)
     * @return подготовленная модель с раздутыми геометриями (UTM)
     */
    public ObstacleModel build(UUID uploadId) {
        List<RestrictionEntity> restrictions = restrictionRepository.findByUploadId(uploadId);

        List<Geometry> forbidden = new ArrayList<>();
        List<ObstacleModel.SpecialZone> special = new ArrayList<>();

        for (RestrictionEntity restriction : restrictions) {
            Optional<RestrictionRule> ruleOpt = ruleRegistry.ruleFor(restriction.getRestrictionType());
            if (ruleOpt.isEmpty()) {
                log.warn("Неизвестный restriction_type='{}' (feature_id={}) — объект пропущен при построении графа препятствий",
                        restriction.getRestrictionType(), restriction.getFeatureId());
                continue;
            }
            RestrictionRule rule = ruleOpt.get();

            double clearanceM = isOks(restriction.getRestrictionType())
                    ? ruleRegistry.minDistanceForDiameter(MAX_DIAMETER_MM)
                    : rule.getMinHorizontalDistanceM();

            Geometry bufferedUtm = restriction.getGeometryUtm().buffer(clearanceM);

            if (rule.getRuleType() == RestrictionRuleType.FORBIDDEN) {
                forbidden.add(bufferedUtm);
            } else {
                double kspets = rule.getKspets() != null ? rule.getKspets() : 1.0;
                special.add(new ObstacleModel.SpecialZone(
                        bufferedUtm, kspets, restriction.getRestrictionType(), rule.getMinCrossingAngleDeg()));
            }
        }

        log.info("ObstacleModel для uploadId={} построена: {} запретных зон, {} зон спецперехода",
                uploadId, forbidden.size(), special.size());

        return new ObstacleModel(forbidden, special);
    }

    private boolean isOks(String restrictionType) {
        return "oks".equalsIgnoreCase(restrictionType);
    }
}
