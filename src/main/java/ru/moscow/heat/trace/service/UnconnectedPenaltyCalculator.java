package ru.moscow.heat.trace.service;

import org.springframework.stereotype.Service;
import ru.moscow.heat.trace.dto.UnconnectedOks;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/**
 * Калькулятор штрафов за неподключенные объекты капитального строительства (ОКС).
 * Формула штрафа за один неподключенный ОКС: 100_000_000 + 500_000 * flowTph.
 */
@Service
public class UnconnectedPenaltyCalculator {

    public static final BigDecimal BASE_PENALTY = BigDecimal.valueOf(100_000_000L);
    public static final BigDecimal FLOW_MULTIPLIER = BigDecimal.valueOf(500_000L);

    /**
     * Рассчитывает штраф за один неподключенный ОКС по расходу: 100_000_000 + 500_000 * flowTph.
     *
     * @param flowTph расчетный расход теплоносителя, т/ч
     * @return сумма штрафа в рублях
     */
    public BigDecimal calculatePenalty(double flowTph) {
        if (flowTph < 0) {
            throw new IllegalArgumentException("Расход теплоносителя не может быть отрицательным: " + flowTph);
        }
        BigDecimal flowPart = FLOW_MULTIPLIER.multiply(BigDecimal.valueOf(flowTph));
        return BASE_PENALTY.add(flowPart).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Рассчитывает штраф за заданный неподключенный объект ОКС.
     *
     * @param unconnectedOks неподключенный ОКС
     * @return сумма штрафа в рублях
     */
    public BigDecimal calculatePenalty(UnconnectedOks unconnectedOks) {
        Objects.requireNonNull(unconnectedOks, "UnconnectedOks не может быть null");
        return calculatePenalty(unconnectedOks.getFlowTph());
    }

    /**
     * Рассчитывает суммарный штраф по списку неподключенных ОКС.
     *
     * @param unconnectedList коллекция неподключенных ОКС
     * @return суммарный штраф в рублях
     */
    public BigDecimal calculateTotalPenalty(Collection<UnconnectedOks> unconnectedList) {
        if (unconnectedList == null || unconnectedList.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (UnconnectedOks oks : unconnectedList) {
            total = total.add(calculatePenalty(oks));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
