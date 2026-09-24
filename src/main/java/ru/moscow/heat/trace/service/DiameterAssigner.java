package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.moscow.heat.spatial.DiameterTable;
import ru.moscow.heat.trace.model.RouteSegment;

import java.math.BigDecimal;
import java.util.List;

/**
 * Назначает расход и условный диаметр сегментам одного маршрута ОКС
 * (план, шаг 8).
 * <p>
 * Правила:
 * <ul>
 *     <li>flow на всём маршруте одного ОКС = flow_tph этой точки
 *     (в итерации 5 объединение ОКС не делается — план, п.2.4 MVP);</li>
 *     <li>идём от ОКС к tie-in, для каждого сегмента —
 *     {@code DiameterTable.minDiameterForFlowAndLength(flow, accumulatedLength)},
 *     где accumulatedLength — сумма длин от начала текущего ДУ;</li>
 *     <li>если длина превышает maxLengthM текущего ДУ — увеличить ДУ,
 *     начать новый отсчёт длины, поставить TechnicalNode
 *     (DIAMETER_CHANGE) в точке смены;</li>
 *     <li>инвариант: ДУ не должен убывать по направлению от ОКС к tie-in.</li>
 * </ul>
 * <p>
 */
@Service
@RequiredArgsConstructor
public class DiameterAssigner {

    private final DiameterTable diameterTable;

    /**
     * Проходит сегменты от ОКС к tie-in (порядок важен!) и заполняет
     * {@code diameterMm} для каждого, при необходимости вставляя точки
     * смены ДУ.
     *
     * @param orderedSegmentsFromOksToTieIn сегменты одного маршрута,
     *                                       упорядоченные от ОКС к tie-in,
     *                                       ещё без назначенного diameterMm
     * @param flowTph                       расход ОКС, т/ч
     * @return сегменты с назначенным ДУ (возможно, с большим числом сегментов,
     * чем на входе — из-за вставленных точек смены ДУ)
     */
    public List<RouteSegment> assign(List<RouteSegment> orderedSegmentsFromOksToTieIn, BigDecimal flowTph) {
        // TODO:
        // double accumulatedLength = 0;
        // int currentDiameter = diameterTable.minDiameterForFlowAndLength(flowTph, 0);
        // for each segment (в порядке от ОКС к tie-in):
        //     accumulatedLength += segment.getLengthM();
        //     int required = diameterTable.minDiameterForFlowAndLength(flowTph, accumulatedLength);
        //     if (required > currentDiameter) {
        //         // вставить TechnicalNode(DIAMETER_CHANGE) в начале сегмента,
        //         // сбросить accumulatedLength = segment.getLengthM(),
        //         // currentDiameter = required;
        //     }
        //     // присвоить currentDiameter сегменту (пересобрать RouteSegment,
        //     // т.к. поля immutable)
        throw new UnsupportedOperationException("TODO: итерация 5, шаг 8");
    }
}
