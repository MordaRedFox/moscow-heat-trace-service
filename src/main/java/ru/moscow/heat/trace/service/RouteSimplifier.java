package ru.moscow.heat.trace.service;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.springframework.stereotype.Service;
import ru.moscow.heat.trace.graph.ObstacleModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Постобработка сырого пути из {@code VisibilityGraph#shortestPath}
 * (план, шаг 6): устраняет зигзаги «жадным» string-pulling алгоритмом
 * <p>Работает в UTM. Использует ту же семантику проверки, что и граф
 * видимости ({@code crosses} + envelope prefilter + PreparedGeometry +
 * STRtree из {@link ObstacleModel}), чтобы string-pulling корректно
 * склеивал сегменты вдоль границ препятствий и при этом не тормозил
 * на загрузках с десятками restriction-полигонов
 */
@Service
public class RouteSimplifier {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final double VISIBILITY_EPSILON_RATIO = 0.001;

    /**
     * Упрощает путь, сохраняя геометрическую корректность относительно
     * препятствий
     * @param rawPathUtm           путь из visibility graph, координаты UTM
     * @param obstacleModel        модель препятствий
     * @param ignoredForbiddenIds  id запретных зон, игнорируемых при
     *                             проверке видимости; обычно содержит id
     *                             полигона ОКС, содержащего стартовую точку
     * @return упрощённый путь (минимум 2 точки, если на входе было ≥2)
     */
    public List<Coordinate> simplify(List<Coordinate> rawPathUtm,
                                      ObstacleModel obstacleModel,
                                      Set<Long> ignoredForbiddenIds) {
        if (rawPathUtm.size() <= 2) {
            return new ArrayList<>(rawPathUtm);
        }
        Set<Long> ignored = ignoredForbiddenIds != null
                ? ignoredForbiddenIds
                : Collections.emptySet();

        List<Coordinate> result = new ArrayList<>();
        result.add(rawPathUtm.get(0));

        int i = 0;
        while (i < rawPathUtm.size() - 1) {
            int farthest = i + 1;
            for (int j = rawPathUtm.size() - 1; j > i + 1; j--) {
                if (isSegmentClear(rawPathUtm.get(i), rawPathUtm.get(j),
                        obstacleModel, i == 0 ? ignored : Collections.emptySet())) {
                    farthest = j;
                    break;
                }
            }
            result.add(rawPathUtm.get(farthest));
            i = farthest;
        }

        return result;
    }

    /**
     * Проверяет, что отрезок A-B не проходит сквозь внутренность ни одной
     * FORBIDDEN-зоны (кроме игнорируемых). Касание границы допускается
     * <p>Использует STRtree из {@link ObstacleModel} для быстрого поиска
     * только тех зон, чей envelope пересекает envelope сегмента
     */
    private boolean isSegmentClear(Coordinate a, Coordinate b,
                                    ObstacleModel obstacleModel,
                                    Set<Long> ignoredForbiddenIds) {
        LineString segment = shrunkSegment(a, b);
        LineString rawSeg = GEOMETRY_FACTORY.createLineString(new Coordinate[]{a, b});
        Envelope env = rawSeg.getEnvelopeInternal();
        for (ObstacleModel.ForbiddenZone zone : obstacleModel.findForbiddenNear(env)) {
            Long id = zone.getSourceRestrictionId();
            if (id != null && ignoredForbiddenIds.contains(id)) {
                continue;
            }
            if (!env.intersects(zone.getEnvelope())) {
                continue;
            }
            // 1. Физическое тело здания / препятствия: пересечение категорически запрещено
            if (zone.getPreparedSourceGeometry() != null) {
                if (zone.getPreparedSourceGeometry().intersects(rawSeg)) {
                    return false;
                }
            } else if (zone.getSourceGeometryUtm() != null) {
                if (zone.getSourceGeometryUtm().intersects(rawSeg)) {
                    return false;
                }
            }
            // 2. Буферная зона: транзитное пересечение запрещено
            if (zone.getPreparedGeometry().crosses(segment)) {
                return false;
            }
        }
        return true;
    }

    private LineString shrunkSegment(Coordinate a, Coordinate b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        Coordinate sa = new Coordinate(a.x + dx * VISIBILITY_EPSILON_RATIO,
                a.y + dy * VISIBILITY_EPSILON_RATIO);
        Coordinate sb = new Coordinate(b.x - dx * VISIBILITY_EPSILON_RATIO,
                b.y - dy * VISIBILITY_EPSILON_RATIO);
        return GEOMETRY_FACTORY.createLineString(new Coordinate[]{sa, sb});
    }
}
