package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.moscow.heat.trace.dto.UnconnectedOks;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnconnectedPenaltyCalculatorTest {

    private final UnconnectedPenaltyCalculator calculator = new UnconnectedPenaltyCalculator();

    @Test
    @DisplayName("Штраф рассчитывается по формуле 100_000_000 + 500_000 * flowTph")
    void calculatePenaltyForSingleOks() {
        // flow = 0 -> 100 000 000 руб
        assertThat(calculator.calculatePenalty(0.0))
                .isEqualByComparingTo(BigDecimal.valueOf(100_000_000));

        // flow = 10 -> 100 000 000 + 500 000 * 10 = 105 000 000 руб
        assertThat(calculator.calculatePenalty(10.0))
                .isEqualByComparingTo(BigDecimal.valueOf(105_000_000));

        // flow = 25.5 -> 100 000 000 + 500 000 * 25.5 = 112 750 000 руб
        assertThat(calculator.calculatePenalty(25.5))
                .isEqualByComparingTo(BigDecimal.valueOf(112_750_000));

        UnconnectedOks oks = new UnconnectedOks("oks-1", 10.0, "Outside boundary");
        assertThat(calculator.calculatePenalty(oks))
                .isEqualByComparingTo(BigDecimal.valueOf(105_000_000));
    }

    @Test
    @DisplayName("Суммарный штраф за несколько неподключенных объектов")
    void calculateTotalPenaltyForMultipleOks() {
        List<UnconnectedOks> list = List.of(
                new UnconnectedOks("oks-1", 10.0),
                new UnconnectedOks("oks-2", 20.0)
        );
        // 105_000_000 + 110_000_000 = 215_000_000
        assertThat(calculator.calculateTotalPenalty(list))
                .isEqualByComparingTo(BigDecimal.valueOf(215_000_000));

        assertThat(calculator.calculateTotalPenalty(Collections.emptyList()))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Отрицательный расход вызывает IllegalArgumentException")
    void negativeFlowThrowsException() {
        assertThatThrownBy(() -> calculator.calculatePenalty(-1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
