package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.repository.OksConnectionPointRepository;
import ru.moscow.heat.spatial.DiameterSpec;
import ru.moscow.heat.spatial.DiameterTable;
import ru.moscow.heat.spatial.GeometryUtils;
import ru.moscow.heat.trace.dto.*;
import ru.moscow.heat.trace.model.NewChamber;
import ru.moscow.heat.trace.model.RouteNode;
import ru.moscow.heat.trace.model.RouteSegment;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Генератор альтернативных вариантов трассировки тепловой сети с дедупликацией и ранжированием.
 * Формирует до 3-х вариантов:
 * <ul>
 *   <li>Variant 1 (Main): группировка ОКС с объединением в общие участки сети и дерево трассировки.</li>
 *   <li>Variant 2 (No Grouping): каждый ОКС подключается индивидуальным участком к точке врезки.</li>
 *   <li>Variant 3 (Alt Tie-in): использование альтернативных кандидатов на присоединение.</li>
 * </ul>
 * Выполняет дедупликацию (отличие score &lt; 0.1% или идентичные множества врезок) и ранжирование по score.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VariantGenerator {

    private static final double GROUPING_DISTANCE_METERS = 200.0;
    private static final double SCORE_DIFF_TOLERANCE_RATIO = 0.001; // 0.1%

    private final OksConnectionPointRepository oksConnectionPointRepository;
    private final TieInCandidateService tieInCandidateService;
    private final DiameterTable diameterTable;
    private final CostCalculator costCalculator;
    private final ChamberCostCalculator chamberCostCalculator;
    private final VariantScoreCalculator variantScoreCalculator;
    private final VariantRanker variantRanker;
    private final GeometryUtils geometryUtils;
    private final GeometryFactory geometryFactory = new GeometryFactory();

    /**
     * Генерирует ранжированный результат трассировки со списком альтернативных вариантов.
     *
     * @param uploadId идентификатор сессии загрузки
     * @param traceId  идентификатор задачи трассировки
     * @return полный результат трассировки TraceResult
     */
    @Transactional(readOnly = true)
    public TraceResult generateTraceResult(UUID uploadId, UUID traceId) {
        List<OksConnectionPointEntity> points = oksConnectionPointRepository.findByUploadId(uploadId);
        if (points.isEmpty()) {
            return new TraceResult(traceId, uploadId, List.of(), List.of());
        }

        Map<String, List<TieInCandidate>> candidatesByPoint =
                tieInCandidateService.findCandidatesForAllPoints(uploadId);

        List<UnconnectedOks> unconnectedOks = new ArrayList<>();
        List<OksConnectionPointEntity> connectedPoints = new ArrayList<>();

        for (OksConnectionPointEntity point : points) {
            List<TieInCandidate> candidates = candidatesByPoint.get(point.getFeatureId());
            if (candidates == null || candidates.isEmpty()) {
                double flow = point.getFlowTph() != null ? point.getFlowTph() : 0.0;
                unconnectedOks.add(new UnconnectedOks(
                        point.getFeatureId(),
                        flow,
                        "Отсутствуют допустимые кандидаты на присоединение к тепловой сети"
                ));
            } else {
                connectedPoints.add(point);
            }
        }

        List<VariantResult> rawVariants = new ArrayList<>();

        // 1. Variant 1: Main (группировка + дерево)
        VariantResult v1 = buildMainVariant("v1", connectedPoints, candidatesByPoint, unconnectedOks);
        if (v1 != null) {
            rawVariants.add(v1);
        }

        // 2. Variant 2: No Grouping (каждый ОКС - одиночная группа)
        VariantResult v2 = buildNoGroupingVariant("v2", connectedPoints, candidatesByPoint, unconnectedOks);
        if (v2 != null) {
            rawVariants.add(v2);
        }

        // 3. Variant 3: Alt Tie-in (альтернативный кандидат)
        VariantResult v3 = buildAltTieInVariant("v3", connectedPoints, candidatesByPoint, unconnectedOks);
        if (v3 != null) {
            rawVariants.add(v3);
        }

        // 4. Дедупликация
        List<VariantResult> deduplicated = deduplicateVariants(rawVariants);

        // 5. Ранжирование
        List<VariantResult> ranked = variantRanker.rankVariants(deduplicated);

        log.info("Трассировка [{}]: сформировано {} вариантов (после дедупликации: {})",
                traceId, rawVariants.size(), ranked.size());

        return new TraceResult(traceId, uploadId, ranked, unconnectedOks);
    }

    /**
     * Вариант 1 (Main): Группировка близких ОКС с общей врезкой в магистральный участок.
     */
    private VariantResult buildMainVariant(
            String variantId,
            List<OksConnectionPointEntity> points,
            Map<String, List<TieInCandidate>> candidatesByPoint,
            List<UnconnectedOks> unconnectedOks) {

        if (points.isEmpty()) {
            VariantSummary summary = variantScoreCalculator.calculateSummary(
                    variantId, List.of(), List.of(), List.of(), unconnectedOks);
            return new VariantResult(variantId, List.of(), List.of(), List.of(), unconnectedOks, summary);
        }

        // Группируем ОКС по их лучшей точке врезки
        Map<String, List<OksConnectionPointEntity>> byTieInKey = new LinkedHashMap<>();
        Map<String, TieInCandidate> bestCandidateByPoint = new HashMap<>();

        for (OksConnectionPointEntity p : points) {
            TieInCandidate best = candidatesByPoint.get(p.getFeatureId()).get(0);
            bestCandidateByPoint.put(p.getFeatureId(), best);
            String tieInKey = getTieInKey(best);
            byTieInKey.computeIfAbsent(tieInKey, k -> new ArrayList<>()).add(p);
        }

        List<RouteSegment> allSegments = new ArrayList<>();
        List<NewChamber> allChambers = new ArrayList<>();
        Map<String, List<String>> tieInSegmentsMap = new LinkedHashMap<>();
        Map<String, String> newChamberUuidMap = new HashMap<>();

        int segmentCounter = 1;

        for (Map.Entry<String, List<OksConnectionPointEntity>> entry : byTieInKey.entrySet()) {
            List<OksConnectionPointEntity> groupPoints = entry.getValue();
            TieInCandidate tieIn = bestCandidateByPoint.get(groupPoints.get(0).getFeatureId());
            Point tieInPoint = tieIn.getTieInPoint();

            RouteNode tieInNode = createTieInNode(tieIn, tieInPoint, newChamberUuidMap);

            if (groupPoints.size() == 1) {
                // Одиночный ОКС в группе
                OksConnectionPointEntity p = groupPoints.get(0);
                Point pGeom = (Point) p.getGeometry();
                RouteNode oksNode = new RouteNode("node-oks-" + p.getFeatureId(), p.getFeatureId(), pGeom, "oks");

                double length = geometryUtils.distanceMeters(pGeom, tieInPoint);
                double flow = p.getFlowTph() != null ? p.getFlowTph() : 0.0;
                DiameterSpec spec = diameterTable.minDiameterForFlowAndLength(flow, length);

                LineString line = geometryFactory.createLineString(new Coordinate[]{
                        pGeom.getCoordinate(), tieInPoint.getCoordinate()
                });
                String segId = variantId + "-seg-" + (segmentCounter++);
                RouteSegment seg = costCalculator.applyCost(new RouteSegment(
                        segId, oksNode, tieInNode, line, length, spec.getDiameterMm(), flow, 1.0, 1.0, null
                ));
                allSegments.add(seg);
                recordTieInSegment(tieIn, seg.getStringId(), tieInSegmentsMap, allChambers, spec.getDiameterMm(), newChamberUuidMap);
            } else {
                // Несколько ОКС подключаются к одной врезке: создаем разветвительный технический узел (центроид)
                double sumX = 0, sumY = 0;
                double totalFlow = 0;
                for (OksConnectionPointEntity p : groupPoints) {
                    Point pt = (Point) p.getGeometry();
                    sumX += pt.getX();
                    sumY += pt.getY();
                    totalFlow += (p.getFlowTph() != null ? p.getFlowTph() : 0.0);
                }
                Point techPoint = geometryFactory.createPoint(new Coordinate(sumX / groupPoints.size(), sumY / groupPoints.size()));
                RouteNode techNode = new RouteNode(UUID.randomUUID().toString(), null, techPoint, "technical_node");

                // Магистральный сегмент от технического узла до врезки с суммарным расходом
                double mainLength = geometryUtils.distanceMeters(techPoint, tieInPoint);
                DiameterSpec mainSpec = diameterTable.minDiameterForFlowAndLength(totalFlow, mainLength);
                LineString mainLine = geometryFactory.createLineString(new Coordinate[]{
                        techPoint.getCoordinate(), tieInPoint.getCoordinate()
                });
                String mainSegId = variantId + "-seg-" + (segmentCounter++);
                RouteSegment mainSeg = costCalculator.applyCost(new RouteSegment(
                        mainSegId, techNode, tieInNode, mainLine, mainLength, mainSpec.getDiameterMm(), totalFlow, 1.0, 1.0, null
                ));
                allSegments.add(mainSeg);
                recordTieInSegment(tieIn, mainSeg.getStringId(), tieInSegmentsMap, allChambers, mainSpec.getDiameterMm(), newChamberUuidMap);

                // Ответвления от каждого ОКС до технического узла
                for (OksConnectionPointEntity p : groupPoints) {
                    Point pGeom = (Point) p.getGeometry();
                    RouteNode oksNode = new RouteNode("node-oks-" + p.getFeatureId(), p.getFeatureId(), pGeom, "oks");
                    double branchLength = geometryUtils.distanceMeters(pGeom, techPoint);
                    double branchFlow = p.getFlowTph() != null ? p.getFlowTph() : 0.0;
                    DiameterSpec branchSpec = diameterTable.minDiameterForFlowAndLength(branchFlow, branchLength);
                    LineString branchLine = geometryFactory.createLineString(new Coordinate[]{
                            pGeom.getCoordinate(), techPoint.getCoordinate()
                    });
                    String branchSegId = variantId + "-seg-" + (segmentCounter++);
                    RouteSegment branchSeg = costCalculator.applyCost(new RouteSegment(
                            branchSegId, oksNode, techNode, branchLine, branchLength, branchSpec.getDiameterMm(), branchFlow, 1.0, 1.0, null
                    ));
                    allSegments.add(branchSeg);
                }
            }
        }

        List<ExistingChamberTieIn> tieIns = buildExistingTieIns(tieInSegmentsMap);
        VariantSummary summary = variantScoreCalculator.calculateSummary(
                variantId, allSegments, allChambers, tieIns, unconnectedOks);

        return new VariantResult(variantId, allSegments, allChambers, tieIns, unconnectedOks, summary);
    }

    /**
     * Вариант 2 (No Grouping): каждый ОКС подключается напрямую к своему лучшему кандидату на присоединение.
     */
    private VariantResult buildNoGroupingVariant(
            String variantId,
            List<OksConnectionPointEntity> points,
            Map<String, List<TieInCandidate>> candidatesByPoint,
            List<UnconnectedOks> unconnectedOks) {

        if (points.isEmpty()) {
            VariantSummary summary = variantScoreCalculator.calculateSummary(
                    variantId, List.of(), List.of(), List.of(), unconnectedOks);
            return new VariantResult(variantId, List.of(), List.of(), List.of(), unconnectedOks, summary);
        }

        List<RouteSegment> allSegments = new ArrayList<>();
        List<NewChamber> allChambers = new ArrayList<>();
        Map<String, List<String>> tieInSegmentsMap = new LinkedHashMap<>();
        Map<String, String> newChamberUuidMap = new HashMap<>();

        int segmentCounter = 1;

        for (OksConnectionPointEntity p : points) {
            TieInCandidate tieIn = candidatesByPoint.get(p.getFeatureId()).get(0);
            Point tieInPoint = tieIn.getTieInPoint();
            RouteNode tieInNode = createTieInNode(tieIn, tieInPoint, newChamberUuidMap);

            Point pGeom = (Point) p.getGeometry();
            RouteNode oksNode = new RouteNode("node-oks-" + p.getFeatureId(), p.getFeatureId(), pGeom, "oks");

            double length = geometryUtils.distanceMeters(pGeom, tieInPoint);
            double flow = p.getFlowTph() != null ? p.getFlowTph() : 0.0;
            DiameterSpec spec = diameterTable.minDiameterForFlowAndLength(flow, length);

            LineString line = geometryFactory.createLineString(new Coordinate[]{
                    pGeom.getCoordinate(), tieInPoint.getCoordinate()
            });
            String segId = variantId + "-seg-" + (segmentCounter++);
            RouteSegment seg = costCalculator.applyCost(new RouteSegment(
                    segId, oksNode, tieInNode, line, length, spec.getDiameterMm(), flow, 1.0, 1.0, null
            ));
            allSegments.add(seg);
            recordTieInSegment(tieIn, seg.getStringId(), tieInSegmentsMap, allChambers, spec.getDiameterMm(), newChamberUuidMap);
        }

        List<ExistingChamberTieIn> tieIns = buildExistingTieIns(tieInSegmentsMap);
        VariantSummary summary = variantScoreCalculator.calculateSummary(
                variantId, allSegments, allChambers, tieIns, unconnectedOks);

        return new VariantResult(variantId, allSegments, allChambers, tieIns, unconnectedOks, summary);
    }

    /**
     * Вариант 3 (Alt Tie-in): для ОКС выбирается альтернативный кандидат из TieInCandidateService (если кандидатов > 1).
     */
    private VariantResult buildAltTieInVariant(
            String variantId,
            List<OksConnectionPointEntity> points,
            Map<String, List<TieInCandidate>> candidatesByPoint,
            List<UnconnectedOks> unconnectedOks) {

        if (points.isEmpty()) {
            return null;
        }

        // Проверяем, есть ли хотя бы один альтернативный кандидат
        boolean hasAlt = false;
        for (OksConnectionPointEntity p : points) {
            List<TieInCandidate> list = candidatesByPoint.get(p.getFeatureId());
            if (list != null && list.size() > 1) {
                hasAlt = true;
                break;
            }
        }
        if (!hasAlt) {
            return null;
        }

        List<RouteSegment> allSegments = new ArrayList<>();
        List<NewChamber> allChambers = new ArrayList<>();
        Map<String, List<String>> tieInSegmentsMap = new LinkedHashMap<>();
        Map<String, String> newChamberUuidMap = new HashMap<>();

        int segmentCounter = 1;

        for (OksConnectionPointEntity p : points) {
            List<TieInCandidate> list = candidatesByPoint.get(p.getFeatureId());
            // Если есть альтернативный кандидат, выбираем его (индекс 1), иначе лучший (индекс 0)
            TieInCandidate tieIn = (list.size() > 1) ? list.get(1) : list.get(0);
            Point tieInPoint = tieIn.getTieInPoint();
            RouteNode tieInNode = createTieInNode(tieIn, tieInPoint, newChamberUuidMap);

            Point pGeom = (Point) p.getGeometry();
            RouteNode oksNode = new RouteNode("node-oks-" + p.getFeatureId(), p.getFeatureId(), pGeom, "oks");

            double length = geometryUtils.distanceMeters(pGeom, tieInPoint);
            double flow = p.getFlowTph() != null ? p.getFlowTph() : 0.0;
            DiameterSpec spec = diameterTable.minDiameterForFlowAndLength(flow, length);

            LineString line = geometryFactory.createLineString(new Coordinate[]{
                    pGeom.getCoordinate(), tieInPoint.getCoordinate()
            });
            String segId = variantId + "-seg-" + (segmentCounter++);
            RouteSegment seg = costCalculator.applyCost(new RouteSegment(
                    segId, oksNode, tieInNode, line, length, spec.getDiameterMm(), flow, 1.0, 1.0, null
            ));
            allSegments.add(seg);
            recordTieInSegment(tieIn, seg.getStringId(), tieInSegmentsMap, allChambers, spec.getDiameterMm(), newChamberUuidMap);
        }

        List<ExistingChamberTieIn> tieIns = buildExistingTieIns(tieInSegmentsMap);
        VariantSummary summary = variantScoreCalculator.calculateSummary(
                variantId, allSegments, allChambers, tieIns, unconnectedOks);

        return new VariantResult(variantId, allSegments, allChambers, tieIns, unconnectedOks, summary);
    }

    /**
     * Дедупликация вариантов: если score отличается менее чем на 0.1% или множества врезок идентичны,
     * оставляется только один вариант.
     */
    public List<VariantResult> deduplicateVariants(List<VariantResult> variants) {
        if (variants == null || variants.isEmpty()) {
            return List.of();
        }

        List<VariantResult> result = new ArrayList<>();

        for (VariantResult candidate : variants) {
            boolean isDuplicate = false;
            for (VariantResult accepted : result) {
                if (isDuplicateOf(candidate, accepted)) {
                    isDuplicate = true;
                    log.info("Вариант [{}] отброшен как дубликат варианта [{}]",
                            candidate.getVariantId(), accepted.getVariantId());
                    break;
                }
            }
            if (!isDuplicate) {
                result.add(candidate);
            }
        }
        return result;
    }

    private boolean isDuplicateOf(VariantResult v1, VariantResult v2) {
        if (v1.getSummary() == null || v2.getSummary() == null) {
            return false;
        }

        // 1. Проверка множества врезок (существующие камеры + новые камеры)
        Set<String> tieIns1 = extractTieInIdentifiers(v1);
        Set<String> tieIns2 = extractTieInIdentifiers(v2);
        if (!tieIns1.isEmpty() && tieIns1.equals(tieIns2)) {
            return true;
        }

        // 2. Проверка score: отличие менее чем на 0.1%
        double s1 = v1.getSummary().getScore();
        double s2 = v2.getSummary().getScore();
        double maxScore = Math.max(s1, s2);
        if (maxScore > 0.0) {
            double diffRatio = Math.abs(s1 - s2) / maxScore;
            if (diffRatio < SCORE_DIFF_TOLERANCE_RATIO) {
                return true;
            }
        } else if (Math.abs(s1 - s2) < 1e-9) {
            return true;
        }

        return false;
    }

    private Set<String> extractTieInIdentifiers(VariantResult variant) {
        Set<String> set = new HashSet<>();
        if (variant.getTieIns() != null) {
            for (ExistingChamberTieIn t : variant.getTieIns()) {
                set.add("exist:" + t.getChamberFeatureId());
            }
        }
        if (variant.getChambers() != null) {
            for (NewChamber c : variant.getChambers()) {
                set.add("new:@" + Math.round(c.getGeometry().getX()) + "," + Math.round(c.getGeometry().getY()));
            }
        }
        return set;
    }

    private String getTieInKey(TieInCandidate candidate) {
        if (candidate.getTieInType() == TieInType.EXISTING_CHAMBER && candidate.getExistingChamberId() != null) {
            return "existing:" + candidate.getExistingChamberId();
        }
        Point pt = candidate.getTieInPoint();
        return "new:" + Math.round(pt.getX()) + ":" + Math.round(pt.getY());
    }

    private String getNewChamberId(Point pt, Map<String, String> newChamberUuidMap) {
        String key = Math.round(pt.getX()) + ":" + Math.round(pt.getY());
        return newChamberUuidMap.computeIfAbsent(key, k -> UUID.randomUUID().toString());
    }

    private RouteNode createTieInNode(
            TieInCandidate candidate, Point tieInPoint, Map<String, String> newChamberUuidMap) {
        if (candidate.getTieInType() == TieInType.EXISTING_CHAMBER) {
            return new RouteNode(
                    candidate.getExistingChamberId(),
                    candidate.getExistingChamberId(),
                    tieInPoint,
                    "existing_chamber"
            );
        } else {
            String chId = getNewChamberId(tieInPoint, newChamberUuidMap);
            return new RouteNode(
                    chId,
                    null,
                    tieInPoint,
                    "new_chamber"
            );
        }
    }

    private void recordTieInSegment(
            TieInCandidate candidate,
            String segmentId,
            Map<String, List<String>> tieInSegmentsMap,
            List<NewChamber> allChambers,
            int segmentDiameter,
            Map<String, String> newChamberUuidMap) {

        if (candidate.getTieInType() == TieInType.EXISTING_CHAMBER) {
            tieInSegmentsMap.computeIfAbsent(candidate.getExistingChamberId(), k -> new ArrayList<>()).add(segmentId);
        } else {
            // Новая камера
            Point pt = candidate.getTieInPoint();
            String chamberId = getNewChamberId(pt, newChamberUuidMap);
            boolean exists = allChambers.stream().anyMatch(c -> c.getId().equals(chamberId));
            if (!exists) {
                int dn = candidate.getRequiredChamberDiameter() != null
                        ? candidate.getRequiredChamberDiameter()
                        : segmentDiameter;
                allChambers.add(chamberCostCalculator.createChamber(chamberId, pt, dn));
            }
        }
    }

    private List<ExistingChamberTieIn> buildExistingTieIns(Map<String, List<String>> map) {
        List<ExistingChamberTieIn> list = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            list.add(new ExistingChamberTieIn(entry.getKey(), entry.getValue()));
        }
        return list;
    }
}
