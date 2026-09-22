package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.moscow.heat.geojson.entity.HeatChamberEntity;
import ru.moscow.heat.geojson.entity.HeatNetworkEntity;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.repository.HeatChamberRepository;
import ru.moscow.heat.geojson.repository.HeatNetworkRepository;
import ru.moscow.heat.geojson.repository.OksConnectionPointRepository;
import ru.moscow.heat.spatial.DiameterSpec;
import ru.moscow.heat.spatial.DiameterTable;
import ru.moscow.heat.spatial.GeometryUtils;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TieInType;

import java.util.*;

/**
 * Сервис поиска и выбора кандидатов на присоединение (tie-in candidates)
 * перспективных объектов капитального строительства (ОКС) к существующей тепловой сети.
 * <p>
 * Логика сервиса строго соответствует разделу 3.2 Технического приложения ЛЦТ-2026:
 * <ol>
 *   <li>Для точки подключения находится ближайшая точка на существующей сети (потенциальная точка присоединения).</li>
 *   <li>Выполняется поиск существующих камер в радиусе {@value #CHAMBER_SEARCH_RADIUS_METERS} м от точки присоединения.</li>
 *   <li>Проверяется допустимость каждой камеры: число примыканий после подключения не должно превышать {@value #MAX_CHAMBER_CONNECTIONS}.</li>
 *   <li>Если подходящие камеры найдены, формируются кандидаты типа {@link TieInType#EXISTING_CHAMBER} со стоимостью {@value #EXISTING_CHAMBER_TIE_IN_COST} руб.</li>
 *   <li>Если подходящей камеры нет, формируется кандидат типа {@link TieInType#NEW_CHAMBER} со стоимостью по нормативной шкале от наибольшего ДУ примыкающих участков.</li>
 *   <li>Выбор лучшего кандидата производится детерминированным компаратором по правилу:
 *       минимальная стоимость &rarr; минимальное расстояние до камеры &rarr; большее число свободных примыканий.</li>
 * </ol>
 * Состояние сервиса не персистится в БД (in-memory расчет).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TieInCandidateService {

    /** Стоимость одной врезки в существующую тепловую камеру в рублях (раздел 3.2 ТП) */
    public static final double EXISTING_CHAMBER_TIE_IN_COST = 5_000_000.0;

    /** Радиус поиска существующей камеры от точки присоединения в метрах (раздел 3.2 ТП) */
    public static final double CHAMBER_SEARCH_RADIUS_METERS = 10.0;

    /** Максимальное суммарное число примыканий к одной камере после подключения (раздел 3.2 ТП) */
    public static final int MAX_CHAMBER_CONNECTIONS = 4;

    /** Допуск совпадения конца линейного участка сети с точкой камеры в метрах (раздел 3.2 ТП) */
    public static final double ENDPOINT_MATCH_TOLERANCE_METERS = 1.0;

    /**
     * Компаратор выбора лучшего кандидата на присоединение согласно правилу спецификации:
     * 1) Минимальная стоимость (cost)
     * 2) Минимальное расстояние до камеры (distanceToChamberM)
     * 3) Большее число свободных примыканий (4 - currentChamberConnections)
     * 4) Детерминированный tie-breaker по existingChamberId
     */
    public static final Comparator<TieInCandidate> BEST_CANDIDATE_COMPARATOR = (c1, c2) -> {
        // 1. Минимальная стоимость
        int costCmp = Double.compare(c1.getCost(), c2.getCost());
        if (costCmp != 0) {
            return costCmp;
        }

        // 2. Минимальное расстояние до камеры
        int distCmp = Double.compare(c1.getDistanceToChamberM(), c2.getDistanceToChamberM());
        if (distCmp != 0) {
            return distCmp;
        }

        // 3. Большее число свободных примыканий (4 - currentChamberConnections)
        int free1 = c1.getCurrentChamberConnections() != null
                ? (MAX_CHAMBER_CONNECTIONS - c1.getCurrentChamberConnections())
                : MAX_CHAMBER_CONNECTIONS;
        int free2 = c2.getCurrentChamberConnections() != null
                ? (MAX_CHAMBER_CONNECTIONS - c2.getCurrentChamberConnections())
                : MAX_CHAMBER_CONNECTIONS;
        int freeCmp = Integer.compare(free2, free1); // по убыванию свободных мест
        if (freeCmp != 0) {
            return freeCmp;
        }

        // Детерминированный tie-breaker
        String id1 = c1.getExistingChamberId() != null ? c1.getExistingChamberId() : "";
        String id2 = c2.getExistingChamberId() != null ? c2.getExistingChamberId() : "";
        return id1.compareTo(id2);
    };

    private final HeatNetworkRepository heatNetworkRepository;
    private final HeatChamberRepository heatChamberRepository;
    private final OksConnectionPointRepository oksConnectionPointRepository;
    private final DiameterTable diameterTable;
    private final GeometryUtils geometryUtils;

    /**
     * Вычисляет стоимость строительства новой тепловой камеры в рублях
     * по нормативной шкале от условного диаметра (раздел 3.2 ТП ЛЦТ-2026).
     *
     * @param diameterMm условный диаметр камеры в мм (наибольший ДУ примыкающих участков)
     * @return стоимость новой камеры в рублях
     * @throws IllegalArgumentException если диаметр выходит за пределы нормативной таблицы (50..1400 мм)
     */
    public static double calculateNewChamberCost(int diameterMm) {
        if (diameterMm >= 50 && diameterMm <= 200) {
            return 3_000_000.0;
        } else if (diameterMm >= 250 && diameterMm <= 500) {
            return 5_000_000.0;
        } else if (diameterMm >= 600 && diameterMm <= 1000) {
            return 8_000_000.0;
        } else if (diameterMm >= 1200 && diameterMm <= 1400) {
            return 12_000_000.0;
        } else {
            throw new IllegalArgumentException(
                    "Недопустимый условный диаметр камеры: " + diameterMm
                            + " мм. Допустимый диапазон: 50..1400 мм (по таблице 3.2 ТП)");
        }
    }

    /**
     * Находит всех кандидатов на присоединение для заданной точки подключения ОКС.
     * Кандидаты сортируются по приоритету (лучший кандидат первый).
     *
     * @param uploadId идентификатор сессии загрузки
     * @param point    сущность точки подключения ОКС
     * @return отсортированный список кандидатов на присоединение (пустой список, если сеть отсутствует)
     */
    @Transactional(readOnly = true)
    public List<TieInCandidate> findCandidatesForPoint(UUID uploadId, OksConnectionPointEntity point) {
        if (uploadId == null || point == null || point.getGeometry() == null) {
            return Collections.emptyList();
        }

        Point connectionPoint = (Point) point.getGeometry();

        // 1. Поиск ближайшего участка сети и проекции точки на него (потенциальная точка присоединения)
        List<HeatNetworkEntity> candidateNetworks = heatNetworkRepository.findWithinDistance(
                uploadId, connectionPoint, 1000.0);
        if (candidateNetworks.isEmpty()) {
            candidateNetworks = heatNetworkRepository.findNearest(uploadId, connectionPoint, 10);
        }
        if (candidateNetworks.isEmpty()) {
            candidateNetworks = heatNetworkRepository.findByUploadId(uploadId);
        }
        if (candidateNetworks.isEmpty()) {
            log.warn("В загрузке [{}] отсутствуют участки тепловой сети для подключения точки [{}]",
                    uploadId, point.getFeatureId());
            return Collections.emptyList();
        }

        HeatNetworkEntity nearestNetwork = null;
        Point nearestPointOnNetwork = null;
        double minDistanceToNetwork = Double.MAX_VALUE;

        for (HeatNetworkEntity network : candidateNetworks) {
            if (network.getGeometry() == null || network.getGeometry().isEmpty()) {
                continue;
            }
            Point projPoint = geometryUtils.nearestPointOnGeometry(network.getGeometry(), connectionPoint);
            double dist = geometryUtils.distanceMeters(connectionPoint, projPoint);
            if (dist < minDistanceToNetwork) {
                minDistanceToNetwork = dist;
                nearestNetwork = network;
                nearestPointOnNetwork = projPoint;
            }
        }

        if (nearestNetwork == null || nearestPointOnNetwork == null) {
            log.warn("Не удалось найти проекцию точки подключения [{}] на существующую сеть",
                    point.getFeatureId());
            return Collections.emptyList();
        }

        // 2. Поиск существующих камер в радиусе 10 м от точки присоединения на сети
        List<HeatChamberEntity> chambers = heatChamberRepository.findWithinDistance(
                uploadId, nearestPointOnNetwork, CHAMBER_SEARCH_RADIUS_METERS);

        List<TieInCandidate> candidates = new ArrayList<>();

        // 3. Фильтрация подходящих камер по лимиту примыканий (текущие + 1 <= 4)
        for (HeatChamberEntity chamber : chambers) {
            if (chamber.getGeometry() == null || chamber.getGeometry().isEmpty()) {
                continue;
            }
            Point chamberPoint = (Point) chamber.getGeometry();
            int currentConnections = heatNetworkRepository.countConnectionsToChamber(
                    uploadId, chamberPoint, ENDPOINT_MATCH_TOLERANCE_METERS);

            if (currentConnections + 1 <= MAX_CHAMBER_CONNECTIONS) {
                double distanceToChamber = geometryUtils.distanceMeters(nearestPointOnNetwork, chamberPoint);
                candidates.add(TieInCandidate.builder()
                        .connectionPointId(point.getFeatureId())
                        .heatNetworkId(nearestNetwork.getFeatureId())
                        .tieInType(TieInType.EXISTING_CHAMBER)
                        .existingChamberId(chamber.getFeatureId())
                        .tieInPoint(chamberPoint)
                        .distanceToChamberM(distanceToChamber)
                        .currentChamberConnections(currentConnections)
                        .cost(EXISTING_CHAMBER_TIE_IN_COST)
                        .requiredChamberDiameter(null)
                        .build());
            }
        }

        // 4. Если подходящих существующих камер нет — создается новая камера в точке присоединения
        if (candidates.isEmpty()) {
            Set<Integer> adjoiningDiameters = new HashSet<>();
            if (nearestNetwork.getDiameter() != null && nearestNetwork.getDiameter() > 0) {
                adjoiningDiameters.add(nearestNetwork.getDiameter());
            }

            // Дополнительно учитываем участки сети, примыкающие к точке врезки в пределах допуска
            List<HeatNetworkEntity> adjoiningNetworks = heatNetworkRepository.findWithinDistance(
                    uploadId, nearestPointOnNetwork, ENDPOINT_MATCH_TOLERANCE_METERS);
            for (HeatNetworkEntity adj : adjoiningNetworks) {
                if (adj.getDiameter() != null && adj.getDiameter() > 0) {
                    adjoiningDiameters.add(adj.getDiameter());
                }
            }

            if (adjoiningDiameters.isEmpty()) {
                adjoiningDiameters.add(50); // Минимальный нормативный диаметр как безопасный fallback
            }

            DiameterSpec chamberSpec = diameterTable.largestOf(adjoiningDiameters);
            int requiredDiameter = chamberSpec.getDiameterMm();
            double chamberCost = calculateNewChamberCost(requiredDiameter);

            candidates.add(TieInCandidate.builder()
                    .connectionPointId(point.getFeatureId())
                    .heatNetworkId(nearestNetwork.getFeatureId())
                    .tieInType(TieInType.NEW_CHAMBER)
                    .existingChamberId(null)
                    .tieInPoint(nearestPointOnNetwork)
                    .distanceToChamberM(0.0)
                    .currentChamberConnections(null)
                    .cost(chamberCost)
                    .requiredChamberDiameter(requiredDiameter)
                    .build());
        }

        // 5. Сортировка по компаратору (лучший кандидат первым)
        candidates.sort(BEST_CANDIDATE_COMPARATOR);

        log.info("Точка подключения [{}]: найдено {} кандидатов на присоединение, лучший: {}",
                point.getFeatureId(), candidates.size(), candidates.get(0));

        return Collections.unmodifiableList(candidates);
    }

    /**
     * Поиск кандидатов на присоединение по идентификатору загрузки и feature_id точки ОКС
     *
     * @param uploadId          идентификатор сессии загрузки
     * @param connectionPointId идентификатор точки подключения ОКС
     * @return список кандидатов на присоединение
     */
    @Transactional(readOnly = true)
    public List<TieInCandidate> findCandidatesForPoint(UUID uploadId, String connectionPointId) {
        if (uploadId == null || connectionPointId == null) {
            return Collections.emptyList();
        }
        return oksConnectionPointRepository.findByUploadIdAndFeatureId(uploadId, connectionPointId)
                .map(p -> findCandidatesForPoint(uploadId, p))
                .orElse(Collections.emptyList());
    }

    /**
     * Выбирает лучшего кандидата на присоединение для конкретной точки ОКС
     *
     * @param uploadId          идентификатор сессии загрузки
     * @param connectionPointId идентификатор точки подключения ОКС
     * @return Optional с лучшим кандидатом или empty
     */
    @Transactional(readOnly = true)
    public Optional<TieInCandidate> findBestCandidateForPoint(UUID uploadId, String connectionPointId) {
        List<TieInCandidate> candidates = findCandidatesForPoint(uploadId, connectionPointId);
        return selectBestCandidate(candidates);
    }

    /**
     * Выбирает лучшего кандидата на присоединение для сущности точки ОКС
     *
     * @param uploadId идентификатор сессии загрузки
     * @param point    сущность точки подключения ОКС
     * @return Optional с лучшим кандидатом или empty
     */
    @Transactional(readOnly = true)
    public Optional<TieInCandidate> findBestCandidateForPoint(UUID uploadId, OksConnectionPointEntity point) {
        List<TieInCandidate> candidates = findCandidatesForPoint(uploadId, point);
        return selectBestCandidate(candidates);
    }

    /**
     * Вычисляет списки кандидатов на присоединение для всех точек подключения указанной загрузки.
     *
     * @param uploadId идентификатор сессии загрузки
     * @return Map, где ключ — feature_id точки ОКС, значение — список кандидатов
     */
    @Transactional(readOnly = true)
    public Map<String, List<TieInCandidate>> findCandidatesForAllPoints(UUID uploadId) {
        if (uploadId == null) {
            return Collections.emptyMap();
        }

        List<OksConnectionPointEntity> points = oksConnectionPointRepository.findByUploadId(uploadId);
        Map<String, List<TieInCandidate>> result = new LinkedHashMap<>();

        for (OksConnectionPointEntity point : points) {
            List<TieInCandidate> candidates = findCandidatesForPoint(uploadId, point);
            result.put(point.getFeatureId(), candidates);
        }

        log.info("Сводка кандидатов на присоединение для загрузки [{}]: обработано {} точек подключения ОКС",
                uploadId, points.size());

        return Collections.unmodifiableMap(result);
    }

    /**
     * Вычисляет единственного лучшего кандидата на присоединение для каждой точки ОКС загрузки.
     *
     * @param uploadId идентификатор сессии загрузки
     * @return Map, где ключ — feature_id точки ОКС, значение — выбранный лучший кандидат
     */
    @Transactional(readOnly = true)
    public Map<String, TieInCandidate> findBestCandidatesForAllPoints(UUID uploadId) {
        Map<String, List<TieInCandidate>> all = findCandidatesForAllPoints(uploadId);
        Map<String, TieInCandidate> result = new LinkedHashMap<>();

        all.forEach((pointId, candidates) ->
                selectBestCandidate(candidates).ifPresent(best -> result.put(pointId, best)));

        return Collections.unmodifiableMap(result);
    }

    /**
     * Выбирает наилучшего кандидата из переданного списка на основе зафиксированного компаратора.
     *
     * @param candidates список кандидатов
     * @return Optional с лучшим кандидатом или empty для пустого списка
     */
    public Optional<TieInCandidate> selectBestCandidate(List<TieInCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream().min(BEST_CANDIDATE_COMPARATOR);
    }
}
