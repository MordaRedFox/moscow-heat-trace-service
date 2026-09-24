package ru.moscow.heat.trace.service;

import org.locationtech.jts.geom.Coordinate;
import org.springframework.stereotype.Service;
import ru.moscow.heat.trace.graph.ObstacleModel;
import ru.moscow.heat.trace.model.RouteSegment;
import ru.moscow.heat.trace.model.TechnicalNode;

import java.util.List;

/**
 * Разбивает упрощённый путь на {@link RouteSegment} по точкам разбиения
 * (план, шаг 7):
 * <ul>
 *     <li>конец спецзоны (граница special-полигона);</li>
 *     <li>смена Kспец (при наложении двух special-зон — Kспец = max);</li>
 *     <li>смена ДУ (после {@code DiameterAssigner});</li>
 *     <li>конец одного сегмента — начало другого.</li>
 * </ul>
 * В каждой точке разбиения, кроме узлов ОКС и камер, ставится
 * {@link TechnicalNode}.
 * <p>
 * Обратите внимание: смена ДУ известна только после {@code DiameterAssigner},
 * поэтому разбиение по спецзонам/Kспец делается первым проходом здесь,
 * а повторное разбиение по ДУ выполняется как часть шага 8
 * (см. {@code DiameterAssigner}).
 */
@Service
public class RouteSegmentSplitter {

    /** Результат первичного разбиения — до назначения ДУ. */
    public static final class SplitPoint {
        // TODO: координата, LayingMethod, kspets на исходящем сегменте, TechnicalNode.Reason
    }

    /**
     * Разбивает упрощённый путь по спецзонам и границам Kспец.
     *
     * @param simplifiedPathUtm путь после {@code RouteSimplifier}
     * @param obstacleModel     модель препятствий (для определения спецзон)
     * @return точки разбиения по порядку от start к end
     */
    public List<SplitPoint> split(List<Coordinate> simplifiedPathUtm, ObstacleModel obstacleModel) {
        // TODO
        throw new UnsupportedOperationException("TODO: итерация 5, шаг 7");
    }
}
