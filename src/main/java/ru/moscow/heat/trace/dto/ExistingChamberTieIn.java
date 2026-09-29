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
 * Врезка в существующую тепловую камеру.
 * Стоимость врезки рассчитывается как 5 000 000 руб. за каждый подключенный сегмент (5 000 000 * size).
 */
@Getter
@ToString
@EqualsAndHashCode
public final class ExistingChamberTieIn {

    public static final BigDecimal TIE_IN_UNIT_COST = BigDecimal.valueOf(5_000_000L);

    @JsonProperty("chamberFeatureId")
    private final String chamberFeatureId;

    @JsonProperty("attachedSegmentIds")
    private final List<String> attachedSegmentIds;

    @JsonProperty("cost")
    private final BigDecimal cost;

    @JsonCreator
    public ExistingChamberTieIn(
            @JsonProperty("chamberFeatureId") String chamberFeatureId,
            @JsonProperty("attachedSegmentIds") List<String> attachedSegmentIds,
            @JsonProperty("cost") BigDecimal cost) {
        this.chamberFeatureId = chamberFeatureId;
        this.attachedSegmentIds = attachedSegmentIds != null
                ? Collections.unmodifiableList(new ArrayList<>(attachedSegmentIds))
                : Collections.emptyList();
        if (cost != null) {
            this.cost = cost.setScale(2, RoundingMode.HALF_UP);
        } else {
            this.cost = TIE_IN_UNIT_COST.multiply(BigDecimal.valueOf(this.attachedSegmentIds.size()))
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }

    public ExistingChamberTieIn(String chamberFeatureId, List<String> attachedSegmentIds) {
        this(chamberFeatureId, attachedSegmentIds, null);
    }
}
