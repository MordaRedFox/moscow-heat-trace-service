package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.moscow.heat.spatial.DiameterSpec;
import ru.moscow.heat.spatial.DiameterTable;
import ru.moscow.heat.trace.model.RouteSegment;
import ru.moscow.heat.trace.model.TechnicalNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Назначает условный диаметр сегментам одного маршрута ОКС (план, шаг 8).
 * <p>
 * Правила (ТЗ, п.2.4):
 * <ul>
 *     <li>flow на всём маршруте одного ОКС = flow_tph этой точки
 *     (в итерации 5 объединение ОКС не делается — план, MVP);</li>
 *     <li>идём от ОКС к tie-in, накапливаем длину, пока ДУ по
 *     {@link DiameterTable#minDiameterForFlowAndLength(double, double)}
 *     не требует увеличения — тогда начинаем новый отсчёт длины и
 *     ставим {@link TechnicalNode} (DIAMETER_CHANGE) в начале сегмента,
 *     на котором сработало увеличение;</li>
 *     <li>инвариант: ДУ не убывает по направлению от ОКС к tie-in
 *     (в этом алгоритме это выполняется автоматически, так как ДУ
 *     только растёт по мере накопления длины).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DiameterAssigner {

    /** Результат: сегменты с назначенным ДУ + технические узлы смены ДУ. */
    public static final class AssignmentResult {
        private final List<RouteSegment> segments;
        private final List<TechnicalNode> technicalNodes;

        public AssignmentResult(List<RouteSegment> segments, List<TechnicalNode> technicalNodes) {
            this.segments = segments;
            this.technicalNodes = technicalNodes;
        }

        public List<RouteSegment> getSegments() {
            return segments;
        }

        public List<TechnicalNode> getTechnicalNodes() {
            return technicalNodes;
        }
    }

    private final DiameterTable diameterTable;

    /**
     * Проходит сегменты от ОКС к tie-in (порядок важен!) и заполняет
     * {@code diameterMm} для каждого, вставляя технические узлы там,
     * где ДУ увеличивается.
     *
     * @param orderedSegmentsFromOksToTieIn сегменты одного маршрута,
     *                                       упорядоченные от ОКС к tie-in
     * @param flowTph                       расход ОКС, т/ч
     * @return сегменты с назначенным ДУ + список технических узлов
     */
    public AssignmentResult assign(List<RouteSegment> orderedSegmentsFromOksToTieIn, BigDecimal flowTph) {
        double flow = flowTph.doubleValue();
        List<RouteSegment> result = new ArrayList<>();
        List<TechnicalNode> technicalNodes = new ArrayList<>();

        double accumulatedLength = 0.0;
        DiameterSpec currentSpec = diameterTable.minDiameterForFlowAndLength(flow, 0.0);

        for (RouteSegment segment : orderedSegmentsFromOksToTieIn) {
            accumulatedLength += segment.getLengthM();
            DiameterSpec required = diameterTable.minDiameterForFlowAndLength(flow, accumulatedLength);

            if (required.getDiameterMm() > currentSpec.getDiameterMm()) {
                technicalNodes.add(new TechnicalNode(
                        UUID.randomUUID(),
                        segment.getFromNode().getCoordinateUtm(),
                        TechnicalNode.Reason.DIAMETER_CHANGE));
                accumulatedLength = segment.getLengthM();
                currentSpec = required;
            }

            result.add(withDiameter(segment, currentSpec.getDiameterMm()));
        }

        return new AssignmentResult(result, technicalNodes);
    }

    private RouteSegment withDiameter(RouteSegment segment, int diameterMm) {
        return new RouteSegment(
                segment.getId(),
                segment.getFromNode(),
                segment.getToNode(),
                segment.getGeometryUtm(),
                segment.getFlowTph(),
                diameterMm,
                segment.getLayingMethod(),
                segment.getKspets(),
                segment.getLengthM(),
                segment.getCost());
    }
}
