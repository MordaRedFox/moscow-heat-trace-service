package ru.moscow.heat.trace.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Сводные показатели варианта трассировки тепловой сети.
 * Содержит ранжирование, структуру затрат, протяженность сети и интегральный балл (score).
 */
@Getter
@ToString
@EqualsAndHashCode
public final class VariantSummary {

    @JsonProperty("variantId")
    private final String variantId;

    @JsonProperty("rank")
    private final Integer rank;

    @JsonProperty("constructionCost")
    private final BigDecimal constructionCost;

    @JsonProperty("chamberConstructionCost")
    private final BigDecimal chamberConstructionCost;

    @JsonProperty("existingChamberTieInCount")
    private final int existingChamberTieInCount;

    @JsonProperty("existingChamberTieInCost")
    private final BigDecimal existingChamberTieInCost;

    @JsonProperty("unconnectedPenalty")
    private final BigDecimal unconnectedPenalty;

    @JsonProperty("calculatedCost")
    private final BigDecimal calculatedCost;

    @JsonProperty("newNetworkLength")
    private final double newNetworkLength;

    @JsonProperty("score")
    private final double score;

    @JsonProperty("unconnectedOksIds")
    private final List<String> unconnectedOksIds;

    @JsonCreator
    public VariantSummary(
            @JsonProperty("variantId") String variantId,
            @JsonProperty("rank") Integer rank,
            @JsonProperty("constructionCost") BigDecimal constructionCost,
            @JsonProperty("chamberConstructionCost") BigDecimal chamberConstructionCost,
            @JsonProperty("existingChamberTieInCount") int existingChamberTieInCount,
            @JsonProperty("existingChamberTieInCost") BigDecimal existingChamberTieInCost,
            @JsonProperty("unconnectedPenalty") BigDecimal unconnectedPenalty,
            @JsonProperty("calculatedCost") BigDecimal calculatedCost,
            @JsonProperty("newNetworkLength") double newNetworkLength,
            @JsonProperty("score") double score,
            @JsonProperty("unconnectedOksIds") List<String> unconnectedOksIds) {
        this.variantId = variantId;
        this.rank = rank;
        this.constructionCost = constructionCost != null
                ? constructionCost.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.chamberConstructionCost = chamberConstructionCost != null
                ? chamberConstructionCost.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.existingChamberTieInCount = existingChamberTieInCount;
        this.existingChamberTieInCost = existingChamberTieInCost != null
                ? existingChamberTieInCost.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.unconnectedPenalty = unconnectedPenalty != null
                ? unconnectedPenalty.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        this.calculatedCost = calculatedCost != null
                ? calculatedCost.setScale(2, RoundingMode.HALF_UP)
                : this.constructionCost.add(this.chamberConstructionCost)
                .add(this.existingChamberTieInCost)
                .add(this.unconnectedPenalty);
        this.newNetworkLength = Math.round(newNetworkLength * 100.0) / 100.0;
        this.score = Math.round(score * 10000.0) / 10000.0;
        this.unconnectedOksIds = unconnectedOksIds != null
                ? Collections.unmodifiableList(new ArrayList<>(unconnectedOksIds))
                : Collections.emptyList();
    }

    /**
     * Создает копию сводки с обновленным рангом.
     *
     * @param newRank новый ранг (1, 2, 3...)
     * @return новый неизменяемый экземпляр VariantSummary
     */
    public VariantSummary withRank(Integer newRank) {
        return new VariantSummary(
                this.variantId,
                newRank,
                this.constructionCost,
                this.chamberConstructionCost,
                this.existingChamberTieInCount,
                this.existingChamberTieInCost,
                this.unconnectedPenalty,
                this.calculatedCost,
                this.newNetworkLength,
                this.score,
                this.unconnectedOksIds
        );
    }
}
