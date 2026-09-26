package ru.moscow.heat.spatial;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.*;

/**
 * Сервис связывания точек подключения ОКС с полигонами ОКС
 * ({@code restriction_type = 'oks'})
 * <p>Согласно ТП (раздел 2.2) и разъяснениям (п. 3):
 * <ul>
 *     <li>полигон {@code restriction_type = 'oks'} является
 *     пространственным ограничением;</li>
 *     <li>для полигона, содержащего целевую точку подключения, допускается
 *     один финальный прямой участок без соблюдения защитного отступа
 *     к собственному полигону;</li>
 *     <li>связывание выполняется нативным запросом PostGIS с функцией
 *     {@code ST_Contains} по метрической колонке {@code geometry_utm}.</li>
 * </ul>
 */
@Service
public class OksConnectionPointResolver {

    private final EntityManager entityManager;

    public OksConnectionPointResolver(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(
                entityManager, "EntityManager must not be null");
    }

    /**
     * Для каждой точки {@code oks_connection_point} находит {@code feature_id}
     * содержащего её полигона {@code restriction_type='oks'}
     * @param uploadId идентификатор сессии загрузки
     * @return Map: feature_id точки → feature_id полигона
     */
    @Transactional(readOnly = true)
    public Map<String, String> resolvePolygonIds(UUID uploadId) {
        if (uploadId == null) {
            return Collections.emptyMap();
        }

        String sql = "SELECT p.feature_id AS point_id, "
                + "r.feature_id AS polygon_id "
                + "FROM oks_connection_point p "
                + "JOIN restriction r ON p.upload_id = r.upload_id "
                + "WHERE p.upload_id = :uploadId "
                + "  AND r.restriction_type = 'oks' "
                + "  AND ST_Contains(r.geometry_utm, p.geometry_utm)";

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("uploadId", uploadId)
                .getResultList();

        Map<String, String> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String pointId = (String) row[0];
            String polygonId = (String) row[1];
            map.putIfAbsent(pointId, polygonId);
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Возвращает первичный ключ ({@code id}) полигона ОКС, содержащего
     * заданную точку подключения. Используется в {@code TraceOrchestrator}
     * для подстановки в набор игнорируемых FORBIDDEN-зон при поиске пути
     * (см. {@code VisibilityGraph.shortestPath(..., Set<Long>)})
     * @param uploadId        идентификатор сессии загрузки
     * @param pointFeatureId  feature_id точки подключения ОКС
     * @return id полигона, если точка лежит строго внутри него;
     *         {@code Optional.empty()} в противном случае
     */
    @Transactional(readOnly = true)
    public Optional<Long> resolvePolygonDatabaseId(UUID uploadId,
                                                    String pointFeatureId) {
        if (uploadId == null || pointFeatureId == null) {
            return Optional.empty();
        }
        String sql = "SELECT r.id FROM oks_connection_point p "
                + "JOIN restriction r ON p.upload_id = r.upload_id "
                + "WHERE p.upload_id = :uploadId "
                + "  AND p.feature_id = :pointFeatureId "
                + "  AND r.restriction_type = 'oks' "
                + "  AND ST_Contains(r.geometry_utm, p.geometry_utm) "
                + "LIMIT 1";

        @SuppressWarnings("unchecked")
        List<Number> rows = entityManager.createNativeQuery(sql)
                .setParameter("uploadId", uploadId)
                .setParameter("pointFeatureId", pointFeatureId)
                .getResultList();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0).longValue());
    }

    /**
     * Возвращает список {@code feature_id} точек ОКС, не входящих
     * ни в один полигон {@code restriction_type='oks'}
     * @param uploadId идентификатор сессии загрузки
     * @return список feature_id непривязанных точек
     */
    @Transactional(readOnly = true)
    public List<String> findUnboundConnectionPoints(UUID uploadId) {
        if (uploadId == null) {
            return Collections.emptyList();
        }

        String sql = "SELECT p.feature_id "
                + "FROM oks_connection_point p "
                + "WHERE p.upload_id = :uploadId "
                + "  AND NOT EXISTS ("
                + "    SELECT 1 FROM restriction r "
                + "    WHERE r.upload_id = p.upload_id "
                + "      AND r.restriction_type = 'oks' "
                + "      AND ST_Contains(r.geometry_utm, p.geometry_utm)"
                + "  ) "
                + "ORDER BY p.feature_id";

        @SuppressWarnings("unchecked")
        List<String> list = entityManager.createNativeQuery(sql)
                .setParameter("uploadId", uploadId)
                .getResultList();

        return Collections.unmodifiableList(list);
    }
}
