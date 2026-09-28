package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.service.CoordinateTransformService;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TieInType;
import ru.moscow.heat.trace.model.OksGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Группирует ОКС для совместного подключения (итерация 6, шаг 1).
 * <p>
 * Правила группировки:
 * <ol>
 *     <li><b>По общей камере:</b> ОКС, у которых выбранный кандидат имеет
 *     одинаковый ненулевой {@code existingChamberId}, объединяются в одну
 *     группу.</li>
 *     <li><b>По радиусу:</b> ОКС, у которых точки врезки (tie-in) находятся
 *     в пределах {@link #JOINT_TIE_IN_RADIUS_M} метров друг от друга,
 *     объединяются. Радиусное объединение применяется только к парам
 *     (без транзитивности через цепочки), чтобы избежать «ползущих»
 *     групп.</li>
 *     <li><b>Одиночные:</b> ОКС, не попавшие ни в одну группу, формируют
 *     группы из одного элемента.</li>
 * </ol>
 * <p>
 * Приоритет: камерное объединение первично. Если ОКС уже в камерной группе,
 * радиусное объединение к нему не применяется.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OksGrouper {

    /** Радиус объединения точек врезки, метры (план итерации 6). */
    public static final double JOINT_TIE_IN_RADIUS_M = 30.0;

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final int WGS84_SRID = 4326;

    private final CoordinateTransformService coordinateTransformService;

    /**
     * Выполняет группировку ОКС по их кандидатам врезки.
     *
     * @param oksList    все ОКС загрузки
     * @param candidatesByOksId карта: featureId ОКС → выбранный кандидат
     * @return список групп (каждая содержит ≥ 1 ОКС)
     */
    public List<OksGroup> group(List<OksConnectionPointEntity> oksList,
                                Map<String, TieInCandidate> candidatesByOksId) {
        if (oksList.isEmpty()) {
            return List.of();
        }

        // Индексируем ОКС по featureId для быстрого доступа
        Map<String, OksConnectionPointEntity> oksById = new HashMap<>();
        for (OksConnectionPointEntity oks : oksList) {
            oksById.put(oks.getFeatureId(), oks);
        }

        // --- Шаг 1: камерная группировка (по existingChamberId) ---
        Map<String, List<String>> chamberGroups = new HashMap<>();
        List<String> ungrouped = new ArrayList<>();

        for (OksConnectionPointEntity oks : oksList) {
            TieInCandidate candidate = candidatesByOksId.get(oks.getFeatureId());
            if (candidate == null) {
                // ОКС без кандидата не участвует в группировке
                continue;
            }
            if (candidate.getType() == TieInType.EXISTING_CHAMBER
                    && candidate.getExistingChamberId() != null) {
                chamberGroups
                        .computeIfAbsent(candidate.getExistingChamberId(), k -> new ArrayList<>())
                        .add(oks.getFeatureId());
            } else {
                ungrouped.add(oks.getFeatureId());
            }
        }

        // Формируем камерные группы (только если в группе > 1 ОКС,
        // иначе переводим в разряд одиночных)
        List<OksGroup> result = new ArrayList<>();
        List<String> forRadiusGrouping = new ArrayList<>(ungrouped);

        for (Map.Entry<String, List<String>> entry : chamberGroups.entrySet()) {
            List<String> memberIds = entry.getValue();
            if (memberIds.size() == 1) {
                // Одиночный ОКС с камерным кандидатом — не группа
                forRadiusGrouping.add(memberIds.get(0));
            } else {
                result.add(buildGroup(memberIds, oksById, candidatesByOksId, true));
            }
        }

        // --- Шаг 2: радиусная группировка (для оставшихся) ---
        List<OksGroup> radiusGroups = groupByRadius(forRadiusGrouping, oksById, candidatesByOksId);
        result.addAll(radiusGroups);

        log.info("OksGrouper: {} ОКС → {} групп (камерных: {}, радиусных/одиночных: {})",
                oksList.size(), result.size(),
                chamberGroups.values().stream().filter(v -> v.size() > 1).count(),
                radiusGroups.size());

        return result;
    }

    /**
     * Радиусная группировка: объединяем пары ОКС, чьи точки врезки
     * в пределах JOINT_TIE_IN_RADIUS_M. Без транзитивности.
     */
    private List<OksGroup> groupByRadius(List<String> oksIds,
                                         Map<String, OksConnectionPointEntity> oksById,
                                         Map<String, TieInCandidate> candidatesByOksId) {
        List<OksGroup> groups = new ArrayList<>();
        boolean[] used = new boolean[oksIds.size()];

        // Конвертируем tie-in точки в UTM для расчёта расстояний
        Coordinate[] tieInUtm = new Coordinate[oksIds.size()];
        for (int i = 0; i < oksIds.size(); i++) {
            TieInCandidate c = candidatesByOksId.get(oksIds.get(i));
            tieInUtm[i] = toUtmCoordinate(c);
        }

        for (int i = 0; i < oksIds.size(); i++) {
            if (used[i]) {
                continue;
            }
            List<String> groupMembers = new ArrayList<>();
            groupMembers.add(oksIds.get(i));
            used[i] = true;

            for (int j = i + 1; j < oksIds.size(); j++) {
                if (used[j]) {
                    continue;
                }
                double dist = tieInUtm[i].distance(tieInUtm[j]);
                if (dist <= JOINT_TIE_IN_RADIUS_M) {
                    groupMembers.add(oksIds.get(j));
                    used[j] = true;
                }
            }

            groups.add(buildGroup(groupMembers, oksById, candidatesByOksId, false));
        }

        return groups;
    }

    /**
     * Строит OksGroup из списка featureId участников.
     * Общий tie-in — кандидат первого участника (представительный).
     */
    private OksGroup buildGroup(List<String> memberIds,
                                Map<String, OksConnectionPointEntity> oksById,
                                Map<String, TieInCandidate> candidatesByOksId,
                                boolean byChamber) {
        List<OksConnectionPointEntity> points = new ArrayList<>();
        List<TieInCandidate> candidates = new ArrayList<>();

        for (String id : memberIds) {
            OksConnectionPointEntity oks = oksById.get(id);
            TieInCandidate candidate = candidatesByOksId.get(id);
            if (oks != null && candidate != null) {
                points.add(oks);
                candidates.add(candidate);
            }
        }

        if (points.isEmpty()) {
            throw new IllegalStateException("Пустая группа после фильтрации");
        }

        // Общий tie-in: для камерной группы — первый кандидат (все указывают
        // на одну камеру). Для радиусной — тоже первый (представительный).
        TieInCandidate sharedTieIn = candidates.get(0);

        return new OksGroup(points, candidates, sharedTieIn, byChamber);
    }

    /**
     * Конвертирует tie-in точку кандидата из WGS84 в UTM.
     */
    private Coordinate toUtmCoordinate(TieInCandidate candidate) {
        Point wgs84 = GEOMETRY_FACTORY.createPoint(
                new Coordinate(candidate.getTieInLongitude(),
                        candidate.getTieInLatitude()));
        wgs84.setSRID(WGS84_SRID);
        Geometry utm = coordinateTransformService.toUtm(wgs84);
        return utm.getCoordinate();
    }
}
