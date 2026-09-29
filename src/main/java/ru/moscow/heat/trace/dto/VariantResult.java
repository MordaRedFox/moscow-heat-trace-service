package ru.moscow.heat.trace.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import ru.moscow.heat.trace.model.NewChamber;
import ru.moscow.heat.trace.model.RouteSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Полный результат одного варианта трассировки тепловой сети.
 * Содержит построенные линейные участки (сегменты), новые камеры, врезки и сводные показатели.
 */
@Getter
@ToString
@EqualsAndHashCode
public final class VariantResult {

    @JsonProperty("variantId")
    private final String variantId;

    @JsonProperty("segments")
    private final List<RouteSegment> segments;

    @JsonProperty("chambers")
    private final List<NewChamber> chambers;

    @JsonProperty("tieIns")
    private final List<ExistingChamberTieIn> tieIns;

    @JsonProperty("unconnectedOks")
    private final List<UnconnectedOks> unconnectedOks;

    @JsonProperty("summary")
    private final VariantSummary summary;

    @JsonCreator
    public VariantResult(
            @JsonProperty("variantId") String variantId,
            @JsonProperty("segments") List<RouteSegment> segments,
            @JsonProperty("chambers") List<NewChamber> chambers,
            @JsonProperty("tieIns") List<ExistingChamberTieIn> tieIns,
            @JsonProperty("unconnectedOks") List<UnconnectedOks> unconnectedOks,
            @JsonProperty("summary") VariantSummary summary) {
        this.variantId = variantId;
        this.segments = segments != null ? Collections.unmodifiableList(new ArrayList<>(segments)) : Collections.emptyList();
        this.chambers = chambers != null ? Collections.unmodifiableList(new ArrayList<>(chambers)) : Collections.emptyList();
        this.tieIns = tieIns != null ? Collections.unmodifiableList(new ArrayList<>(tieIns)) : Collections.emptyList();
        this.unconnectedOks = unconnectedOks != null ? Collections.unmodifiableList(new ArrayList<>(unconnectedOks)) : Collections.emptyList();
        this.summary = summary;
    }

    /**
     * Создает копию результата с обновленным рангом в сводке.
     *
     * @param rank присвоенный ранг
     * @return новый экземпляр VariantResult
     */
    public VariantResult withRank(int rank) {
        VariantSummary newSummary = this.summary != null ? this.summary.withRank(rank) : null;
        return new VariantResult(
                this.variantId,
                this.segments,
                this.chambers,
                this.tieIns,
                this.unconnectedOks,
                newSummary
        );
    }
}
