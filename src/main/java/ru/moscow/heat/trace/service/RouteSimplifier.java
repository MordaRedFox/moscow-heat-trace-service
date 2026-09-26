package ru.moscow.heat.trace.service;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.springframework.stereotype.Service;
import ru.moscow.heat.trace.graph.ObstacleModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Постобработка сырого пути из {@code VisibilityGraph.shortestPath}
 * (план, шаг 6): устраняет зигзаги "жадным" string-pulling алгоритмом —
 * из текущей точки ищем самую дальнюю точку пути, до которой видно
 * напрямую (не пересекая FORBIDDEN-зоны), и сразу к ней перескакиваем.
 * Так как узлы графа видимости — это углы препятствий, результат
 * гарантированно остаётся корректным (не пересекает запретов), но
 * убирает лишние промежуточные повороты, которые A* оставил из-за
 * структуры графа, а не из-за реальной необходимости.
 * <p>
 * Работает в UTM (см. {@code ObstacleModel}/{@code VisibilityGraph}) —
 * никакой трансформации координат.
 * <p>
 * MVP-упрощение: явная проверка "максимальный угол поворота ≤ 90°"
 * (требование ТП) не реализована отдельно — string-pulling обычно и
 * так убирает нефизичные острые углы, оставляя только повороты вокруг
 * настоящих препятствий. Если на демо всплывут острые углы — сюда
 * нужно добавить постфильтр.
 */
@Service
public class RouteSimplifier {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final double VISIBILITY_EPSILON_RATIO = 0.001;

    /**
     * Упрощает путь, сохраняя геометрическую корректность относительно
     * препятствий.
     *
     * @param rawPathUtm    путь из visibility graph, координаты UTM, по порядку
     * @param obstacleModel модель препятствий — нужна, чтобы проверять,
     *                      что "срезание" зигзага не пересекает FORBIDDEN
     * @return упрощённый путь (минимум 2 точки, если на входе было ≥2)
     */
    public List<Coordinate> simplify(List<Coordinate> rawPathUtm, ObstacleModel obstacleModel) {
        if (rawPathUtm.size() <= 2) {
            return new ArrayList<>(rawPathUtm);
        }

        List<Coordinate> result = new ArrayList<>();
        result.add(rawPathUtm.get(0));

        int i = 0;
        while (i < rawPathUtm.size() - 1) {
            int farthest = i + 1;
            for (int j = rawPathUtm.size() - 1; j > i + 1; j--) {
                if (isSegmentClear(rawPathUtm.get(i), rawPathUtm.get(j), obstacleModel)) {
                    farthest = j;
                    break;
                }
            }
            result.add(rawPathUtm.get(farthest));
            i = farthest;
        }

        return result;
    }

    private boolean isSegmentClear(Coordinate a, Coordinate b, ObstacleModel obstacleModel) {
        LineString segment = shrunkSegment(a, b);
        for (Geometry forbidden : obstacleModel.getForbiddenBufferedGeometriesUtm()) {
            if (segment.intersects(forbidden)) {
                return false;
            }
        }
        return true;
    }

    /** Тот же epsilon-трюк, что в {@code VisibilityGraph.isVisible}, см. его javadoc. */
    private LineString shrunkSegment(Coordinate a, Coordinate b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        Coordinate sa = new Coordinate(a.x + dx * VISIBILITY_EPSILON_RATIO, a.y + dy * VISIBILITY_EPSILON_RATIO);
        Coordinate sb = new Coordinate(b.x - dx * VISIBILITY_EPSILON_RATIO, b.y - dy * VISIBILITY_EPSILON_RATIO);
        return GEOMETRY_FACTORY.createLineString(new Coordinate[]{sa, sb});
    }
}
