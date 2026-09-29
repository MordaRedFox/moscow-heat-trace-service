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
 *     <li><b>По общей камере:</b> ОКС с одинаковым {@code existingChamberId};</li>
 *     <li><b>По радиусу:</b> ОКС, чьи tie-in точки в пределах
 *         {@link #JOINT_TIE_IN_RADIUS_M} метров друг от друга;</li>
 *     <li><b>Одиночные:</b> ОКС, не попавшие ни в одну группу.</li>
 * </ol>
 *
 * <p>Флаг {@code noGroup} отключает группировку — каждый ОКС становится
 * отдельной группой. Используется в стратегии
 * {@code TraceOrchestrator.TraceStrategy#NO_GROUP} для формирования
 * варианта без объединения.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OksGrouper {

    /** Радиус объединения точек врезки, метры. */
    public static final double JOINT_TIE_IN_RADIUS_M = 30.0;

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final int WGS84_SRID = 4326;

    private final CoordinateTransformService coordinateTransformService;

    /**
     * Группировка с основными правилами (эквивалент {@code noGroup = false}).
     *
     * @param oksList           все ОКС загрузки
     * @param candidatesByOksId карта featureId ОКС → выбранный кандидат
     * @return список групп
     */
    public List<OksGroup> group(List<OksConnectionPointEntity> oksList,
                                Map<String, TieInCandidate> candidatesByOksId) {
        return group(oksList, candidatesByOksId, false);
    }

    /**
     * Группировка с возможностью отключения объединения.
     *
     * @param oksList           все ОКС загрузки
     * @param candidatesByOksId карта featureId ОКС → выбранный кандидат
     * @param noGroup           {@code true} — каждый ОКС отдельной группой
     * @return список групп
     */
    public List<OksGroup> group(List<OksConnectionPointEntity> oksList,
                                Map<String, TieInCandidate> candidatesByOksId,
                                boolean noGroup) {
        if (oksList.isEmpty()) {
            return List.of();
        }
        if (noGroup) {
            return buildSingletons(oksList, candidatesByOksId);
        }

        Map<String, OksConnectionPointEntity> oksById = new HashMap<>();
        for (OksConnectionPointEntity oks : oksList) {
            oksById.put(oks.getFeatureId(), oks);
        }

        Map<String, List<String>> chamberGroups = new HashMap<>();
        List<String> ungrouped = new ArrayList<>();

        for (OksConnectionPointEntity oks : oksList) {
            TieInCandidate candidate = candidatesByOksId.get(oks.getFeatureId());
            if (candidate == null) continue;
            if (candidate.getType() == TieInType.EXISTING_CHAMBER
                    && candidate.getExistingChamberId() != null) {
                chamberGroups
                        .computeIfAbsent(candidate.getExistingChamberId(),
                                k -> new ArrayList<>())
                        .add(oks.getFeatureId());
            } else {
                ungrouped.add(oks.getFeatureId());
            }
        }

        List<OksGroup> result = new ArrayList<>();
        List<String> forRadiusGrouping = new ArrayList<>(ungrouped);

        for (Map.Entry<String, List<String>> entry : chamberGroups.entrySet()) {
            List<String> memberIds = entry.getValue();
            if (memberIds.size() == 1) {
                forRadiusGrouping.add(memberIds.get(0));
            } else {
                result.add(buildGroup(memberIds, oksById, candidatesByOksId, true));
            }
        }

        List<OksGroup> radiusGroups = groupByRadius(
                forRadiusGrouping, oksById, candidatesByOksId);
        result.addAll(radiusGroups);

        log.info("OksGrouper: {} ОКС → {} групп (камерных: {}, радиусных/одиночных: {})",
                oksList.size(), result.size(),
                chamberGroups.values().stream().filter(v -> v.size() > 1).count(),
                radiusGroups.size());

        return result;
    }

    /** Строит одиночные группы (каждый ОКС — отдельно). */
    private List<OksGroup> buildSingletons(List<OksConnectionPointEntity> oksList,
                                            Map<String, TieInCandidate> candidatesByOksId) {
        List<OksGroup> result = new ArrayList<>();
        for (OksConnectionPointEntity oks : oksList) {
            TieInCandidate c = candidatesByOksId.get(oks.getFeatureId());
            if (c == null) continue;
            boolean byChamber = c.getType() == TieInType.EXISTING_CHAMBER;
            result.add(new OksGroup(
                    List.of(oks),
                    List.of(c),
                    c,
                    byChamber));
        }
        log.info("OksGrouper (noGroup): {} ОКС → {} одиночных групп",
                oksList.size(), result.size());
        return result;
    }

    private List<OksGroup> groupByRadius(List<String> oksIds,
                                         Map<String, OksConnectionPointEntity> oksById,
                                         Map<String, TieInCandidate> candidatesByOksId) {
        List<OksGroup> groups = new ArrayList<>();
        boolean[] used = new boolean[oksIds.size()];

        Coordinate[] tieInUtm = new Coordinate[oksIds.size()];
        for (int i = 0; i < oksIds.size(); i++) {
            TieInCandidate c = candidatesByOksId.get(oksIds.get(i));
            tieInUtm[i] = toUtmCoordinate(c);
        }

        for (int i = 0; i < oksIds.size(); i++) {
            if (used[i]) continue;
            List<String> groupMembers = new ArrayList<>();
            groupMembers.add(oksIds.get(i));
            used[i] = true;

            for (int j = i + 1; j < oksIds.size(); j++) {
                if (used[j]) continue;
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

        return new OksGroup(points, candidates, candidates.get(0), byChamber);
    }

    private Coordinate toUtmCoordinate(TieInCandidate candidate) {
        Point wgs84 = GEOMETRY_FACTORY.createPoint(
                new Coordinate(candidate.getTieInLongitude(),
                        candidate.getTieInLatitude()));
        wgs84.setSRID(WGS84_SRID);
        Geometry utm = coordinateTransformService.toUtm(wgs84);
        return utm.getCoordinate();
    }
}
