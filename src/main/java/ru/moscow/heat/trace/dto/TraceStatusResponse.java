package ru.moscow.heat.trace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Статус сессии трассировки, отдаваемый {@code GET /api/trace/{traceId}}.
 * <p>
 * ВНИМАНИЕ: этот файл переписан для итерации 5 (реальная трассировка вместо
 * заглушки {@code NOT_IMPLEMENTED}) на основе того, как класс использовался
 * в уже присланном {@code TraceService}/{@code TraceController}
 * (конструктор {@code (traceId, status, createdAt)}, поля traceId/status).
 * Если в вашей текущей версии класса были другие поля, которых я не видел —
 * пришлите файл, смёржим.
 * <p>
 * Иммутабельный DTO с билдером — по образцу {@code TieInCandidate}.
 * Поля {@code totalOksCount}/{@code connectedCount}/{@code unconnectedCount}/
 * {@code unconnectedOksFeatureIds} заполняются только при {@code status == COMPLETED}
 * (ТЗ, п.2.9-2.10: сервис должен явно показывать перечень ОКС без маршрута).
 * {@code errorMessage} заполняется только при {@code status == FAILED}.
 */
@Schema(description = "Статус сессии трассировки")
public final class TraceStatusResponse {

    private final UUID traceId;
    private final TraceStatus status;
    private final Instant createdAt;
    private final Instant startedAt;
    private final Instant completedAt;
    private final Integer totalOksCount;
    private final Integer connectedCount;
    private final Integer unconnectedCount;
    private final List<String> unconnectedOksFeatureIds;
    private final String errorMessage;

    private TraceStatusResponse(Builder b) {
        this.traceId = b.traceId;
        this.status = b.status;
        this.createdAt = b.createdAt;
        this.startedAt = b.startedAt;
        this.completedAt = b.completedAt;
        this.totalOksCount = b.totalOksCount;
        this.connectedCount = b.connectedCount;
        this.unconnectedCount = b.unconnectedCount;
        this.unconnectedOksFeatureIds = b.unconnectedOksFeatureIds;
        this.errorMessage = b.errorMessage;
    }

    /**
     * Конструктор для обратной совместимости с существующими вызовами вида
     * {@code new TraceStatusResponse(traceId, status, createdAt)}.
     * Эквивалентен {@code builder().traceId(...).status(...).createdAt(...).build()}.
     */
    public TraceStatusResponse(UUID traceId, TraceStatus status, Instant createdAt) {
        this(builder().traceId(traceId).status(status).createdAt(createdAt));
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Создаёт копию с изменённым статусом и прочими полями через билдер на основе текущего состояния. */
    public Builder toBuilder() {
        return builder()
                .traceId(traceId)
                .status(status)
                .createdAt(createdAt)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .totalOksCount(totalOksCount)
                .connectedCount(connectedCount)
                .unconnectedCount(unconnectedCount)
                .unconnectedOksFeatureIds(unconnectedOksFeatureIds)
                .errorMessage(errorMessage);
    }

    public UUID getTraceId() {
        return traceId;
    }

    public TraceStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Integer getTotalOksCount() {
        return totalOksCount;
    }

    public Integer getConnectedCount() {
        return connectedCount;
    }

    public Integer getUnconnectedCount() {
        return unconnectedCount;
    }

    public List<String> getUnconnectedOksFeatureIds() {
        return unconnectedOksFeatureIds;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TraceStatusResponse)) {
            return false;
        }
        TraceStatusResponse that = (TraceStatusResponse) o;
        return Objects.equals(traceId, that.traceId)
                && status == that.status
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(startedAt, that.startedAt)
                && Objects.equals(completedAt, that.completedAt)
                && Objects.equals(totalOksCount, that.totalOksCount)
                && Objects.equals(connectedCount, that.connectedCount)
                && Objects.equals(unconnectedCount, that.unconnectedCount)
                && Objects.equals(unconnectedOksFeatureIds, that.unconnectedOksFeatureIds)
                && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId, status, createdAt, startedAt, completedAt,
                totalOksCount, connectedCount, unconnectedCount, unconnectedOksFeatureIds, errorMessage);
    }

    @Override
    public String toString() {
        return "TraceStatusResponse{"
                + "traceId=" + traceId
                + ", status=" + status
                + ", createdAt=" + createdAt
                + ", startedAt=" + startedAt
                + ", completedAt=" + completedAt
                + ", totalOksCount=" + totalOksCount
                + ", connectedCount=" + connectedCount
                + ", unconnectedCount=" + unconnectedCount
                + ", errorMessage='" + errorMessage + '\''
                + '}';
    }

    public static final class Builder {
        private UUID traceId;
        private TraceStatus status;
        private Instant createdAt;
        private Instant startedAt;
        private Instant completedAt;
        private Integer totalOksCount;
        private Integer connectedCount;
        private Integer unconnectedCount;
        private List<String> unconnectedOksFeatureIds = Collections.emptyList();
        private String errorMessage;

        private Builder() {
        }

        public Builder traceId(UUID traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder status(TraceStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public Builder completedAt(Instant completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public Builder totalOksCount(Integer totalOksCount) {
            this.totalOksCount = totalOksCount;
            return this;
        }

        public Builder connectedCount(Integer connectedCount) {
            this.connectedCount = connectedCount;
            return this;
        }

        public Builder unconnectedCount(Integer unconnectedCount) {
            this.unconnectedCount = unconnectedCount;
            return this;
        }

        public Builder unconnectedOksFeatureIds(List<String> unconnectedOksFeatureIds) {
            this.unconnectedOksFeatureIds = unconnectedOksFeatureIds != null
                    ? unconnectedOksFeatureIds : Collections.emptyList();
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public TraceStatusResponse build() {
            return new TraceStatusResponse(this);
        }
    }
}
