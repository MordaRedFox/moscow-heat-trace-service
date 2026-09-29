package ru.moscow.heat.trace.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.moscow.heat.trace.dto.ExistingChamberTieIn;
import ru.moscow.heat.trace.dto.TraceResult;
import ru.moscow.heat.trace.dto.VariantResult;
import ru.moscow.heat.trace.dto.VariantSummary;
import ru.moscow.heat.trace.model.NewChamber;
import ru.moscow.heat.trace.model.RouteNodeType;
import ru.moscow.heat.trace.model.RouteSegment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Маппер внутренних результатов трассировки (список {@link ru.moscow.heat.trace.model.TraceResult}
 * от {@link TraceOrchestrator#runAll(UUID)}) в публичный {@link TraceResult}.
 *
 * <p>Каждый входной результат соответствует одной стратегии
 * {@link TraceOrchestrator.TraceStrategy}: v1 = MAIN, v2 = NO_GROUP,
 * v3 = ALT_TIE_IN.
 *
 * <p>Заполняет стоимость сегментов и камер, формирует врезки в существующие
 * камеры, считает {@link VariantSummary} через {@link VariantScoreCalculator}.
 */
@Slf4j
@Service
public class TraceResultMapper {

    private static final String[] VARIANT_IDS = {"v1", "v2", "v3"};

    private final CostCalculator costCalculator;
    private final ChamberCostCalculator chamberCostCalculator;
    private final VariantScoreCalculator variantScoreCalculator;

    public TraceResultMapper(CostCalculator costCalculator,
                             ChamberCostCalculator chamberCostCalculator,
                             VariantScoreCalculator variantScoreCalculator) {
        this.costCalculator = costCalculator;
        this.chamberCostCalculator = chamberCostCalculator;
        this.variantScoreCalculator = variantScoreCalculator;
    }

    /**
     * Преобразует список внутренних результатов в публичный {@link TraceResult}.
     *
     * @param uploadId     идентификатор загрузки
     * @param traceId      идентификатор трассировки
     * @param orchResults  список результатов от оркестратора (обычно 3 стратегии)
     * @return публичный DTO со списком вариантов
     */
    public TraceResult toTraceResult(UUID uploadId, UUID traceId,
                                     List<ru.moscow.heat.trace.model.TraceResult> orchResults) {
        List<VariantResult> variants = new ArrayList<>();

        if (orchResults != null) {
            int idx = 0;
            for (ru.moscow.heat.trace.model.TraceResult orchResult : orchResults) {
                String variantId = idx < VARIANT_IDS.length
                        ? VARIANT_IDS[idx] : ("v" + (idx + 1));
                variants.add(buildVariant(variantId, orchResult));
                idx++;
            }
        }

        List<ru.moscow.heat.trace.dto.UnconnectedOks> unconnected =
                extractUnconnected(orchResults);

        return new TraceResult(traceId, uploadId, variants, unconnected);
    }

    private VariantResult buildVariant(String variantId,
                                       ru.moscow.heat.trace.model.TraceResult orchResult) {

        List<RouteSegment> costedSegments = new ArrayList<>();
        Map<String, List<String>> tieInSegmentsMap = new LinkedHashMap<>();

        if (orchResult.getSegments() != null) {
            for (RouteSegment rawSeg : orchResult.getSegments()) {
                RouteSegment costed = (rawSeg.getCost() != null)
                        ? rawSeg
                        : costCalculator.applyCost(rawSeg);
                costedSegments.add(costed);

                if (rawSeg.getFromNode() != null
                        && rawSeg.getFromNode().getType() == RouteNodeType.EXISTING_CHAMBER) {
                    String chId = rawSeg.getFromNode().getSourceFeatureId();
                    if (chId != null) {
                        tieInSegmentsMap.computeIfAbsent(chId, k -> new ArrayList<>())
                                .add(costed.getStringId());
                    }
                }
                if (rawSeg.getToNode() != null
                        && rawSeg.getToNode().getType() == RouteNodeType.EXISTING_CHAMBER) {
                    String chId = rawSeg.getToNode().getSourceFeatureId();
                    if (chId != null) {
                        tieInSegmentsMap.computeIfAbsent(chId, k -> new ArrayList<>())
                                .add(costed.getStringId());
                    }
                }
            }
        }

        List<NewChamber> costedChambers = new ArrayList<>();
        if (orchResult.getNewChambers() != null) {
            for (NewChamber raw : orchResult.getNewChambers()) {
                int diameter = raw.getDiameterMm() > 0 ? raw.getDiameterMm() : 200;
                BigDecimal cost = (raw.getCost() != null)
                        ? raw.getCost()
                        : chamberCostCalculator.calculateCost(diameter);
                costedChambers.add(new NewChamber(raw.getId(), raw.getGeometry(),
                        diameter, cost));
            }
        }

        List<ExistingChamberTieIn> tieIns = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : tieInSegmentsMap.entrySet()) {
            tieIns.add(new ExistingChamberTieIn(entry.getKey(), entry.getValue()));
        }

        List<ru.moscow.heat.trace.dto.UnconnectedOks> unconnected =
                toDtoUnconnected(orchResult.getUnconnectedOks());

        VariantSummary summary = variantScoreCalculator.calculateSummary(
                variantId, costedSegments, costedChambers, tieIns, unconnected);

        return new VariantResult(variantId, costedSegments, costedChambers,
                tieIns, unconnected, summary);
    }

    private List<ru.moscow.heat.trace.dto.UnconnectedOks> extractUnconnected(
            List<ru.moscow.heat.trace.model.TraceResult> orchResults) {
        if (orchResults == null || orchResults.isEmpty()) {
            return List.of();
        }
        return toDtoUnconnected(orchResults.get(0).getUnconnectedOks());
    }

    private List<ru.moscow.heat.trace.dto.UnconnectedOks> toDtoUnconnected(
            List<ru.moscow.heat.trace.model.UnconnectedOks> source) {
        List<ru.moscow.heat.trace.dto.UnconnectedOks> result = new ArrayList<>();
        if (source != null) {
            for (ru.moscow.heat.trace.model.UnconnectedOks u : source) {
                result.add(new ru.moscow.heat.trace.dto.UnconnectedOks(
                        u.getOksPointFeatureId(), 0.0, u.getDetails()));
            }
        }
        return result;
    }
}
