package ru.moscow.heat.trace.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Итоговый результат выполнения задачи трассировки для набора данных.
 * Содержит список ранжированных альтернативных вариантов (до 3 вариантов).
 */
@Getter
@ToString
@EqualsAndHashCode
public final class TraceResult {

    @JsonProperty("traceId")
    private final UUID traceId;

    @JsonProperty("uploadId")
    private final UUID uploadId;

    @JsonProperty("variants")
    private final List<VariantResult> variants;

    @JsonProperty("unconnectedOks")
    private final List<UnconnectedOks> unconnectedOks;

    @JsonCreator
    public TraceResult(
            @JsonProperty("traceId") UUID traceId,
            @JsonProperty("uploadId") UUID uploadId,
            @JsonProperty("variants") List<VariantResult> variants,
            @JsonProperty("unconnectedOks") List<UnconnectedOks> unconnectedOks) {
        this.traceId = traceId;
        this.uploadId = uploadId;
        this.variants = variants != null ? Collections.unmodifiableList(new ArrayList<>(variants)) : Collections.emptyList();
        this.unconnectedOks = unconnectedOks != null ? Collections.unmodifiableList(new ArrayList<>(unconnectedOks)) : Collections.emptyList();
    }
}
