package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.repository.OksConnectionPointRepository;
import ru.moscow.heat.geojson.service.CoordinateTransformService;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TieInType;
import ru.moscow.heat.trace.graph.ObstacleModel;
import ru.moscow.heat.trace.graph.ObstacleModelBuilder;
import ru.moscow.heat.trace.graph.VisibilityGraph;
import ru.moscow.heat.trace.model.NewChamber;
import ru.moscow.heat.trace.model.RouteSegment;
import ru.moscow.heat.trace.model.TechnicalNode;
import ru.moscow.heat.trace.model.TraceResult;
import ru.moscow.heat.trace.model.UnconnectedOks;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Главный сценарий трассировки одной загрузки (план, шаги 1-10).
 * <p>
 * Работает в UTM целиком (см. javadoc {@code ObstacleModel}/{@code VisibilityGraph}/
 * {@code RouteNode}): координата ОКС берётся готовой из
 * {@code OksConnectionPointEntity.getGeometryUtm()}, а координата точки
 * врезки — единственное место, где нужна трансформация координат:
 * {@link TieInCandidate} хранит только WGS84 (tieInLongitude/tieInLatitude),
 * поэтому конвертируем именно её через {@code CoordinateTransformService.toUtm()}.
 * <p>
 * Конвейер на один ОКС (шаги 2-9 плана):
 * <ol>
 *     <li>{@code TieInCandidateService.findCandidates} → берём первого (MVP);</li>
 *     <li>{@code VisibilityGraph.shortestPath} → сырой путь A*;</li>
 *     <li>{@code RouteSimplifier.simplify} → убираем зигзаги;</li>
 *     <li>{@code RouteSegmentSplitter.split} → режем по границам спецзон,
 *     diameterMm пока 0;</li>
 *     <li>{@code DiameterAssigner.assign} → назначаем ДУ, вставляем
 *     технические узлы смены ДУ;</li>
 *     <li>{@code LengthValidator.validate} → защитная проверка (не должна
 *     падать, если DiameterAssigner отработал корректно).</li>
 * </ol>
 * <p>
 * ГРАФ и ObstacleModel строятся один раз на всю загрузку (шаги 3-4) и
 * переиспользуются для каждого ОКС.
 * <p>
 * TODO (не в этой итерации): объединение нескольких ОКС в общую сеть
 * (риск R4 плана) — сейчас каждый ОКС трассируется независимо, поэтому
 * общие участки не выявляются и не переиспользуются.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceOrchestrator {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final int WGS84_SRID = 4326;

    private final OksConnectionPointRepository oksRepository;
    private final TieInCandidateService tieInCandidateService;
    private final ObstacleModelBuilder obstacleModelBuilder;
    private final CoordinateTransformService coordinateTransformService;
    private final RouteSimplifier routeSimplifier;
    private final RouteSegmentSplitter routeSegmentSplitter;
    private final DiameterAssigner diameterAssigner;
    private final LengthValidator lengthValidator;

    /**
     * Выполняет расчёт трассировки для загрузки.
     *
     * @param uploadId id загрузки
     * @return {@link TraceResult} — сегменты, новые камеры, технические
     * узлы, неподключённые ОКС, счётчики
     */
    public TraceResult run(UUID uploadId) {
        List<OksConnectionPointEntity> oksPoints = oksRepository.findByUploadId(uploadId);
        log.info("Трассировка uploadId={}: {} перспективных ОКС", uploadId, oksPoints.size());

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
                oksPoints.size(), oksPoints.size() - unconnected.size(), unconnected.size());

        return new TraceResult(allSegments, allNewChambers, allTechnicalNodes, unconnected, counters);
    }

    private void processOne(OksConnectionPointEntity oks, UUID uploadId, ObstacleModel obstacleModel,
                             VisibilityGraph graph, List<RouteSegment> allSegments,
                             List<TechnicalNode> allTechnicalNodes, List<NewChamber> allNewChambers,
                             List<UnconnectedOks> unconnected) {

        // Шаг 2. Кандидат врезки — MVP берёт первого (наивысший приоритет).
        List<TieInCandidate> candidates = tieInCandidateService.findCandidates(uploadId, oks.getFeatureId());
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
        Coordinate endUtm = tieInCoordinateUtm(candidate);

        // Шаг 5. Поиск пути A* по графу видимости.
        VisibilityGraph.PathResult pathResult = graph.shortestPath(startUtm, endUtm);
        if (!pathResult.isFound()) {
            unconnected.add(new UnconnectedOks(oks.getFeatureId(),
                    UnconnectedOks.Reason.NO_PATH_IN_GRAPH,
                    "Граф видимости несвязный между ОКС и точкой врезки " + candidate.getId()));
            return;
        }

        // Шаг 6. Упростить путь (убрать зигзаги).
        List<Coordinate> simplifiedPath = routeSimplifier.simplify(pathResult.getPathUtm(), obstacleModel);

        BigDecimal flowTph = BigDecimal.valueOf(oks.getFlowTph() != null ? oks.getFlowTph() : 0.0);

        // Шаг 7. Разбить по границам спецзон (diameterMm пока 0).
        RouteSegmentSplitter.SplitResult splitResult =
                routeSegmentSplitter.split(simplifiedPath, obstacleModel, flowTph, oks.getFeatureId());

        // Шаг 8. Назначить ДУ, получить технические узлы смены ДУ.
        DiameterAssigner.AssignmentResult assignmentResult =
                diameterAssigner.assign(splitResult.getSegments(), flowTph);

        // Шаг 9. Защитная проверка предельной длины.
        LengthValidator.ValidationResult validation = lengthValidator.validate(assignmentResult.getSegments());
        if (!validation.isValid()) {
            unconnected.add(new UnconnectedOks(oks.getFeatureId(),
                    UnconnectedOks.Reason.PATH_REJECTED_BY_VALIDATION,
                    validation.getViolationDetails()));
            return;
        }

        allSegments.addAll(assignmentResult.getSegments());
        allTechnicalNodes.addAll(splitResult.getTechnicalNodes());
        allTechnicalNodes.addAll(assignmentResult.getTechnicalNodes());

        // Новая камера — только если кандидат врезки требует её строительства.
        if (candidate.getType() == TieInType.NEW_CHAMBER) {
            allNewChambers.add(new NewChamber(UUID.randomUUID(), endUtm,
                    candidate.getNewChamberDiameter() != null ? candidate.getNewChamberDiameter() : 0,
                    null));
        }
    }

    /**
     * Единственное место конвертации координат во всём оркестраторе:
     * {@link TieInCandidate} хранит точку врезки только в WGS84
     * ({@code tieInLongitude}/{@code tieInLatitude}). Переводим в UTM
     * через {@code CoordinateTransformService}, чтобы дальше (граф
     * видимости, сегменты) всё было в одной метрической плоскости.
     */
    private Coordinate tieInCoordinateUtm(TieInCandidate candidate) {
        Point tieInWgs84 = GEOMETRY_FACTORY.createPoint(
                new Coordinate(candidate.getTieInLongitude(), candidate.getTieInLatitude()));
        tieInWgs84.setSRID(WGS84_SRID);
        Geometry tieInUtm = coordinateTransformService.toUtm(tieInWgs84);
        return tieInUtm.getCoordinate();
    }
}
