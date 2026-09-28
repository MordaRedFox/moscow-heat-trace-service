package ru.moscow.heat.trace.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExistingChamberTieInTest {

    @Test
    @DisplayName("Стоимость врезки равна 5 000 000 * количество подключенных сегментов")
    void calculatesCostBasedOnAttachedSegmentsCount() {
        ExistingChamberTieIn singleTieIn = new ExistingChamberTieIn("chamber-1", List.of("seg-1"));
        assertThat(singleTieIn.getCost()).isEqualByComparingTo(BigDecimal.valueOf(5_000_000));

        ExistingChamberTieIn doubleTieIn = new ExistingChamberTieIn("chamber-2", List.of("seg-1", "seg-2"));
        assertThat(doubleTieIn.getCost()).isEqualByComparingTo(BigDecimal.valueOf(10_000_000));

        ExistingChamberTieIn tripleTieIn = new ExistingChamberTieIn("chamber-3", List.of("seg-1", "seg-2", "seg-3"));
        assertThat(tripleTieIn.getCost()).isEqualByComparingTo(BigDecimal.valueOf(15_000_000));

        ExistingChamberTieIn emptyTieIn = new ExistingChamberTieIn("chamber-0", Collections.emptyList());
        assertThat(emptyTieIn.getCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
