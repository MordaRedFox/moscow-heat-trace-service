package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.moscow.heat.trace.dto.VariantResult;
import ru.moscow.heat.trace.dto.VariantSummary;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VariantRankerTest {

    private final VariantRanker ranker = new VariantRanker();

    @Test
    @DisplayName("Ранжирование вариантов по возрастанию score с присвоением rank 1, 2, 3")
    void ranksVariantsByAscendingScore() {
        VariantSummary s1 = new VariantSummary("v1", null, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 100.0, 2.5, List.of());
        VariantSummary s2 = new VariantSummary("v2", null, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 100.0, 1.2, List.of());
        VariantSummary s3 = new VariantSummary("v3", null, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 100.0, 3.8, List.of());

        VariantResult r1 = new VariantResult("v1", List.of(), List.of(), List.of(), List.of(), s1);
        VariantResult r2 = new VariantResult("v2", List.of(), List.of(), List.of(), List.of(), s2);
        VariantResult r3 = new VariantResult("v3", List.of(), List.of(), List.of(), List.of(), s3);

        List<VariantResult> ranked = ranker.rankVariants(List.of(r1, r2, r3));

        assertThat(ranked).hasSize(3);
        // r2 (score 1.2) -> rank 1
        assertThat(ranked.get(0).getVariantId()).isEqualTo("v2");
        assertThat(ranked.get(0).getSummary().getRank()).isEqualTo(1);

        // r1 (score 2.5) -> rank 2
        assertThat(ranked.get(1).getVariantId()).isEqualTo("v1");
        assertThat(ranked.get(1).getSummary().getRank()).isEqualTo(2);

        // r3 (score 3.8) -> rank 3
        assertThat(ranked.get(2).getVariantId()).isEqualTo("v3");
        assertThat(ranked.get(2).getSummary().getRank()).isEqualTo(3);
    }

    @Test
    @DisplayName("Пустой список вариантов корректно обрабатывается")
    void emptyListReturnsEmpty() {
        assertThat(ranker.rankVariants(Collections.emptyList())).isEmpty();
        assertThat(ranker.rankVariants(null)).isEmpty();
    }
}
