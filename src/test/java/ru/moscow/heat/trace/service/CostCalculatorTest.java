package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import ru.moscow.heat.spatial.DiameterTable;
import ru.moscow.heat.trace.model.RouteNode;
import ru.moscow.heat.trace.model.RouteSegment;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostCalculatorTest {

    private final DiameterTable diameterTable = new DiameterTable();
    private final CostCalculator calculator = new CostCalculator(diameterTable);
    private final GeometryFactory gf = new GeometryFactory();

    @Test
    @DisplayName("Расчет стоимости сегмента сети по формуле Cуч = L * cнов(ДУ) * Kгл * Kспец при Kгл=1, Kспец=1")
    void calculateCostStandardSegment() {
        // ДУ 100 мм: cнов = 89748.0 руб/м
        // L = 100.0 м, Kгл = 1, Kспец = 1
        // Cуч = 100.0 * 89748.0 * 1.0 * 1.0 = 8 974 800.00 руб.
        Point p1 = gf.createPoint(new Coordinate(0.0, 0.0));
        Point p2 = gf.createPoint(new Coordinate(100.0, 0.0));
        RouteNode n1 = new RouteNode("n1", "src-1", p1, "oks");
        RouteNode n2 = new RouteNode("n2", "src-2", p2, "chamber");
        LineString line = gf.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(100, 0)});

        RouteSegment segment = new RouteSegment(
                "seg-1", n1, n2, line, 100.0, 100, 20.0, 1.0, 1.0, null);

        BigDecimal cost = calculator.calculateCost(segment);
        assertThat(cost).isEqualByComparingTo(BigDecimal.valueOf(8_974_800L));

        RouteSegment withCost = calculator.applyCost(segment);
        assertThat(withCost.getCost()).isEqualByComparingTo(BigDecimal.valueOf(8_974_800L));
        assertThat(segment.getCost()).isNull(); // RouteSegment immutable!
    }

    @Test
    @DisplayName("Расчет стоимости сегмента с повышающим коэффициентом Kспец = 1.2")
    void calculateCostWithKspets() {
        // ДУ 100 мм: cнов = 89748.0 руб/м
        // L = 50.0 м, Kгл = 1, Kспец = 1.2
        // Cуч = 50.0 * 89748.0 * 1 * 1.2 = 5 384 880.00 руб.
        Point p1 = gf.createPoint(new Coordinate(0.0, 0.0));
        Point p2 = gf.createPoint(new Coordinate(50.0, 0.0));
        RouteNode n1 = new RouteNode("n1", null, p1, "node");
        RouteNode n2 = new RouteNode("n2", null, p2, "node");
        LineString line = gf.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(50, 0)});

        RouteSegment segment = new RouteSegment(
                "seg-2", n1, n2, line, 50.0, 100, 15.0, 1.2, 1.0, null);

        BigDecimal cost = calculator.calculateCost(segment);
        assertThat(cost).isEqualByComparingTo(BigDecimal.valueOf(5_384_880L));
    }

    @Test
    @DisplayName("Суммарная стоимость коллекции сегментов сети")
    void calculateTotalCostForSegments() {
        Point p1 = gf.createPoint(new Coordinate(0, 0));
        Point p2 = gf.createPoint(new Coordinate(10, 0));
        Point p3 = gf.createPoint(new Coordinate(20, 0));
        RouteNode n1 = new RouteNode("n1", null, p1, "node");
        RouteNode n2 = new RouteNode("n2", null, p2, "node");
        RouteNode n3 = new RouteNode("n3", null, p3, "node");
        LineString l1 = gf.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(10, 0)});
        LineString l2 = gf.createLineString(new Coordinate[]{new Coordinate(10, 0), new Coordinate(20, 0)});

        // ДУ 50: cнов = 74023.0 руб/м
        // seg1: L=10 -> 740 230.00 руб.
        RouteSegment seg1 = calculator.applyCost(new RouteSegment(
                "s1", n1, n2, l1, 10.0, 50, 2.0, 1.0, 1.0, null));
        // seg2: L=20 -> 1 480 460.00 руб.
        RouteSegment seg2 = new RouteSegment(
                "s2", n2, n3, l2, 20.0, 50, 2.0, 1.0, 1.0, null);

        BigDecimal total = calculator.calculateTotalCost(List.of(seg1, seg2));
        assertThat(total).isEqualByComparingTo(BigDecimal.valueOf(2_220_690L));
    }

    @Test
    @DisplayName("Исключение при расчете для несуществующего диаметра")
    void invalidDiameterThrowsException() {
        Point p1 = gf.createPoint(new Coordinate(0, 0));
        Point p2 = gf.createPoint(new Coordinate(10, 0));
        RouteNode n1 = new RouteNode("n1", null, p1, "node");
        RouteNode n2 = new RouteNode("n2", null, p2, "node");
        LineString l = gf.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(10, 0)});

        RouteSegment seg = new RouteSegment(
                "s1", n1, n2, l, 10.0, 9999, 2.0, 1.0, 1.0, null);

        assertThatThrownBy(() -> calculator.calculateCost(seg))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
