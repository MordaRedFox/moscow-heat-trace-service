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
import ru.moscow.heat.trace.model.RouteNodeType;
import ru.moscow.heat.trace.model.RouteSegment;
import ru.moscow.heat.trace.model.TechnicalNode;
import ru.moscow.heat.trace.model.TraceResult;
import ru.moscow.heat.trace.model.UnconnectedOks;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Главный сценарий трассировки одной загрузки (план, шаги 1-10)
 * <p>Работает в UTM целиком. Единственная трансформация координат —
 * перевод {@code TieInCandidate.targetXxx} (WGS84) в UTM для {@code endUtm}
 * <p>Обход полигона своего ОКС реализуется через параметр
 * {@code ignoredForbiddenIds} в {@link VisibilityGraph#shortestPath} и
 * {@link RouteSimplifier#simplify}: для каждой точки ОКС находится id её
 * полигона через {@link OksConnectionPointResolver}, и этот буфер
 * игнорируется при проверке видимости от стартовой точки. Так
 * обеспечивается правило ТП «финальный прямой участок от границы полигона
 * до точки подключения не проверяется на отступ к этому полигону»
 * <p>Проверка углов пересечения с road/tram_tracks выполняется после
 * упрощения пути через {@link AngleChecker}: для каждого сегмента
 * проверяются все спецзоны, требующие угла, и при нарушении ОКС
 * отправляется в {@link UnconnectedOks#PATH_REJECTED_BY_VALIDATION}.
 * <p>Конвейер на один ОКС:
 * <ol>
 *     <li>{@link TieInCandidateService#findCandidates} → берём первого;</li>
 *     <li>найти id своего ОКС-полигона через resolver;</li>
 *     <li>{@link VisibilityGraph#shortestPath} с ignore-set;</li>
 *     <li>{@link RouteSimplifier#simplify} с ignore-set;</li>
 *     <li>{@link AngleChecker} — проверка углов в спецзонах;</li>
 *     <li>{@link RouteSegmentSplitter#split} — сохранение топологии;</li>
 *     <li>{@link DiameterAssigner#assign} — назначение ДУ;</li>
 *     <li>{@link LengthValidator#validate} — защитная проверка.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceOrchestrator {

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

    /**
     * Выполняет расчет трассировки для загрузки
     * @param uploadId id загрузки
     * @return {@link TraceResult}
     */
    public TraceResult run(UUID uploadId) {
        List<OksConnectionPointEntity> oksPoints = oksRepository.findByUploadId(uploadId);
        log.info("Трассировка uploadId={}: {} перспективных ОКС",
                uploadId, oksPoints.size());

        ObstacleModel obstacleModel = obstacleModelBuilder.build(uploadId);

        VisibilityGraph graph = new VisibilityGraph(obstacleModel);
        graph.build();

        List<RouteSegment> allSegments = new ArrayList<>();
        List<TechnicalNode> allTechnicalNodes = new ArrayList<>();
        List<NewChamber> allNewChambers = new ArrayList<>();
        List<UnconnectedOks> unconnected = new ArrayList<>();

        for (OksConnectionPointEntity oks : oksPoints) {
            processOne(oks, uploadId, obstacleModel, graph,
                    allSegments, allTechnicalNodes, allNewChambers, unconnected);
        }

        TraceResult.SummaryCounters counters = new TraceResult.SummaryCounters(
                oksPoints.size(),
                oksPoints.size() - unconnected.size(),
                unconnected.size());

        return new TraceResult(allSegments, allNewChambers, allTechnicalNodes,
                unconnected, counters);
    }

    private void processOne(OksConnectionPointEntity oks, UUID uploadId,
                             ObstacleModel obstacleModel, VisibilityGraph graph,
                             List<RouteSegment> allSegments,
                             List<TechnicalNode> allTechnicalNodes,
                             List<NewChamber> allNewChambers,
                             List<UnconnectedOks> unconnected) {

        // Шаг 2. Кандидат врезки — MVP берёт первого (наивысший приоритет)
        List<TieInCandidate> candidates = tieInCandidateService
                .findCandidates(uploadId, oks.getFeatureId());
        if (candidates.isEmpty()) {
            unconnected.add(new UnconnectedOks(oks.getFeatureId(),
                    UnconnectedOks.Reason.NO_TIE_IN_CANDIDATE,
                    "TieInCandidateService не вернул ни одного кандидата"));
            return;
        }
        TieInCandidate candidate = candidates.get(0);

        if (!(oks.getGeometryUtm() instanceof Point)) {
            unconnected.add(new UnconnectedOks(oks.getFeatureId(),
                    UnconnectedOks.Reason.PATH_REJECTED_BY_VALIDATION,
                    "Геометрия точки подключения не является Point"));
            return;
        }
        Coordinate startUtm = ((Point) oks.getGeometryUtm()).getCoordinate();
        Coordinate endUtm = candidateCoordinateUtm(candidate);

        // Шаг 2б. Найти id своего ОКС-полигона и подготовить ignore-set
        Optional<Long> oksPolygonId = oksResolver
                .resolvePolygonDatabaseId(uploadId, oks.getFeatureId());
        Set<Long> ignoredForbiddenIds = oksPolygonId
                .map(Collections::singleton)
                .orElseGet(Collections::emptySet);

        if (oksPolygonId.isEmpty()) {
            log.debug("Точка ОКС {} не связана с полигоном — трассировка "
                            + "без исключений из FORBIDDEN-зон",
                    oks.getFeatureId());
        }

        // Шаг 5. Поиск пути A* по графу видимости
        VisibilityGraph.PathResult pathResult = graph.shortestPath(
                startUtm, endUtm, ignoredForbiddenIds);
        if (!pathResult.isFound()) {
            unconnected.add(new UnconnectedOks(oks.getFeatureId(),
                    UnconnectedOks.Reason.NO_PATH_IN_GRAPH,
                    "Граф видимости несвязный между ОКС и точкой врезки "
                            + candidate.getId()));
            return;
        }

        // Шаг 6. Упростить путь (убрать зигзаги)
        List<Coordinate> simplifiedPath = routeSimplifier
                .simplify(pathResult.getPathUtm(), obstacleModel, ignoredForbiddenIds);

        // Шаг 6б. Проверка углов пересечения road/tram_tracks
        String angleViolation = checkCrossingAngles(simplifiedPath, obstacleModel);
        if (angleViolation != null) {
            unconnected.add(new UnconnectedOks(oks.getFeatureId(),
                    UnconnectedOks.Reason.PATH_REJECTED_BY_VALIDATION,
                    angleViolation));
            return;
        }

        BigDecimal flowTph = BigDecimal.valueOf(
                oks.getFlowTph() != null ? oks.getFlowTph() : 0.0);

        // Шаг 7. Разбить по границам спецзон, сохраняя топологию RouteNode
        RouteNodeType endNodeType = candidate.getType() == TieInType.EXISTING_CHAMBER
                ? RouteNodeType.EXISTING_CHAMBER
                : RouteNodeType.NEW_CHAMBER;
        String endFeatureId = candidate.getType() == TieInType.EXISTING_CHAMBER
                ? candidate.getExistingChamberId()
                : null;

        RouteSegmentSplitter.SplitResult splitResult = routeSegmentSplitter
                .split(simplifiedPath, obstacleModel, flowTph,
                        oks.getFeatureId(), endNodeType, endFeatureId);

        // Шаг 8. Назначить ДУ
        DiameterAssigner.AssignmentResult assignmentResult =
                diameterAssigner.assign(splitResult.getSegments(), flowTph);

        // Шаг 9. Защитная проверка предельной длины
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

        // Новая камера - только если кандидат требует ее строительства
        if (candidate.getType() == TieInType.NEW_CHAMBER) {
            allNewChambers.add(new NewChamber(UUID.randomUUID(), endUtm,
                    candidate.getNewChamberDiameter() != null
                            ? candidate.getNewChamberDiameter() : 0,
                    null));
        }
    }

    /**
     * Проверяет углы пересечения пути со всеми спецзонами, для которых
     * задан {@code minCrossingAngleDeg}. Возвращает описание первого
     * нарушения или {@code null}, если все в порядке
     */
    private String checkCrossingAngles(List<Coordinate> pathUtm,
                                        ObstacleModel obstacleModel) {
        for (int i = 0; i < pathUtm.size() - 1; i++) {
            Coordinate a = pathUtm.get(i);
            Coordinate b = pathUtm.get(i + 1);
            LineString segment = GEOMETRY_FACTORY.createLineString(
                    new Coordinate[]{a, b});
            segment.setSRID(32637);

            for (ObstacleModel.SpecialZone zone : obstacleModel.getSpecialZones()) {
                if (zone.getMinCrossingAngleDeg() == null) {
                    continue;
                }
                if (!segment.intersects(zone.getSourceGeometryUtm())) {
                    continue;
                }
                boolean ok = angleChecker.isCrossingAngleValid(
                        a, b,
                        zone.getSourceGeometryUtm(),
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

    /**
     * Единственное место конвертации координат: {@link TieInCandidate}
     * хранит {@code targetXxx} в WGS84. Для {@link TieInType#EXISTING_CHAMBER}
     * это координата камеры, для {@link TieInType#NEW_CHAMBER} — точка
     * на сети
     */
    private Coordinate candidateCoordinateUtm(TieInCandidate candidate) {
        Point targetWgs84 = GEOMETRY_FACTORY.createPoint(
                new Coordinate(candidate.getTargetLongitude(),
                        candidate.getTargetLatitude()));
        targetWgs84.setSRID(WGS84_SRID);
        Geometry targetUtm = coordinateTransformService.toUtm(targetWgs84);
        return targetUtm.getCoordinate();
    }
}
