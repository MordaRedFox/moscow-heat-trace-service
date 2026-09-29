package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.repository.OksConnectionPointRepository;
import ru.moscow.heat.geojson.service.CoordinateTransformService;
import ru.moscow.heat.spatial.OksConnectionPointResolver;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TieInType;
import ru.moscow.heat.trace.graph.ObstacleModel;
import ru.moscow.heat.trace.graph.ObstacleModelBuilder;
import ru.moscow.heat.trace.graph.VisibilityGraph;
import ru.moscow.heat.trace.model.NewChamber;
import ru.moscow.heat.trace.model.OksGroup;
import ru.moscow.heat.trace.model.RouteNodeType;
import ru.moscow.heat.trace.model.RouteSegment;
import ru.moscow.heat.trace.model.RouteTree;
import ru.moscow.heat.trace.model.TechnicalNode;
import ru.moscow.heat.trace.model.TraceResult;
import ru.moscow.heat.trace.model.TreeEdge;
import ru.moscow.heat.trace.model.UnconnectedOks;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Главный сценарий трассировки одной загрузки (итерации 6–7).
 *
 * <p>Поддерживает три стратегии формирования варианта:
 * <ul>
 *   <li>{@link TraceStrategy#MAIN} — группировка ОКС и лучший кандидат врезки;</li>
 *   <li>{@link TraceStrategy#NO_GROUP} — каждый ОКС отдельно, лучший кандидат;</li>
 *   <li>{@link TraceStrategy#ALT_TIE_IN} — группировка, альтернативный кандидат
 *       (второй в списке, если есть).</li>
 * </ul>
 *
 * <p>Все три стратегии используют полный конвейер: A* по visibility graph,
 * RouteSimplifier, AngleChecker, разбиение на сегменты, назначение ДУ,
 * проверку предельной длины. Никаких прямых линий в обход препятствий.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceOrchestrator {

    /** Стратегия формирования варианта. */
    public enum TraceStrategy {
        MAIN,
        NO_GROUP,
        ALT_TIE_IN
    }

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final int WGS84_SRID = 4326;

    private final OksConnectionPointRepository oksRepository;
    private final TieInCandidateService tieInCandidateService;
    private final OksConnectionPointResolver oksResolver;
    private final ObstacleModelBuilder obstacleModelBuilder;
    private final CoordinateTransformService coordinateTransformService;
    private final RouteSimplifier routeSimplifier;
    private final RouteSegmentSplitter routeSegmentSplitter;
    private final AngleChecker angleChecker;
    private final DiameterAssigner diameterAssigner;
    private final LengthValidator lengthValidator;

    private final OksGrouper oksGrouper;
    private final TreeRouter treeRouter;
    private final FlowAggregator flowAggregator;
    private final TreeDiameterAssigner treeDiameterAssigner;
    private final TreeRouteSegmentSplitter treeRouteSegmentSplitter;

    /**
     * Основной запуск (стратегия MAIN). Используется в тестах и в одиночных вызовах.
     *
     * @param uploadId id загрузки
     * @return результат трассировки (одна стратегия)
     */
    public TraceResult run(UUID uploadId) {
        return run(uploadId, TraceStrategy.MAIN);
    }

    /**
     * Запуск трассировки с заданной стратегией.
     *
     * @param uploadId id загрузки
     * @param strategy стратегия формирования варианта
     * @return результат трассировки
     */
    public TraceResult run(UUID uploadId, TraceStrategy strategy) {
        List<OksConnectionPointEntity> oksPoints =
                oksRepository.findByUploadId(uploadId);
        log.info("Трассировка uploadId={}, strategy={}: {} ОКС",
                uploadId, strategy, oksPoints.size());

        ObstacleModel obstacleModel = obstacleModelBuilder.build(uploadId);
        VisibilityGraph graph = new VisibilityGraph(obstacleModel);
        graph.build();

        Map<String, List<TieInCandidate>> allCandidates =
                tieInCandidateService.findCandidatesForAllPoints(uploadId);

        List<OksConnectionPointEntity> validOks = new ArrayList<>();
        List<UnconnectedOks> unconnected = new ArrayList<>();
        Map<String, TieInCandidate> selectedCandidates = new HashMap<>();

        boolean useAlt = (strategy == TraceStrategy.ALT_TIE_IN);

        for (OksConnectionPointEntity oks : oksPoints) {
            List<TieInCandidate> candidates =
                    allCandidates.get(oks.getFeatureId());
            if (candidates == null || candidates.isEmpty()) {
                unconnected.add(new UnconnectedOks(oks.getFeatureId(),
                        UnconnectedOks.Reason.NO_TIE_IN_CANDIDATE,
                        "TieInCandidateService не вернул кандидатов"));
                continue;
            }
            TieInCandidate chosen = candidates.get(0);
            if (useAlt && candidates.size() > 1) {
                chosen = candidates.get(1);
            }
            validOks.add(oks);
            selectedCandidates.put(oks.getFeatureId(), chosen);
        }

        boolean noGroup = (strategy == TraceStrategy.NO_GROUP);
        List<OksGroup> groups = oksGrouper.group(
                validOks, selectedCandidates, noGroup);

        List<RouteSegment> allSegments = new ArrayList<>();
        List<TechnicalNode> allTechnicalNodes = new ArrayList<>();
        List<NewChamber> allNewChambers = new ArrayList<>();

        int multiGroupCount = 0;
        int fallbackCount = 0;

        for (OksGroup group : groups) {
            if (group.isMulti()) {
                boolean success;
                try {
                    success = processGroup(group, uploadId,
                            obstacleModel, graph,
                            allSegments, allTechnicalNodes,
                            allNewChambers, unconnected);
                } catch (Exception e) {
                    log.error("Ошибка обработки группы из {} ОКС: {}",
                            group.size(), e.getMessage(), e);
                    fallbackToIndividual(group, uploadId,
                            obstacleModel, graph,
                            allSegments, allTechnicalNodes,
                            allNewChambers, unconnected);
                    success = false;
                }
                if (success) {
                    multiGroupCount++;
                } else {
                    fallbackCount++;
                }
            } else {
                processOne(group.getPoints().get(0),
                        group.getCandidates().get(0),
                        uploadId, obstacleModel, graph,
                        allSegments, allTechnicalNodes,
                        allNewChambers, unconnected);
            }
        }

        log.info("Трассировка {}: {} групп ({} много-ОКС, {} fallback), "
                        + "{} сегментов, {} камер, {} неподключённых",
                strategy, groups.size(), multiGroupCount, fallbackCount,
                allSegments.size(), allNewChambers.size(),
                unconnected.size());

        TraceResult.SummaryCounters counters =
                new TraceResult.SummaryCounters(
                        oksPoints.size(),
                        oksPoints.size() - unconnected.size(),
                        unconnected.size());

        return new TraceResult(allSegments, allNewChambers,
                allTechnicalNodes, unconnected, counters);
    }

    /**
     * Запускает все три стратегии. Используется в async-процессоре для
     * формирования полного набора вариантов.
     *
     * @param uploadId id загрузки
     * @return список результатов: [MAIN, NO_GROUP, ALT_TIE_IN]
     */
    public List<TraceResult> runAll(UUID uploadId) {
        List<TraceResult> results = new ArrayList<>();
        results.add(run(uploadId, TraceStrategy.MAIN));
        results.add(run(uploadId, TraceStrategy.NO_GROUP));
        results.add(run(uploadId, TraceStrategy.ALT_TIE_IN));
        return results;
    }

    private boolean processGroup(OksGroup group, UUID uploadId,
                                 ObstacleModel obstacleModel,
                                 VisibilityGraph graph,
                                 List<RouteSegment> allSegments,
                                 List<TechnicalNode> allTechNodes,
                                 List<NewChamber> allChambers,
                                 List<UnconnectedOks> unconnected) {

        RouteTree tree = treeRouter.buildTree(group, uploadId, graph,
                obstacleModel);
        if (tree == null) {
            log.warn("Группа {} ОКС: дерево не построено — поштучная обработка",
                    group.size());
            fallbackToIndividual(group, uploadId, obstacleModel, graph,
                    allSegments, allTechNodes, allChambers, unconnected);
            return false;
        }

        String angleViolation = checkTreeAngles(tree, obstacleModel);
        if (angleViolation != null) {
            log.warn("Группа {} ОКС: {} — поштучная обработка",
                    group.size(), angleViolation);
            fallbackToIndividual(group, uploadId, obstacleModel, graph,
                    allSegments, allTechNodes, allChambers, unconnected);
            return false;
        }

        try {
            flowAggregator.aggregate(tree, group);
            treeDiameterAssigner.assign(tree);
        } catch (IllegalStateException e) {
            log.warn("Группа {} ОКС: не удалось назначить ДУ ({}) — "
                            + "поштучная обработка",
                    group.size(), e.getMessage());
            fallbackToIndividual(group, uploadId, obstacleModel, graph,
                    allSegments, allTechNodes, allChambers, unconnected);
            return false;
        }

        TreeRouteSegmentSplitter.SplitResult splitResult =
                treeRouteSegmentSplitter.split(tree, obstacleModel,
                        group.getSharedTieIn());

        allSegments.addAll(splitResult.getSegments());
        allTechNodes.addAll(splitResult.getTechnicalNodes());
        allChambers.addAll(splitResult.getNewChambers());
        return true;
    }

    private void fallbackToIndividual(OksGroup group, UUID uploadId,
                                      ObstacleModel obstacleModel,
                                      VisibilityGraph graph,
                                      List<RouteSegment> allSegments,
                                      List<TechnicalNode> allTechNodes,
                                      List<NewChamber> allChambers,
                                      List<UnconnectedOks> unconnected) {
        for (int i = 0; i < group.getPoints().size(); i++) {
            processOne(group.getPoints().get(i),
                    group.getCandidates().get(i),
                    uploadId, obstacleModel, graph,
                    allSegments, allTechNodes, allChambers, unconnected);
        }
    }

    private void processOne(OksConnectionPointEntity oks,
                            TieInCandidate candidate,
                            UUID uploadId,
                            ObstacleModel obstacleModel,
                            VisibilityGraph graph,
                            List<RouteSegment> allSegments,
                            List<TechnicalNode> allTechnicalNodes,
                            List<NewChamber> allNewChambers,
                            List<UnconnectedOks> unconnected) {

        if (!(oks.getGeometryUtm() instanceof Point)) {
            unconnected.add(new UnconnectedOks(oks.getFeatureId(),
                    UnconnectedOks.Reason.PATH_REJECTED_BY_VALIDATION,
                    "Геометрия точки подключения не является Point"));
            return;
        }
        Coordinate startUtm = ((Point) oks.getGeometryUtm()).getCoordinate();
        Coordinate endUtm = candidateCoordinateUtm(candidate);

        Optional<Long> oksPolygonId = oksResolver
                .resolvePolygonDatabaseId(uploadId, oks.getFeatureId());
        Set<Long> ignoredForbiddenIds = oksPolygonId
                .map(Collections::singleton)
                .orElseGet(Collections::emptySet);

        VisibilityGraph.PathResult pathResult = graph.shortestPath(
                startUtm, endUtm, ignoredForbiddenIds);
        if (!pathResult.isFound()) {
            unconnected.add(new UnconnectedOks(oks.getFeatureId(),
                    UnconnectedOks.Reason.NO_PATH_IN_GRAPH,
                    "Граф видимости несвязный между ОКС и точкой врезки "
                            + candidate.getId()));
            return;
        }

        List<Coordinate> simplifiedPath = routeSimplifier
                .simplify(pathResult.getPathUtm(), obstacleModel,
                        ignoredForbiddenIds);

        String angleViolation = checkCrossingAngles(
                simplifiedPath, obstacleModel);
        if (angleViolation != null) {
            unconnected.add(new UnconnectedOks(oks.getFeatureId(),
                    UnconnectedOks.Reason.PATH_REJECTED_BY_VALIDATION,
                    angleViolation));
            return;
        }

        BigDecimal flowTph = BigDecimal.valueOf(
                oks.getFlowTph() != null ? oks.getFlowTph() : 0.0);

        RouteNodeType endNodeType =
                candidate.getType() == TieInType.EXISTING_CHAMBER
                        ? RouteNodeType.EXISTING_CHAMBER
                        : RouteNodeType.NEW_CHAMBER;
        String endFeatureId =
                candidate.getType() == TieInType.EXISTING_CHAMBER
                        ? candidate.getExistingChamberId()
                        : null;

        RouteSegmentSplitter.SplitResult splitResult =
                routeSegmentSplitter.split(simplifiedPath, obstacleModel,
                        flowTph, oks.getFeatureId(),
                        endNodeType, endFeatureId);

        DiameterAssigner.AssignmentResult assignmentResult =
                diameterAssigner.assign(splitResult.getSegments(), flowTph);

        LengthValidator.ValidationResult validation =
                lengthValidator.validate(assignmentResult.getSegments());
        if (!validation.isValid()) {
            unconnected.add(new UnconnectedOks(oks.getFeatureId(),
                    UnconnectedOks.Reason.PATH_REJECTED_BY_VALIDATION,
                    validation.getViolationDetails()));
            return;
        }

        allSegments.addAll(assignmentResult.getSegments());
        allTechnicalNodes.addAll(splitResult.getTechnicalNodes());
        allTechnicalNodes.addAll(assignmentResult.getTechnicalNodes());

        if (candidate.getType() == TieInType.NEW_CHAMBER) {
            allNewChambers.add(new NewChamber(UUID.randomUUID(), endUtm,
                    candidate.getNewChamberDiameter() != null
                            ? candidate.getNewChamberDiameter() : 0,
                    null));
        }
    }

    private String checkTreeAngles(RouteTree tree,
                                    ObstacleModel obstacleModel) {
        for (TreeEdge edge : tree.getEdges()) {
            String violation = checkCrossingAngles(
                    edge.getGeometryUtm(), obstacleModel);
            if (violation != null) {
                return violation;
            }
        }
        return null;
    }

    private String checkCrossingAngles(List<Coordinate> pathUtm,
                                       ObstacleModel obstacleModel) {
        for (int i = 0; i < pathUtm.size() - 1; i++) {
            Coordinate a = pathUtm.get(i);
            Coordinate b = pathUtm.get(i + 1);
            LineString segment = GEOMETRY_FACTORY.createLineString(
                    new Coordinate[]{a, b});
            segment.setSRID(32637);

            for (ObstacleModel.SpecialZone zone :
                    obstacleModel.getSpecialZones()) {
                if (zone.getMinCrossingAngleDeg() == null) continue;
                if (!segment.intersects(zone.getSourceGeometryUtm())) continue;
                boolean ok = angleChecker.isCrossingAngleValid(
                        a, b, zone.getSourceGeometryUtm(),
                        zone.getMinCrossingAngleDeg());
                if (!ok) {
                    return String.format(
                            "Нарушен угол пересечения с %s: сегмент %d "
                                    + "(%.0f,%.0f)-(%.0f,%.0f), требуется ≥ %.0f°",
                            zone.getRestrictionType(), i,
                            a.x, a.y, b.x, b.y,
                            zone.getMinCrossingAngleDeg());
                }
            }
        }
        return null;
    }

    private Coordinate candidateCoordinateUtm(TieInCandidate candidate) {
        Point targetWgs84 = GEOMETRY_FACTORY.createPoint(
                new Coordinate(candidate.getTargetLongitude(),
                        candidate.getTargetLatitude()));
        targetWgs84.setSRID(WGS84_SRID);
        Geometry targetUtm = coordinateTransformService.toUtm(targetWgs84);
        return targetUtm.getCoordinate();
    }
}
