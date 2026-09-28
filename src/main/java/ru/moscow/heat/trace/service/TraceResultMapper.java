package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.moscow.heat.trace.dto.ExistingChamberTieIn;
import ru.moscow.heat.trace.dto.VariantResult;
import ru.moscow.heat.trace.dto.VariantSummary;
import ru.moscow.heat.trace.model.NewChamber;
import ru.moscow.heat.trace.model.RouteNodeType;
import ru.moscow.heat.trace.model.RouteSegment;
import ru.moscow.heat.trace.model.TraceResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Тонкий маппер для преобразования внутреннего результата трассировки {@link TraceResult}
 * от {@link TraceOrchestrator} (A* с обходом препятствий) в формат вариантов {@link ru.moscow.heat.trace.dto.TraceResult}.
 */
@Slf4j
@Service
public class TraceResultMapper {

    private final CostCalculator costCalculator;
    private final ChamberCostCalculator chamberCostCalculator;
    private final VariantScoreCalculator variantScoreCalculator;
    private final VariantGenerator variantGenerator;

    @org.springframework.beans.factory.annotation.Autowired
    public TraceResultMapper(
            CostCalculator costCalculator,
            ChamberCostCalculator chamberCostCalculator,
            VariantScoreCalculator variantScoreCalculator,
            @org.springframework.beans.factory.annotation.Autowired(required = false) VariantGenerator variantGenerator) {
        this.costCalculator = costCalculator;
        this.chamberCostCalculator = chamberCostCalculator;
        this.variantScoreCalculator = variantScoreCalculator;
        this.variantGenerator = variantGenerator;
    }

    public TraceResultMapper(
            CostCalculator costCalculator,
            ChamberCostCalculator chamberCostCalculator,
            VariantScoreCalculator variantScoreCalculator) {
        this(costCalculator, chamberCostCalculator, variantScoreCalculator, null);
    }

    /**
     * Преобразует внутренний TraceResult в DTO TraceResult со сформированным вариантом v1
     * и дополнительными вариантами v2, v3 из VariantGenerator.
     *
     * @param uploadId   идентификатор загрузки
     * @param traceId    идентификатор трассировки
     * @param orchResult результат от TraceOrchestrator
     * @return DTO TraceResult
     */
    public ru.moscow.heat.trace.dto.TraceResult toTraceResult(
            UUID uploadId, UUID traceId, TraceResult orchResult) {

        List<RouteSegment> costedSegments = new ArrayList<>();
        Map<String, List<String>> tieInSegmentsMap = new LinkedHashMap<>();

        // 1. Сегменты с расчетом стоимости
        if (orchResult.getSegments() != null) {
            for (RouteSegment rawSeg : orchResult.getSegments()) {
                RouteSegment costedSeg = (rawSeg.getCost() != null)
                        ? rawSeg
                        : costCalculator.applyCost(rawSeg);
                costedSegments.add(costedSeg);

                if (rawSeg.getFromNode() != null && rawSeg.getFromNode().getType() == RouteNodeType.EXISTING_CHAMBER) {
                    String chId = rawSeg.getFromNode().getSourceFeatureId();
                    if (chId != null) {
                        tieInSegmentsMap.computeIfAbsent(chId, k -> new ArrayList<>()).add(costedSeg.getStringId());
                    }
                }
                if (rawSeg.getToNode() != null && rawSeg.getToNode().getType() == RouteNodeType.EXISTING_CHAMBER) {
                    String chId = rawSeg.getToNode().getSourceFeatureId();
                    if (chId != null) {
                        tieInSegmentsMap.computeIfAbsent(chId, k -> new ArrayList<>()).add(costedSeg.getStringId());
                    }
                }
            }
        }

        // 2. Новые камеры с расчетом стоимости
        List<NewChamber> costedChambers = new ArrayList<>();
        if (orchResult.getNewChambers() != null) {
            for (NewChamber rawChamber : orchResult.getNewChambers()) {
                int diameter = rawChamber.getDiameterMm() > 0 ? rawChamber.getDiameterMm() : 200;
                BigDecimal cost = (rawChamber.getCost() != null)
                        ? rawChamber.getCost()
                        : chamberCostCalculator.calculateCost(diameter);
                costedChambers.add(new NewChamber(rawChamber.getId(), rawChamber.getGeometry(), diameter, cost));
            }
        }

        // 3. Врезки в существующие камеры
        List<ExistingChamberTieIn> tieIns = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : tieInSegmentsMap.entrySet()) {
            tieIns.add(new ExistingChamberTieIn(entry.getKey(), entry.getValue()));
        }

        // 4. Неподключенные ОКС
        List<ru.moscow.heat.trace.dto.UnconnectedOks> unconnected = new ArrayList<>();
        if (orchResult.getUnconnectedOks() != null) {
            for (ru.moscow.heat.trace.model.UnconnectedOks u : orchResult.getUnconnectedOks()) {
                unconnected.add(new ru.moscow.heat.trace.dto.UnconnectedOks(
                        u.getOksPointFeatureId(),
                        0.0,
                        u.getDetails()
                ));
            }
        }

        // 5. Расчет сводки и скоринга основного варианта v1 (A* с обходом препятствий)
        VariantSummary summary = variantScoreCalculator.calculateSummary(
                "v1", costedSegments, costedChambers, tieIns, unconnected);

        VariantResult variant = new VariantResult(
                "v1", costedSegments, costedChambers, tieIns, unconnected, summary.withRank(1));

        List<VariantResult> allVariants = new ArrayList<>();
        allVariants.add(variant);

        // 6. Подмешиваем альтернативные варианты v2 и v3 по п. 2.8 ТЗ (не менее 3 вариантов)
        if (variantGenerator != null && uploadId != null) {
            try {
                ru.moscow.heat.trace.dto.TraceResult generated = variantGenerator.generateTraceResult(uploadId, traceId);
                if (generated != null && generated.getVariants() != null) {
                    int nextRank = 2;
                    for (VariantResult genVar : generated.getVariants()) {
                        if (!"v1".equals(genVar.getVariantId())) {
                            allVariants.add(genVar.withRank(nextRank++));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Не удалось сгенерировать дополнительные варианты для [{}]: {}", traceId, e.getMessage());
            }
        }

        return new ru.moscow.heat.trace.dto.TraceResult(
                traceId, uploadId, allVariants, unconnected);
    }
}
