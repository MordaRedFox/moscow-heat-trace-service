package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import ru.moscow.heat.spatial.DiameterTable;
import ru.moscow.heat.trace.model.LayingMethod;
import ru.moscow.heat.trace.model.RouteNode;
import ru.moscow.heat.trace.model.RouteNodeType;
import ru.moscow.heat.trace.model.RouteSegment;
import ru.moscow.heat.trace.model.TechnicalNode;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты {@link DiameterAssigner}.
 * <p>
 * Проверяется правило ТП п. 2.4: ДУ выбирается по расходу и накопленной
 * длине непрерывной части сети одного ДУ; при превышении предельной
 * длины ДУ увеличивается, начинается новый отсчёт; ДУ не убывает по
 * направлению от ОКС к tie-in; на границе смены ДУ появляется
 * {@link TechnicalNode} с причиной DIAMETER_CHANGE
 */
@DisplayName("Unit-тесты DiameterAssigner")
class DiameterAssignerTest {

    private static final GeometryFactory GF = new GeometryFactory();

    private DiameterAssigner assigner;

    @BeforeEach
    void setUp() {
        assigner = new DiameterAssigner(new DiameterTable());
    }

    @Test
    @DisplayName("Пустой список — пустой результат")
    void emptyInput() {
        DiameterAssigner.AssignmentResult res = assigner.assign(
                Collections.emptyList(), BigDecimal.valueOf(10.0));
        assertThat(res.getSegments()).isEmpty();
        assertThat(res.getTechnicalNodes()).isEmpty();
    }

    @Test
    @DisplayName("Короткий маршрут — ДУ постоянен")
    void shortRoute_ConstantDiameter() {
        // 2 сегмента по 300 м, flow = 10 т/ч → ДУ 80 (13.2 >= 10, длина 327)
        // Сумма 600 м < 327 — нет, 600 > 327 → вырастет
        // Возьмем сегменты по 100 м: суммарно 200 м < 327 → ДУ 80
        List<RouteSegment> segments = List.of(
                segment(0, 100, 100.0, 10.0),
                segment(100, 200, 100.0, 10.0));

        DiameterAssigner.AssignmentResult res = assigner.assign(
                segments, BigDecimal.valueOf(10.0));

        assertThat(res.getSegments()).hasSize(2);
        assertThat(res.getSegments().get(0).getDiameterMm()).isEqualTo(80);
        assertThat(res.getSegments().get(1).getDiameterMm()).isEqualTo(80);
        assertThat(res.getTechnicalNodes()).isEmpty();
    }

    @Test
    @DisplayName("Превышение предельной длины — ДУ растёт, tech-node DIAMETER_CHANGE")
    void lengthExceeded_DiameterIncreases() {
        // flow = 100 т/ч: ДУ 200 (152.3 >= 100, длина 1042), ДУ 250 (1379)
        // 2 сегмента по 600 м: 600 (OK), 1200 (>1042 → ДУ 250)
        List<RouteSegment> segments = List.of(
                segment(0, 600, 600.0, 100.0),
                segment(600, 1200, 600.0, 100.0));

        DiameterAssigner.AssignmentResult res = assigner.assign(
                segments, BigDecimal.valueOf(100.0));

        assertThat(res.getSegments()).hasSize(2);
        assertThat(res.getSegments().get(0).getDiameterMm()).isEqualTo(200);
        assertThat(res.getSegments().get(1).getDiameterMm()).isEqualTo(250);

        assertThat(res.getTechnicalNodes()).hasSize(1);
        assertThat(res.getTechnicalNodes().get(0).getReason())
                .isEqualTo(TechnicalNode.Reason.DIAMETER_CHANGE);
    }

    @Test
    @DisplayName("ДУ не убывает по направлению маршрута")
    void diameterNeverDecreases() {
        List<RouteSegment> segments = List.of(
                segment(0, 300, 300.0, 50.0),
                segment(300, 800, 500.0, 50.0),
                segment(800, 1400, 600.0, 50.0));

        DiameterAssigner.AssignmentResult res = assigner.assign(
                segments, BigDecimal.valueOf(50.0));

        int prev = 0;
        for (RouteSegment s : res.getSegments()) {
            assertThat(s.getDiameterMm()).isGreaterThanOrEqualTo(prev);
            prev = s.getDiameterMm();
        }
    }

    /**
     * Создает прямой RouteSegment вдоль оси X от x0 до x1
     */
    private RouteSegment segment(double x0, double x1,
                                  double lengthM, double flowTph) {
        RouteNode from = new RouteNode(UUID.randomUUID(),
                RouteNodeType.CORNER, new Coordinate(x0, 0), null);
        RouteNode to = new RouteNode(UUID.randomUUID(),
                RouteNodeType.CORNER, new Coordinate(x1, 0), null);
        LineString geom = GF.createLineString(new Coordinate[]{
                new Coordinate(x0, 0), new Coordinate(x1, 0)
        });
        return new RouteSegment(UUID.randomUUID(), from, to, geom,
                BigDecimal.valueOf(flowTph), 0,
                LayingMethod.BASE, 1.0, lengthM, null);
    }
}
