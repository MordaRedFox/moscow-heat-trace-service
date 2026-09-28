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
 * подсчета количества примыканий участков к тепловым камерам
 */
public interface HeatNetworkRepository
        extends UploadAwareRepository<HeatNetworkEntity> {

    /**
     * Поиск участков тепловой сети в радиусе от точки
     * @param uploadId     идентификатор сессии загрузки
     * @param pointWkt     точка в формате WKT в WGS 84 (EPSG:4326)
     * @param radiusMeters радиус поиска в метрах
     * @return список участков сети
     */
    @Query(value = "SELECT * FROM heat_network n "
            + "WHERE n.upload_id = :uploadId "
            + "AND ST_DWithin(n.geometry_utm, "
            + "ST_Transform(ST_GeomFromText(:pointWkt, 4326), 32637), "
            + ":radiusMeters) "
            + "ORDER BY ST_Distance(n.geometry_utm, "
            + "ST_Transform(ST_GeomFromText(:pointWkt, 4326), 32637)) "
            + "ASC",
            nativeQuery = true)
    List<HeatNetworkEntity> findWithinDistance(
            @Param("uploadId") UUID uploadId,
            @Param("pointWkt") String pointWkt,
            @Param("radiusMeters") double radiusMeters);

    /**
     * Поиск N ближайших к точке участков сети.
     * Использует KNN-оператор {@code <->}, работающий через
     * GIST-индекс по {@code geometry_utm}
     * @param uploadId идентификатор сессии загрузки
     * @param pointWkt точка в формате WKT в WGS 84 (EPSG:4326)
     * @param limit    максимальное количество участков
     * @return список ближайших участков
     */
    @Query(value = "SELECT * FROM heat_network n "
            + "WHERE n.upload_id = :uploadId "
            + "ORDER BY n.geometry_utm <-> "
            + "ST_Transform(ST_GeomFromText(:pointWkt, 4326), 32637) "
            + "ASC LIMIT :limit",
            nativeQuery = true)
    List<HeatNetworkEntity> findNearest(
            @Param("uploadId") UUID uploadId,
            @Param("pointWkt") String pointWkt,
            @Param("limit") int limit);

    /**
     * Подсчитывает количество примыканий участков сети к точке камеры.
     * Согласно разделу 3.2 ТП и разъяснениям п. 12:
     * <ul>
     *   <li>конец участка (start или end), совпадающий с точкой камеры
     *       в пределах допуска, даёт 1 примыкание;</li>
     *   <li>участок, проходящий через камеру и не начинающийся и не
     *       заканчивающийся в ней, даёт 2 примыкания (камера делит
     *       линию на две части);</li>
     *   <li>если оба конца участка в камере, даются оба примыкания.</li>
     * </ul>
     * Запрос разворачивает {@code MultiLineString} через {@code ST_Dump}
     * и суммирует примыкания по всем вложенным линиям
     * @param uploadId         идентификатор сессии загрузки
     * @param chamberPointWkt  точка камеры в формате WKT в WGS 84
     * @param toleranceMeters  допуск совпадения в метрах (нормативно 1.0 м)
     * @return суммарное количество примыкающих концов участков сети
     */
    @Query(value = "WITH p AS ("
            + "  SELECT ST_Transform("
            + "    ST_GeomFromText(:chamberPointWkt, 4326), 32637) AS geom"
            + "), "
            + "lines AS ("
            + "  SELECT (ST_Dump(n.geometry_utm)).geom AS geom "
            + "  FROM heat_network n, p "
            + "  WHERE n.upload_id = :uploadId "
            + "    AND ST_DWithin(n.geometry_utm, p.geom, :toleranceMeters)"
            + ") "
            + "SELECT CAST(COALESCE(SUM("
            + "  CASE WHEN ST_DWithin(ST_StartPoint(l.geom), p.geom, "
            + "       :toleranceMeters) THEN 1 ELSE 0 END "
            + "+ CASE WHEN ST_DWithin(ST_EndPoint(l.geom), p.geom, "
            + "       :toleranceMeters) THEN 1 ELSE 0 END "
            + "+ CASE "
            + "    WHEN NOT ST_DWithin(ST_StartPoint(l.geom), p.geom, "
            + "         :toleranceMeters) "
            + "     AND NOT ST_DWithin(ST_EndPoint(l.geom), p.geom, "
            + "         :toleranceMeters) "
            + "     AND ST_DWithin(l.geom, p.geom, :toleranceMeters) "
            + "    THEN 2 ELSE 0 "
            + "  END "
            + "), 0) AS integer) "
            + "FROM lines l, p",
            nativeQuery = true)
    int countConnectionsToChamber(
            @Param("uploadId") UUID uploadId,
            @Param("chamberPointWkt") String chamberPointWkt,
            @Param("toleranceMeters") double toleranceMeters);

    default List<HeatNetworkEntity> findWithinDistance(
            UUID uploadId, Point pointWgs84, double radiusMeters) {
        if (pointWgs84 == null || uploadId == null) {
            return List.of();
        }
        return findWithinDistance(
                uploadId, pointWgs84.toText(), radiusMeters);
    }

    default List<HeatNetworkEntity> findNearest(
            UUID uploadId, Point pointWgs84, int limit) {
        if (pointWgs84 == null || uploadId == null) {
            return List.of();
        }
        return findNearest(uploadId, pointWgs84.toText(), limit);
    }

    default int countConnectionsToChamber(
            UUID uploadId, Point chamberPointWgs84,
            double toleranceMeters) {
        if (chamberPointWgs84 == null || uploadId == null) {
            return 0;
        }
        return countConnectionsToChamber(
                uploadId, chamberPointWgs84.toText(),
                toleranceMeters);
    }
}
