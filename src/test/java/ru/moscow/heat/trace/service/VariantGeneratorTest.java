package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.moscow.heat.trace.dto.ExistingChamberTieIn;
import ru.moscow.heat.trace.dto.VariantResult;
import ru.moscow.heat.trace.dto.VariantSummary;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VariantGeneratorTest {

    private final VariantGenerator generator = new VariantGenerator(
            null, null, null, null, null, null, null, null
    );

    @Test
    @DisplayName("Дедупликация: варианты с идентичными множествами врезок объединяются")
    void deduplicatesIdenticalTieIns() {
        ExistingChamberTieIn tieIn1 = new ExistingChamberTieIn("chamber-100", List.of("s1"));
        ExistingChamberTieIn tieIn2 = new ExistingChamberTieIn("chamber-100", List.of("s2"));

        VariantSummary s1 = new VariantSummary("v1", null, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 100.0, 1.0, List.of());
        VariantSummary s2 = new VariantSummary("v2", null, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 150.0, 2.0, List.of());

        VariantResult r1 = new VariantResult("v1", List.of(), List.of(), List.of(tieIn1), List.of(), s1);
        VariantResult r2 = new VariantResult("v2", List.of(), List.of(), List.of(tieIn2), List.of(), s2);

        List<VariantResult> deduplicated = generator.deduplicateVariants(List.of(r1, r2));

        assertThat(deduplicated).hasSize(1);
        assertThat(deduplicated.get(0).getVariantId()).isEqualTo("v1");
    }

    @Test
    @DisplayName("Дедупликация: варианты со score отличающимся менее чем на 0.1% отбрасываются")
    void deduplicatesCloseScores() {
        ExistingChamberTieIn tieIn1 = new ExistingChamberTieIn("chamber-100", List.of("s1"));
        ExistingChamberTieIn tieIn2 = new ExistingChamberTieIn("chamber-200", List.of("s2"));

        // score 1.000 и 1.0005 (отличие 0.05% < 0.1%)
        VariantSummary s1 = new VariantSummary("v1", null, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 100.0, 1.000, List.of());
        VariantSummary s2 = new VariantSummary("v2", null, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 100.0, 1.0005, List.of());

        VariantResult r1 = new VariantResult("v1", List.of(), List.of(), List.of(tieIn1), List.of(), s1);
        VariantResult r2 = new VariantResult("v2", List.of(), List.of(), List.of(tieIn2), List.of(), s2);

        List<VariantResult> deduplicated = generator.deduplicateVariants(List.of(r1, r2));

        assertThat(deduplicated).hasSize(1);
        assertThat(deduplicated.get(0).getVariantId()).isEqualTo("v1");
    }

    @Test
    @DisplayName("Дедупликация: различные варианты с разными врезками и отличием score > 0.1% сохраняются")
    void keepsDistinctVariants() {
        ExistingChamberTieIn tieIn1 = new ExistingChamberTieIn("chamber-100", List.of("s1"));
        ExistingChamberTieIn tieIn2 = new ExistingChamberTieIn("chamber-200", List.of("s2"));

        VariantSummary s1 = new VariantSummary("v1", null, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 100.0, 1.0, List.of());
        VariantSummary s2 = new VariantSummary("v2", null, BigDecimal.ZERO, BigDecimal.ZERO, 1, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 120.0, 1.5, List.of());

        VariantResult r1 = new VariantResult("v1", List.of(), List.of(), List.of(tieIn1), List.of(), s1);
        VariantResult r2 = new VariantResult("v2", List.of(), List.of(), List.of(tieIn2), List.of(), s2);

        List<VariantResult> deduplicated = generator.deduplicateVariants(List.of(r1, r2));

        assertThat(deduplicated).hasSize(2);
        assertThat(deduplicated.get(0).getVariantId()).isEqualTo("v1");
        assertThat(deduplicated.get(1).getVariantId()).isEqualTo("v2");
    }
}
