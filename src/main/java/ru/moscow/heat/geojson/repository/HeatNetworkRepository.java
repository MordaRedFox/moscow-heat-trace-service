package ru.moscow.heat.geojson.repository;

import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.moscow.heat.geojson.entity.HeatNetworkEntity;

import java.util.List;
import java.util.UUID;

/**
 * Репозиторий участков существующей тепловой сети.
 * Предоставляет методы пространственного поиска сегментов сети и
 * расчета количества примыканий участков к узлам (тепловым камерам).
 */
public interface HeatNetworkRepository
        extends UploadAwareRepository<HeatNetworkEntity> {

    /**
     * Поиск участков тепловой сети в заданном радиусе от точки.
     *
     * @param uploadId     идентификатор сессии загрузки
     * @param pointWkt     координаты точки в формате WKT в WGS 84 (EPSG:4326)
     * @param radiusMeters радиус поиска в метрах
     * @return список участков сети
     */
    @Query(value = "SELECT * FROM heat_network n WHERE n.upload_id = :uploadId "
            + "AND ST_DWithin(n.geometry_utm, ST_Transform(ST_SetSRID(ST_GeomFromText(:pointWkt), 4326), 32637), :radiusMeters) "
            + "ORDER BY ST_Distance(n.geometry_utm, ST_Transform(ST_SetSRID(ST_GeomFromText(:pointWkt), 4326), 32637)) ASC",
            nativeQuery = true)
    List<HeatNetworkEntity> findWithinDistance(
            @Param("uploadId") UUID uploadId,
            @Param("pointWkt") String pointWkt,
            @Param("radiusMeters") double radiusMeters);

    /**
     * Поиск ближайших к заданной точке участков тепловой сети с ограничением количества.
     *
     * @param uploadId идентификатор сессии загрузки
     * @param pointWkt координаты точки в формате WKT в WGS 84 (EPSG:4326)
     * @param limit    максимальное количество возвращаемых участков
     * @return список ближайших участков сети
     */
    @Query(value = "SELECT * FROM heat_network n WHERE n.upload_id = :uploadId "
            + "ORDER BY ST_Distance(n.geometry_utm, ST_Transform(ST_SetSRID(ST_GeomFromText(:pointWkt), 4326), 32637)) ASC "
            + "LIMIT :limit",
            nativeQuery = true)
    List<HeatNetworkEntity> findNearest(
            @Param("uploadId") UUID uploadId,
            @Param("pointWkt") String pointWkt,
            @Param("limit") int limit);

    /**
     * Подсчитывает количество примыканий участков тепловой сети к точке камеры.
     * Согласно разделу 3.2 ТП ЛЦТ-2026:
     * - Каждый конец участка сети (ST_StartPoint или ST_EndPoint), совпадающий с точкой
     *   камеры в пределах допуска (1.0 м), считается за 1 примыкание.
     * - Транзитная линия, разделенная камерой на два участка, дает 2 примыкания.
     *
     * @param uploadId        идентификатор сессии загрузки
     * @param chamberPointWkt координаты камеры в формате WKT в WGS 84 (EPSG:4326)
     * @param toleranceMeters допуск совпадения в метрах (нормативно 1.0 м)
     * @return суммарное количество примыкающих концов существующих участков сети
     */
    @Query(value = "WITH lines AS ("
            + "  SELECT (ST_Dump(n.geometry_utm)).geom AS geom "
            + "  FROM heat_network n "
            + "  WHERE n.upload_id = :uploadId "
            + "    AND ST_DWithin(n.geometry_utm, ST_Transform(ST_SetSRID(ST_GeomFromText(:chamberPointWkt), 4326), 32637), :toleranceMeters)"
            + ") "
            + "SELECT CAST(COUNT(*) AS integer) FROM ("
            + "  SELECT 1 FROM lines WHERE ST_DWithin(ST_StartPoint(geom), ST_Transform(ST_SetSRID(ST_GeomFromText(:chamberPointWkt), 4326), 32637), :toleranceMeters) "
            + "  UNION ALL "
            + "  SELECT 1 FROM lines WHERE ST_DWithin(ST_EndPoint(geom), ST_Transform(ST_SetSRID(ST_GeomFromText(:chamberPointWkt), 4326), 32637), :toleranceMeters)"
            + ") endpoints",
            nativeQuery = true)
    int countConnectionsToChamber(
            @Param("uploadId") UUID uploadId,
            @Param("chamberPointWkt") String chamberPointWkt,
            @Param("toleranceMeters") double toleranceMeters);

    default List<HeatNetworkEntity> findWithinDistance(UUID uploadId, Point pointWgs84, double radiusMeters) {
        if (pointWgs84 == null || uploadId == null) {
            return List.of();
        }
        return findWithinDistance(uploadId, pointWgs84.toText(), radiusMeters);
    }

    default List<HeatNetworkEntity> findNearest(UUID uploadId, Point pointWgs84, int limit) {
        if (pointWgs84 == null || uploadId == null) {
            return List.of();
        }
        return findNearest(uploadId, pointWgs84.toText(), limit);
    }

    default int countConnectionsToChamber(UUID uploadId, Point chamberPointWgs84, double toleranceMeters) {
        if (chamberPointWgs84 == null || uploadId == null) {
            return 0;
        }
        return countConnectionsToChamber(uploadId, chamberPointWgs84.toText(), toleranceMeters);
    }
}
