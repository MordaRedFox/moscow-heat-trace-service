package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import ru.moscow.heat.trace.dto.ExistingChamberTieIn;
import ru.moscow.heat.trace.dto.UnconnectedOks;
import ru.moscow.heat.trace.dto.VariantSummary;
import ru.moscow.heat.trace.model.NewChamber;
import ru.moscow.heat.trace.model.RouteNode;
import ru.moscow.heat.trace.model.RouteSegment;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class VariantScoreCalculatorTest {

    private final VariantScoreCalculator calculator = new VariantScoreCalculator();
    private final GeometryFactory gf = new GeometryFactory();

    @Test
    @DisplayName("Формула score: 0.7 * (calculatedCost / 25_000_000) + 0.3 * (newNetworkLength / 100)")
    void calculateScoreExactFormula() {
        // При cost = 25 000 000 руб и length = 100 м:
        // Score = 0.7 * 1.0 + 0.3 * 1.0 = 1.0
        double score1 = calculator.calculateScore(BigDecimal.valueOf(25_000_000L), 100.0);
        assertThat(score1).isCloseTo(1.0, within(1e-6));

        // При cost = 50 000 000 руб и length = 200 м:
        // Score = 0.7 * 2.0 + 0.3 * 2.0 = 2.0
        double score2 = calculator.calculateScore(BigDecimal.valueOf(50_000_000L), 200.0);
        assertThat(score2).isCloseTo(2.0, within(1e-6));

        // При cost = 12 500 000 руб и length = 50 м:
        // Score = 0.7 * 0.5 + 0.3 * 0.5 = 0.5
        double score3 = calculator.calculateScore(BigDecimal.valueOf(12_500_000L), 50.0);
        assertThat(score3).isCloseTo(0.5, within(1e-6));
    }

    @Test
    @DisplayName("Формирование сводки варианта VariantSummary с полной структурой затрат")
    void calculateSummaryStructure() {
        Point p1 = gf.createPoint(new Coordinate(0, 0));
        Point p2 = gf.createPoint(new Coordinate(50, 0));
        RouteNode n1 = new RouteNode("n1", "oks-1", p1, "oks");
        RouteNode n2 = new RouteNode("n2", "ch-1", p2, "chamber");
        LineString line = gf.createLineString(new Coordinate[]{new Coordinate(0, 0), new Coordinate(50, 0)});

        // Сегмент: 50 м, стоимость 4 000 000 руб
        RouteSegment segment = new RouteSegment(
                "s1", n1, n2, line, 50.0, 100, 10.0, 1.0, 1.0, BigDecimal.valueOf(4_000_000L));

        // Новая камера: 3 000 000 руб
        NewChamber chamber = new NewChamber("ch-new", p2, 100, BigDecimal.valueOf(3_000_000L));

        // Врезка: 1 сегмент -> 5 000 000 руб
        ExistingChamberTieIn tieIn = new ExistingChamberTieIn("ch-exist", List.of("s1"));

        // Неподключенный ОКС: flow=10 -> 100 000 000 + 500 000 * 10 = 105 000 000 руб
        UnconnectedOks unconnected = new UnconnectedOks("oks-unconnected", 10.0, "Isolated");

        VariantSummary summary = calculator.calculateSummary(
                "v1",
                List.of(segment),
                List.of(chamber),
                List.of(tieIn),
                List.of(unconnected)
        );

        assertThat(summary.getVariantId()).isEqualTo("v1");
        // constructionCost = 4M (сегмент) + 3M (камера) + 5M (врезка) = 12 000 000 руб
        assertThat(summary.getConstructionCost()).isEqualByComparingTo(BigDecimal.valueOf(12_000_000));
        assertThat(summary.getChamberConstructionCost()).isEqualByComparingTo(BigDecimal.valueOf(3_000_000));
        assertThat(summary.getExistingChamberTieInCount()).isEqualTo(1);
        assertThat(summary.getExistingChamberTieInCost()).isEqualByComparingTo(BigDecimal.valueOf(5_000_000));
        assertThat(summary.getUnconnectedPenalty()).isEqualByComparingTo(BigDecimal.valueOf(105_000_000));

        // Итого calculatedCost = constructionCost (12M) + penalty (105M) = 117M
        assertThat(summary.getCalculatedCost()).isEqualByComparingTo(BigDecimal.valueOf(117_000_000));
        assertThat(summary.getNewNetworkLength()).isEqualTo(50.0);
        assertThat(summary.getUnconnectedOksIds()).containsExactly("oks-unconnected");

        // Score = 0.7 * (117 / 25) + 0.3 * (50 / 100) = 0.7 * 4.68 + 0.3 * 0.5 = 3.276 + 0.15 = 3.426
        assertThat(summary.getScore()).isCloseTo(3.426, within(0.001));
    }
}
