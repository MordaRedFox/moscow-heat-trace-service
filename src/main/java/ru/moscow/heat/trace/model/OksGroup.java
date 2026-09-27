package ru.moscow.heat.trace.model;

import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.trace.dto.TieInCandidate;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Группа ОКС, подключаемых совместно через общую точку врезки.
 * <p>
 * Итерация 6: группа формируется {@code OksGrouper} по правилам:
 * <ul>
 *     <li>общая существующая камера (одинаковый {@code existingChamberId});</li>
 *     <li>близость точек врезки (в пределах {@code JOINT_TIE_IN_RADIUS_M}).</li>
 * </ul>
 * Одиночный ОКС также представляется как группа из одного элемента —
 * это упрощает дальнейшую обработку в {@code TraceOrchestrator}.
 */
public final class OksGroup {

    /** ОКС, входящие в группу (минимум один). */
    private final List<OksConnectionPointEntity> points;

    /** Кандидаты врезки для каждого ОКС группы (параллельный список). */
    private final List<TieInCandidate> candidates;

    /**
     * Общий tie-in для группы. Для группы по камере — кандидат камеры.
     * Для группы по радиусу — представительный кандидат (первый по
     * приоритету среди участников группы).
     */
    private final TieInCandidate sharedTieIn;

    /**
     * {@code true}, если группа сформирована по общей существующей камере.
     * {@code false} — по радиусной близости или одиночный ОКС.
     */
    private final boolean groupedByChamber;

    public OksGroup(List<OksConnectionPointEntity> points,
                    List<TieInCandidate> candidates,
                    TieInCandidate sharedTieIn,
                    boolean groupedByChamber) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("OksGroup не может быть пустой");
        }
        if (points.size() != candidates.size()) {
            throw new IllegalArgumentException(
                    "Размеры points и candidates должны совпадать");
        }
        this.points = Collections.unmodifiableList(points);
        this.candidates = Collections.unmodifiableList(candidates);
        this.sharedTieIn = Objects.requireNonNull(sharedTieIn, "sharedTieIn");
        this.groupedByChamber = groupedByChamber;
    }

    /** @return ОКС группы (неизменяемый список) */
    public List<OksConnectionPointEntity> getPoints() {
        return points;
    }

    /** @return кандидаты врезки (параллельно {@code points}) */
    public List<TieInCandidate> getCandidates() {
        return candidates;
    }

    /** @return общий tie-in группы */
    public TieInCandidate getSharedTieIn() {
        return sharedTieIn;
    }

    /** @return {@code true}, если группа по общей камере */
    public boolean isGroupedByChamber() {
        return groupedByChamber;
    }

    /** @return количество ОКС в группе */
    public int size() {
        return points.size();
    }

    /** @return {@code true}, если группа содержит более одного ОКС */
    public boolean isMulti() {
        return points.size() > 1;
    }

    @Override
    public String toString() {
        return "OksGroup{size=" + points.size()
                + ", chamber=" + groupedByChamber
                + ", tieIn=" + sharedTieIn.getId() + '}';
    }
}