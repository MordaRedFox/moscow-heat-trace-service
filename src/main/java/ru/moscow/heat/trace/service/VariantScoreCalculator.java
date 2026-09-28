package ru.moscow.heat.trace.service;

import org.springframework.stereotype.Service;
import ru.moscow.heat.trace.dto.ExistingChamberTieIn;
import ru.moscow.heat.trace.dto.UnconnectedOks;
import ru.moscow.heat.trace.dto.VariantSummary;
import ru.moscow.heat.trace.model.NewChamber;
import ru.moscow.heat.trace.model.RouteSegment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Калькулятор интегрального балла (score) и сводки варианта трассировки.
 * Формула балла: Score = 0.7 * (calculatedCost / 25_000_000) + 0.3 * (newNetworkLength / 100).
 */
@Service
public class VariantScoreCalculator {

    public static final double COST_NORMALIZER = 25_000_000.0;
    public static final double LENGTH_NORMALIZER = 100.0;
    public static final double WEIGHT_COST = 0.7;
    public static final double WEIGHT_LENGTH = 0.3;

    /**
     * Вычисляет интегральный балл (score) варианта трассировки.
     *
     * @param calculatedCost   расчетная приведенная стоимость варианта в рублях
     * @param newNetworkLength суммарная длина новой сети в метрах
     * @return интегральный балл
     */
    public double calculateScore(BigDecimal calculatedCost, double newNetworkLength) {
        double costValue = calculatedCost != null ? calculatedCost.doubleValue() : 0.0;
        double costPart = WEIGHT_COST * (costValue / COST_NORMALIZER);
        double lengthPart = WEIGHT_LENGTH * (newNetworkLength / LENGTH_NORMALIZER);
        return costPart + lengthPart;
    }

    /**
     * Формирует сводные показатели (VariantSummary) для варианта трассировки.
     *
     * @param variantId       идентификатор варианта (например, "v1")
     * @param segments        сегменты сети
     * @param chambers        новые тепловые камеры
     * @param tieIns          врезки в существующие камеры
     * @param unconnectedList список неподключенных ОКС
     * @return сформированный VariantSummary
     */
    public VariantSummary calculateSummary(
            String variantId,
            Collection<RouteSegment> segments,
            Collection<NewChamber> chambers,
            Collection<ExistingChamberTieIn> tieIns,
            Collection<UnconnectedOks> unconnectedList) {

        BigDecimal constructionCost = BigDecimal.ZERO;
        double totalLength = 0.0;
        if (segments != null) {
            for (RouteSegment segment : segments) {
                totalLength += segment.getLengthM();
                if (segment.getCost() != null) {
                    constructionCost = constructionCost.add(segment.getCost());
                }
            }
        }

        BigDecimal chamberCost = BigDecimal.ZERO;
        if (chambers != null) {
            for (NewChamber chamber : chambers) {
                if (chamber.getCost() != null) {
                    chamberCost = chamberCost.add(chamber.getCost());
                }
            }
        }

        int tieInCount = 0;
        BigDecimal tieInCost = BigDecimal.ZERO;
        if (tieIns != null) {
            for (ExistingChamberTieIn tieIn : tieIns) {
                tieInCount += tieIn.getAttachedSegmentIds().size();
                if (tieIn.getCost() != null) {
                    tieInCost = tieInCost.add(tieIn.getCost());
                }
            }
        }

        BigDecimal penalty = BigDecimal.ZERO;
        List<String> unconnectedIds = List.of();
        if (unconnectedList != null && !unconnectedList.isEmpty()) {
            UnconnectedPenaltyCalculator penaltyCalc = new UnconnectedPenaltyCalculator();
            penalty = penaltyCalc.calculateTotalPenalty(unconnectedList);
            unconnectedIds = unconnectedList.stream()
                    .map(UnconnectedOks::getOksFeatureId)
                    .collect(Collectors.toList());
        }

        BigDecimal calculatedCost = constructionCost
                .add(chamberCost)
                .add(tieInCost)
                .add(penalty)
                .setScale(2, RoundingMode.HALF_UP);

        double score = calculateScore(calculatedCost, totalLength);

        return new VariantSummary(
                variantId,
                null,
                constructionCost,
                chamberCost,
                tieInCount,
                tieInCost,
                penalty,
                calculatedCost,
                totalLength,
                score,
                unconnectedIds
        );
    }
}
