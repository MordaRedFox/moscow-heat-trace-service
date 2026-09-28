package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import ru.moscow.heat.trace.graph.ObstacleModel;
import ru.moscow.heat.trace.model.LayingMethod;
import ru.moscow.heat.trace.model.RouteNodeType;
import ru.moscow.heat.trace.model.RouteSegment;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты {@link RouteSegmentSplitter}.
 * <p>
 * Проверяются:
 * <ul>
 *   <li>разбиение пути по границам спецзон;</li>
 *   <li>сохранение топологии: соседние сегменты делят один
 *       {@code RouteNode};</li>
 *   <li>проставление типа OKS_POINT на первом узле и переданного
 *       конечного типа на последнем;</li>
 *   <li>создание технических узлов на промежуточных границах;</li>
 *   <li>классификация технического узла как METHOD_CHANGE при
 *       смене BASE ↔ SPECIAL.</li>
 * </ul>
 */
@DisplayName("Unit-тесты RouteSegmentSplitter")
class RouteSegmentSplitterTest {

    private static final GeometryFactory GF = new GeometryFactory();

    private RouteSegmentSplitter splitter;

    @BeforeEach
    void setUp() {
        splitter = new RouteSegmentSplitter();
    }

    @Test
    @DisplayName("Путь без спецзон — 1 сегмент BASE")
    void noSpecialZones_SingleSegment() {
        ObstacleModel model = new ObstacleModel(
                Collections.emptyList(), Collections.emptyList());

        List<Coordinate> path = List.of(
                new Coordinate(0, 0),
                new Coordinate(100, 0));

        RouteSegmentSplitter.SplitResult result = splitter.split(
                path, model, BigDecimal.valueOf(10.0),
                "oks-1", RouteNodeType.NEW_CHAMBER, null);

        assertThat(result.getSegments()).hasSize(1);
        assertThat(result.getTechnicalNodes()).isEmpty();

        RouteSegment seg = result.getSegments().get(0);
        assertThat(seg.getLayingMethod()).isEqualTo(LayingMethod.BASE);
        assertThat(seg.getFromNode().getType()).isEqualTo(RouteNodeType.OKS_POINT);
        assertThat(seg.getFromNode().getSourceFeatureId()).isEqualTo("oks-1");
        assertThat(seg.getToNode().getType()).isEqualTo(RouteNodeType.NEW_CHAMBER);
    }

    @Test
    @DisplayName("Одна спецзона — 3 сегмента, средний SPECIAL")
    void singleSpecialZone_ThreeSegments() {
        // Спецзона: прямоугольник от (40,-10) до (60,10)
        Polygon special = rectangle(40, -10, 60, 10);
        ObstacleModel.SpecialZone zone = new ObstacleModel.SpecialZone(
                special, special, 1.5, "road", 45.0);
        ObstacleModel model = new ObstacleModel(
                Collections.emptyList(), List.of(zone));

        List<Coordinate> path = List.of(
                new Coordinate(0, 0),
                new Coordinate(100, 0));

        RouteSegmentSplitter.SplitResult result = splitter.split(
                path, model, BigDecimal.valueOf(10.0),
                "oks-1", RouteNodeType.EXISTING_CHAMBER, "ch-1");

        assertThat(result.getSegments()).hasSize(3);
        assertThat(result.getTechnicalNodes()).hasSize(2);

        // Первый BASE, средний SPECIAL, последний BASE
        assertThat(result.getSegments().get(0).getLayingMethod())
                .isEqualTo(LayingMethod.BASE);
        assertThat(result.getSegments().get(1).getLayingMethod())
                .isEqualTo(LayingMethod.SPECIAL);
        assertThat(result.getSegments().get(2).getLayingMethod())
                .isEqualTo(LayingMethod.BASE);

        // Kспец = 1.5 у среднего, 1.0 у крайних
        assertThat(result.getSegments().get(1).getKspets()).isEqualTo(1.5);
        assertThat(result.getSegments().get(0).getKspets()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Топология: end_node_id предыдущего = start_node_id следующего")
    void topologyPreserved() {
        Polygon special = rectangle(40, -10, 60, 10);
        ObstacleModel.SpecialZone zone = new ObstacleModel.SpecialZone(
                special, special, 1.5, "road", 45.0);
        ObstacleModel model = new ObstacleModel(
                Collections.emptyList(), List.of(zone));

        List<Coordinate> path = List.of(
                new Coordinate(0, 0),
                new Coordinate(100, 0));

        RouteSegmentSplitter.SplitResult result = splitter.split(
                path, model, BigDecimal.valueOf(10.0),
                "oks-1", RouteNodeType.NEW_CHAMBER, null);

        List<RouteSegment> segs = result.getSegments();
        for (int i = 0; i < segs.size() - 1; i++) {
            assertThat(segs.get(i).getToNode().getId())
                    .as("end_node_id сегмента %d == start_node_id сегмента %d",
                            i, i + 1)
                    .isEqualTo(segs.get(i + 1).getFromNode().getId());
        }
    }

    @Test
    @DisplayName("Тип конечного узла проставляется корректно")
    void endNodeTypeSet() {
        ObstacleModel model = new ObstacleModel(
                Collections.emptyList(), Collections.emptyList());

        List<Coordinate> path = List.of(
                new Coordinate(0, 0),
                new Coordinate(100, 0));

        // NEW_CHAMBER без feature_id
        RouteSegmentSplitter.SplitResult newCh = splitter.split(
                path, model, BigDecimal.valueOf(5.0),
                "oks-1", RouteNodeType.NEW_CHAMBER, null);
        assertThat(newCh.getSegments().get(0).getToNode().getType())
                .isEqualTo(RouteNodeType.NEW_CHAMBER);
        assertThat(newCh.getSegments().get(0).getToNode().getSourceFeatureId())
                .isNull();

        // EXISTING_CHAMBER с feature_id
        RouteSegmentSplitter.SplitResult existCh = splitter.split(
                path, model, BigDecimal.valueOf(5.0),
                "oks-2", RouteNodeType.EXISTING_CHAMBER, "ch-42");
        assertThat(existCh.getSegments().get(0).getToNode().getType())
                .isEqualTo(RouteNodeType.EXISTING_CHAMBER);
        assertThat(existCh.getSegments().get(0).getToNode().getSourceFeatureId())
                .isEqualTo("ch-42");
    }

    @Test
    @DisplayName("Тип технического узла METHOD_CHANGE при смене BASE -> SPECIAL")
    void technicalNodeReasonMethodChange() {
        Polygon special = rectangle(40, -10, 60, 10);
        ObstacleModel.SpecialZone zone = new ObstacleModel.SpecialZone(
                special, special, 1.5, "road", 45.0);
        ObstacleModel model = new ObstacleModel(
                Collections.emptyList(), List.of(zone));

        List<Coordinate> path = List.of(
                new Coordinate(0, 0),
                new Coordinate(100, 0));

        RouteSegmentSplitter.SplitResult result = splitter.split(
                path, model, BigDecimal.valueOf(10.0),
                "oks-1", RouteNodeType.NEW_CHAMBER, null);

        // 2 техузла: на входе в спецзону (BASE->SPECIAL) и на выходе (SPECIAL->BASE)
        assertThat(result.getTechnicalNodes()).hasSize(2);
        assertThat(result.getTechnicalNodes().get(0).getReason())
                .isEqualTo(ru.moscow.heat.trace.model.TechnicalNode.Reason.METHOD_CHANGE);
        assertThat(result.getTechnicalNodes().get(1).getReason())
                .isEqualTo(ru.moscow.heat.trace.model.TechnicalNode.Reason.METHOD_CHANGE);
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
