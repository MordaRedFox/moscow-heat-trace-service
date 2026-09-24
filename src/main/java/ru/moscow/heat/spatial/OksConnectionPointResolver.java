package ru.moscow.heat.spatial;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.util.*;

/**
 * Сервис связывания точек подключения ОКС с полигонами ОКС
 * (restriction_type = 'oks')
 * Согласно ТП (раздел 2.2) и разъяснениям организаторов (п. 3):
 * - Полигон restriction_type = 'oks' является пространственным
 *   ограничением
 * - Для полигона, содержащего целевую точку подключения,
 *   допускается один финальный прямой участок без соблюдения
 *   защитного отступа к собственному полигону
 * - Связывание выполняется нативным запросом PostGIS с функцией
 *   ST_Contains по метрической колонке geometry_utm с использованием
 *   пространственных GiST-индексов
 */
@Service
public class OksConnectionPointResolver {

    private final EntityManager entityManager;

    public OksConnectionPointResolver(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(
                entityManager, "EntityManager must not be null");
    }

    /**
     * Для каждой точки oks_connection_point находит feature_id
     * содержащего ее полигона restriction с restriction_type = 'oks'.
     * Точка, лежащая строго на границе полигона, по семантике OGC
     * ST_Contains НЕ считается принадлежащей внутренней области
     * полигона и не будет включена в результат
     * @param uploadId идентификатор сессии загрузки
     * @return Map, где ключ — feature_id точки, значение — feature_id
     *         полигона ОКС
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
     * Возвращает список feature_id точек oks_connection_point,
     * которые не входят ни в один полигон restriction
     * с restriction_type = 'oks' (включая точки, лежащие на границе полигона)
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
                + "      AND ST_Contains(r.geometry_utm, "
                + "p.geometry_utm)"
                + "  ) "
                + "ORDER BY p.feature_id";

        @SuppressWarnings("unchecked")
        List<String> list = entityManager.createNativeQuery(sql)
                .setParameter("uploadId", uploadId)
                .getResultList();

        return Collections.unmodifiableList(list);
    }
}
