package ru.moscow.heat.trace.model;

import java.util.List;
import java.util.Objects;

/**
 * Внутренний результат трассировки (итерация 5).
 * <p>
 * Без GeoJSON, без стоимости, без вариантов — см. план, раздел "Что НЕ делаем
 * в итерации 5". Это сырой результат работы {@code TraceOrchestrator},
 * который в итерации 6-7 будет обогащён стоимостью и выгружен в GeoJSON.
 */
public final class TraceResult {

    /** Счётчики для {@code GET /api/trace/{traceId}} (connectedCount/unconnectedCount). */
    public static final class SummaryCounters {
        private final int totalOksCount;
        private final int connectedCount;
        private final int unconnectedCount;

        public SummaryCounters(int totalOksCount, int connectedCount, int unconnectedCount) {
            this.totalOksCount = totalOksCount;
            this.connectedCount = connectedCount;
            this.unconnectedCount = unconnectedCount;
        }

        public int getTotalOksCount() {
            return totalOksCount;
        }

        public int getConnectedCount() {
            return connectedCount;
        }

        public int getUnconnectedCount() {
            return unconnectedCount;
        }
    }

    private final List<RouteSegment> segments;
    private final List<NewChamber> newChambers;
    private final List<TechnicalNode> technicalNodes;
    private final List<UnconnectedOks> unconnectedOks;
    private final SummaryCounters summaryCounters;

    public TraceResult(List<RouteSegment> segments, List<NewChamber> newChambers,
                        List<TechnicalNode> technicalNodes, List<UnconnectedOks> unconnectedOks,
                        SummaryCounters summaryCounters) {
        this.segments = Objects.requireNonNull(segments, "segments");
        this.newChambers = Objects.requireNonNull(newChambers, "newChambers");
        this.technicalNodes = Objects.requireNonNull(technicalNodes, "technicalNodes");
        this.unconnectedOks = Objects.requireNonNull(unconnectedOks, "unconnectedOks");
        this.summaryCounters = Objects.requireNonNull(summaryCounters, "summaryCounters");
    }

    public List<RouteSegment> getSegments() {
        return segments;
    }

    public List<NewChamber> getNewChambers() {
        return newChambers;
    }

    public List<TechnicalNode> getTechnicalNodes() {
        return technicalNodes;
    }

    public List<UnconnectedOks> getUnconnectedOks() {
        return unconnectedOks;
    }

    public SummaryCounters getSummaryCounters() {
        return summaryCounters;
    }
}
