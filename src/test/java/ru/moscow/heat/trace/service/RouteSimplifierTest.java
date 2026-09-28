package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import ru.moscow.heat.trace.graph.ObstacleModel;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты {@link RouteSimplifier}
 * <p>
 * Проверяются:
 * <ul>
 *   <li>склеивание коллинеарных промежуточных точек (string-pulling);</li>
 *   <li>сохранение корректного обхода препятствия — путь не пересекает
 *       запретных зон, число точек не увеличивается;</li>
 *   <li>корректная работа с ignore-set (первый отрезок может
 *       выходить из своего полигона ОКС).</li>
 * </ul>
 * <p>Точное число вершин после string-pulling зависит от геометрии и
 * не является контрактом упростителя — он лишь удаляет лишние
 * промежуточные точки, если их можно срезать. Поэтому проверяется
 * не конкретное число, а сохранение корректности: упрощённый путь
 * не длиннее исходного по числу точек и не пересекает запретные зоны
 */
@DisplayName("Unit-тесты RouteSimplifier")
class RouteSimplifierTest {

    private static final GeometryFactory GF = new GeometryFactory();

    private RouteSimplifier simplifier;

    @BeforeEach
    void setUp() {
        simplifier = new RouteSimplifier();
    }

    @Test
    @DisplayName("Коллинеарные точки склеиваются")
    void collinearPointsMerged() {
        List<Coordinate> raw = List.of(
                new Coordinate(0, 0),
                new Coordinate(50, 0),
                new Coordinate(100, 0)
        );
        ObstacleModel empty = new ObstacleModel(
                Collections.emptyList(), Collections.emptyList());

        List<Coordinate> result = simplifier.simplify(raw, empty, Set.of());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).x).isEqualTo(0.0);
        assertThat(result.get(1).x).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Обход препятствия: число точек не растёт, путь корректен")
    void detourPreserved() {
        Polygon obstacle = rectangle(400, -50, 600, 50);
        ObstacleModel model = new ObstacleModel(
                List.of(new ObstacleModel.ForbiddenZone(1L, obstacle)),
                Collections.emptyList());

        // Путь вокруг препятствия сверху
        List<Coordinate> raw = List.of(
                new Coordinate(0, 0),
                new Coordinate(400, 100),
                new Coordinate(600, 100),
                new Coordinate(1000, 0)
        );

        List<Coordinate> result = simplifier.simplify(raw, model, Set.of());

        // Контракт: упроститель не добавляет точек и сохраняет обход
        assertThat(result).hasSizeLessThanOrEqualTo(raw.size());
        assertThat(result).isNotEmpty();
        assertThat(result.get(0)).isEqualTo(raw.get(0));
        assertThat(result.get(result.size() - 1))
                .isEqualTo(raw.get(raw.size() - 1));

        // Каждый сегмент упрощённого пути не пересекает запретную зону
        for (int i = 0; i < result.size() - 1; i++) {
            org.locationtech.jts.geom.LineString seg = GF.createLineString(
                    new Coordinate[]{result.get(i), result.get(i + 1)});
            assertThat(seg.intersects(obstacle))
                    .as("сегмент %d не должен пересекать запретную зону", i)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("Ignore-set пропускает запретную зону для первого ребра")
    void ignoreSetAllowsFirstSegment() {
        Polygon obstacle = rectangle(100, -50, 200, 50);
        ObstacleModel model = new ObstacleModel(
                List.of(new ObstacleModel.ForbiddenZone(7L, obstacle)),
                Collections.emptyList());

        List<Coordinate> raw = List.of(
                new Coordinate(150, 0),
                new Coordinate(300, 0)
        );

        List<Coordinate> result = simplifier.simplify(raw, model, Set.of(7L));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).x).isEqualTo(150.0);
        assertThat(result.get(1).x).isEqualTo(300.0);
    }

    private Polygon rectangle(double minX, double minY,
                               double maxX, double maxY) {
        LinearRing ring = GF.createLinearRing(new Coordinate[]{
                new Coordinate(minX, minY),
                new Coordinate(maxX, minY),
                new Coordinate(maxX, maxY),
                new Coordinate(minX, maxY),
                new Coordinate(minX, minY)
        });
        return GF.createPolygon(ring);
    }
}
