package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.moscow.heat.geojson.entity.HeatChamberEntity;
import ru.moscow.heat.geojson.entity.HeatNetworkEntity;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.repository.HeatChamberRepository;
import ru.moscow.heat.geojson.repository.HeatNetworkRepository;
import ru.moscow.heat.geojson.repository.OksConnectionPointRepository;
import ru.moscow.heat.spatial.ChamberCostTable;
import ru.moscow.heat.spatial.DiameterSpec;
import ru.moscow.heat.spatial.DiameterTable;
import ru.moscow.heat.spatial.GeometryUtils;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TieInType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Сервис подбора кандидатов на присоединение новой сети
 * к существующей для точек подключения ОКС
 * <p>Для каждой точки подключения {@code oks_connection_point}:
 * <ol>
 *   <li>Находит ближайший участок {@code heat_network}.</li>
 *   <li>Определяет точку присоединения на сети.</li>
 *   <li>Ищет существующие {@code heat_chamber} в радиусе 10 м.</li>
 *   <li>Для каждой камеры проверяет число примыканий
 *       ({@code <= MAX_ATTACHMENTS - 1}) и формирует кандидата
 *       {@link TieInType#EXISTING_CHAMBER}.</li>
 *   <li>Если подходящих камер нет — формирует кандидата
 *       {@link TieInType#NEW_CHAMBER} с ДУ и стоимостью
 *       по таблице 3.2 ТП.</li>
 * </ol>
 * <p>Возвращаемый список отсортирован по приоритету: сначала
 * существующие камеры (по возрастанию расстояния, затем по числу
 * примыканий), затем новая камера. Первый элемент — рекомендуемый
 * кандидат
 * <p>Координаты кандидата разделены на два поля:
 * {@code tieInXxx} — точка на существующей сети (для отладки),
 * {@code targetXxx} — фактическая конечная точка маршрута:
 * камера для {@link TieInType#EXISTING_CHAMBER} и точка
 * присоединения на сети для {@link TieInType#NEW_CHAMBER}
 */
@Service
@RequiredArgsConstructor
public class TieInCandidateService {

    /** Радиус поиска существующей камеры, м (разъяснения п. 11) */
    private static final double CHAMBER_SEARCH_RADIUS_M = 10.0;

    /** Допуск совпадения концов участков с точкой камеры, м */
    private static final double ATTACHMENT_TOLERANCE_M = 1.0;

    /** Максимальное число примыканий к камере (раздел 2.4 ТП) */
    private static final int MAX_ATTACHMENTS = 4;

    /** Стоимость одной врезки в существующую камеру, руб */
    private static final long EXISTING_CHAMBER_TIE_IN_COST = 5_000_000L;

    private final OksConnectionPointRepository oksRepo;
    private final HeatNetworkRepository heatNetworkRepo;
    private final HeatChamberRepository heatChamberRepo;
    private final GeometryUtils geometryUtils;
    private final DiameterTable diameterTable;
    private final ChamberCostTable chamberCostTable;

    /**
     * Подбирает список кандидатов на присоединение для одной точки
     * подключения ОКС
     * @param uploadId          идентификатор сессии загрузки
     * @param connectionPointId идентификатор {@code oks_connection_point}
     * @return список кандидатов, отсортированных по приоритету;
     *         пустой список, если участок сети не найден
     * @throws IllegalArgumentException если точка подключения
     *                                  не найдена в загрузке
     */
    @Transactional(readOnly = true)
    public List<TieInCandidate> findCandidates(
            UUID uploadId, String connectionPointId) {

        OksConnectionPointEntity point = oksRepo
                .findByUploadIdAndFeatureId(uploadId, connectionPointId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Точка подключения не найдена: "
                                + connectionPointId));

        if (!(point.getGeometry() instanceof Point)) {
            return Collections.emptyList();
        }
        Point pointWgs = (Point) point.getGeometry();
        double flowTph = point.getFlowTph() != null
                ? point.getFlowTph() : 0.0;

        List<HeatNetworkEntity> nearest = heatNetworkRepo
                .findNearest(uploadId, pointWgs, 1);
        if (nearest.isEmpty()) {
            return Collections.emptyList();
        }
        HeatNetworkEntity network = nearest.get(0);

        Point tieInPoint = geometryUtils.nearestPointOnGeometry(
                network.getGeometry(), pointWgs);
        double distanceToNetwork = geometryUtils.distanceMeters(
                pointWgs, tieInPoint);

        List<TieInCandidate> candidates = new ArrayList<>();

        List<HeatChamberEntity> chambers = heatChamberRepo
                .findWithinDistance(
                        uploadId, tieInPoint, CHAMBER_SEARCH_RADIUS_M);

        for (HeatChamberEntity chamber : chambers) {
            if (!(chamber.getGeometry() instanceof Point)) {
                continue;
            }
            Point chamberPoint = (Point) chamber.getGeometry();
            int attachments = heatNetworkRepo.countConnectionsToChamber(
                    uploadId, chamberPoint, ATTACHMENT_TOLERANCE_M);
            if (attachments >= MAX_ATTACHMENTS) {
                continue;
            }
            double distanceToChamber = geometryUtils.distanceMeters(
                    tieInPoint, chamberPoint);
            candidates.add(buildExistingChamberCandidate(
                    point, network, chamber, tieInPoint,
                    distanceToNetwork, distanceToChamber, attachments));
        }

        if (candidates.isEmpty()) {
            candidates.add(buildNewChamberCandidate(
                    point, network, tieInPoint,
                    distanceToNetwork, flowTph));
        }

        Comparator<TieInCandidate> byPriority = Comparator
                .comparingInt((TieInCandidate c) ->
                        c.getType() == TieInType.EXISTING_CHAMBER
                                ? 0 : 1)
                .thenComparingDouble(
                        TieInCandidate::getDistanceToChamberM)
                .thenComparingInt(
                        TieInCandidate::getCurrentAttachments);
        candidates.sort(byPriority);

        return candidates;
    }

    /**
     * Подбирает кандидатов на присоединение для всех точек
     * подключения ОКС указанной загрузки
     * @param uploadId идентификатор сессии загрузки
     * @return отображение {@code feature_id} точки подключения в
     *         список кандидатов
     */
    @Transactional(readOnly = true)
    public Map<String, List<TieInCandidate>> findCandidatesForAllPoints(
            UUID uploadId) {

        List<OksConnectionPointEntity> points =
                oksRepo.findByUploadId(uploadId);
        Map<String, List<TieInCandidate>> result = new LinkedHashMap<>();
        for (OksConnectionPointEntity point : points) {
            result.put(
                    point.getFeatureId(),
                    findCandidates(uploadId, point.getFeatureId()));
        }
        return result;
    }

    /**
     * Формирует кандидата на врезку в существующую камеру.
     * {@code targetXxx} = координаты камеры: по ТП маршрут
     * заканчивается именно в ней, а не в точке на сети
     */
    private TieInCandidate buildExistingChamberCandidate(
            OksConnectionPointEntity point,
            HeatNetworkEntity network,
            HeatChamberEntity chamber,
            Point tieInPoint,
            double distanceToNetwork,
            double distanceToChamber,
            int attachments) {

        Point chamberPoint = (Point) chamber.getGeometry();
        String id = "cand_" + point.getFeatureId()
                + "_ch_" + chamber.getFeatureId();
        return TieInCandidate.builder()
                .id(id)
                .connectionPointId(point.getFeatureId())
                .heatNetworkId(network.getFeatureId())
                .type(TieInType.EXISTING_CHAMBER)
                .existingChamberId(chamber.getFeatureId())
                .tieInLongitude(tieInPoint.getX())
                .tieInLatitude(tieInPoint.getY())
                .targetLongitude(chamberPoint.getX())
                .targetLatitude(chamberPoint.getY())
                .distanceToNetworkM(distanceToNetwork)
                .distanceToChamberM(distanceToChamber)
                .currentAttachments(attachments)
                .cost(EXISTING_CHAMBER_TIE_IN_COST)
                .newChamberDiameter(null)
                .build();
    }

    /**
     * Формирует кандидата на строительство новой камеры в точке
     * присоединения на сети. {@code targetXxx} = {@code tieInPoint}:
     * камера создаётся именно там
     */
    private TieInCandidate buildNewChamberCandidate(
            OksConnectionPointEntity point,
            HeatNetworkEntity network,
            Point tieInPoint,
            double distanceToNetwork,
            double flowTph) {

        int existingDiameter = network.getDiameter() != null
                ? network.getDiameter() : 0;
        DiameterSpec newDiameterSpec =
                diameterTable.minDiameterForFlow(flowTph);
        int chamberDiameter = Math.max(
                existingDiameter,
                newDiameterSpec.getDiameterMm());
        long cost = chamberCostTable.costForDiameter(chamberDiameter);

        String id = "cand_" + point.getFeatureId() + "_new";
        return TieInCandidate.builder()
                .id(id)
                .connectionPointId(point.getFeatureId())
                .heatNetworkId(network.getFeatureId())
                .type(TieInType.NEW_CHAMBER)
                .existingChamberId(null)
                .tieInLongitude(tieInPoint.getX())
                .tieInLatitude(tieInPoint.getY())
                .targetLongitude(tieInPoint.getX())
                .targetLatitude(tieInPoint.getY())
                .distanceToNetworkM(distanceToNetwork)
                .distanceToChamberM(0.0)
                .currentAttachments(0)
                .cost(cost)
                .newChamberDiameter(chamberDiameter)
                .build();
    }
}
