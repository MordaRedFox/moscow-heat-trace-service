package ru.moscow.heat.trace.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Неподключенный перспективный объект капитального строительства (ОКС).
 * Содержит идентификатор объекта, расчетный расход теплоносителя и причину невозможности подключения.
 */
@Getter
@ToString
@EqualsAndHashCode
public final class UnconnectedOks {

    @JsonProperty("oksFeatureId")
    private final String oksFeatureId;

    @JsonProperty("flowTph")
    private final double flowTph;

    @JsonProperty("reason")
    private final String reason;

    @JsonCreator
    public UnconnectedOks(
            @JsonProperty("oksFeatureId") String oksFeatureId,
            @JsonProperty("flowTph") double flowTph,
            @JsonProperty("reason") String reason) {
        this.oksFeatureId = oksFeatureId;
        this.flowTph = flowTph;
        this.reason = reason;
    }

    public UnconnectedOks(String oksFeatureId, double flowTph) {
        this(oksFeatureId, flowTph, null);
    }
}
