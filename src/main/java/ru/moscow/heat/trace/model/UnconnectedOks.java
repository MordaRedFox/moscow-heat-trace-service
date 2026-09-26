package ru.moscow.heat.trace.model;

import java.util.Objects;

/**
 * Перспективный ОКС, для которого автоматический маршрут не найден
 * (ТЗ, п.2.9; план итерации 5, риск R6).
 * <p>
 * Не пытаемся "всё равно подключить" — просто фиксируем причину.
 * Штраф за такие ОКС считается в итерации 7.
 */
public final class UnconnectedOks {

    /** Грубая классификация причины — можно уточнять по мере отладки алгоритма. */
    public enum Reason {
        /** Не найден ни один кандидат точки врезки (TieInCandidateService вернул пусто). */
        NO_TIE_IN_CANDIDATE,
        /** Граф видимости несвязный между start и выбранным end. */
        NO_PATH_IN_GRAPH,
        /** Путь найден, но не проходит проверку углов / длины / прочих правил без ручной доработки. */
        PATH_REJECTED_BY_VALIDATION
    }

    /** feature_id точки подключения ОКС (oks_connection_point.feature_id). */
    private final String oksPointFeatureId;
    private final Reason reason;
    private final String details;

    public UnconnectedOks(String oksPointFeatureId, Reason reason, String details) {
        this.oksPointFeatureId = Objects.requireNonNull(oksPointFeatureId, "oksPointFeatureId");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.details = details;
    }

    public String getOksPointFeatureId() {
        return oksPointFeatureId;
    }

    public Reason getReason() {
        return reason;
    }

    public String getDetails() {
        return details;
    }
}
