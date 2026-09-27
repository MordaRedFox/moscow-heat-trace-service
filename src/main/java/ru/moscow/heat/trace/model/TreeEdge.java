package ru.moscow.heat.trace.model;

import org.locationtech.jts.geom.Coordinate;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Ребро дерева маршрутов группы ОКС (итерация 6, шаг 2).
 * <p>
 * Направление ребра — от узла ближе к корню (тай-ин) к узлу дальше от
 * корня (лист ОКС). Направление теплоносителя при этом обратное:
 * от листьев (ОКС) к корню (месту врезки). Это важно для шага 4:
 * инвариант «ДУ не убывает по направлению от ОКС к тай-ину» означает
 * рост ДУ при движении против направления ребра.
 * <p>
 * Координаты {@code geometryUtm} упорядочены от {@code from} к {@code to},
 * в метрах EPSG:32637.
 * <p>
 * Поля {@code flowTph}, {@code diameterMm}, {@code servedOksIds} заполняются
 * позже сервисами {@code FlowAggregator} (шаг 3) и
 * {@code TreeDiameterAssigner} (шаг 4). Структура мутабельна в фазе
 * расчёта и считается зафиксированной после передачи в
 * {@code RouteSegmentSplitter} (шаг 5).
 */
public final class TreeEdge {

    private final UUID id;
    private final TreeNode from;
    private final TreeNode to;
    private final List<Coordinate> geometryUtm;
    private final double lengthM;

    // ---- Заполняются на шагах 3–4 ----

    /** Суммарный расход участка, т/ч (шаг 3). */
    private double flowTph;

    /** Условный диаметр, мм (шаг 4). */
    private int diameterMm;

    /** feature_id ОКС, обслуживаемых через это ребро (шаг 3). */
    private List<String> servedOksIds = Collections.emptyList();

    public TreeEdge(UUID id, TreeNode from, TreeNode to,
                    List<Coordinate> geometryUtm, double lengthM) {
        this.id = Objects.requireNonNull(id, "id");
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
        this.geometryUtm = Objects.requireNonNull(geometryUtm, "geometryUtm");
        this.lengthM = lengthM;
    }

    public UUID getId() {
        return id;
    }

    /** @return узел ближе к корню (тай-ину) */
    public TreeNode getFrom() {
        return from;
    }

    /** @return узел дальше от корня (к листьям) */
    public TreeNode getTo() {
        return to;
    }

    /** @return геометрия от {@code from} к {@code to}, EPSG:32637 */
    public List<Coordinate> getGeometryUtm() {
        return Collections.unmodifiableList(geometryUtm);
    }

    /** @return длина ребра, м */
    public double getLengthM() {
        return lengthM;
    }

    public double getFlowTph() {
        return flowTph;
    }

    /** @param flowTph суммарный расход участка, т/ч (шаг 3) */
    public void setFlowTph(double flowTph) {
        this.flowTph = flowTph;
    }

    public int getDiameterMm() {
        return diameterMm;
    }

    /** @param diameterMm условный диаметр, мм (шаг 4) */
    public void setDiameterMm(int diameterMm) {
        this.diameterMm = diameterMm;
    }

    public List<String> getServedOksIds() {
        return Collections.unmodifiableList(servedOksIds);
    }

    /** @param servedOksIds feature_id ОКС, идущих через ребро (шаг 3) */
    public void setServedOksIds(List<String> servedOksIds) {
        this.servedOksIds = Objects.requireNonNull(servedOksIds, "servedOksIds");
    }

    @Override
    public String toString() {
        return "TreeEdge{id=" + id
                + ", from=" + from.getId()
                + ", to=" + to.getId()
                + ", length=" + String.format("%.1f", lengthM) + "m"
                + ", flow=" + flowTph
                + ", du=" + diameterMm + '}';
    }
}