package ru.moscow.heat.geojson.repository;

import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.moscow.heat.geojson.entity.HeatChamberEntity;

import java.util.List;
import java.util.UUID;

/**
 * Репозиторий существующих тепловых камер.
 * Предоставляет методы пространственного поиска камер в окрестности
 * потенциальных точек присоединения тепловой сети
 */
public interface HeatChamberRepository
        extends UploadAwareRepository<HeatChamberEntity> {

    /**
     * Поиск тепловых камер в радиусе от заданной точки.
     * Пространственная фильтрация выполняется в метрической проекции
     * UTM zone 37N (EPSG:32637) по колонке {@code geometry_utm} с
     * использованием GIST-индекса. Результаты упорядочены по
     * возрастанию расстояния до точки
     * @param uploadId     идентификатор сессии загрузки
     * @param pointWkt     точка в формате WKT в WGS 84 (EPSG:4326)
     * @param radiusMeters радиус поиска в метрах
     * @return список камер в заданном радиусе
     */
    @Query(value = "SELECT * FROM heat_chamber c "
            + "WHERE c.upload_id = :uploadId "
            + "AND ST_DWithin(c.geometry_utm, "
            + "ST_Transform(ST_GeomFromText(:pointWkt, 4326), 32637), "
            + ":radiusMeters) "
            + "ORDER BY ST_Distance(c.geometry_utm, "
            + "ST_Transform(ST_GeomFromText(:pointWkt, 4326), 32637)) "
            + "ASC",
            nativeQuery = true)
    List<HeatChamberEntity> findWithinDistance(
            @Param("uploadId") UUID uploadId,
            @Param("pointWkt") String pointWkt,
            @Param("radiusMeters") double radiusMeters);

    /**
     * Поиск камер в радиусе от точки в WGS 84
     * @param uploadId     идентификатор сессии загрузки
     * @param pointWgs84   точка в EPSG:4326
     * @param radiusMeters радиус поиска в метрах
     * @return список камер в заданном радиусе
     */
    default List<HeatChamberEntity> findWithinDistance(
            UUID uploadId, Point pointWgs84, double radiusMeters) {
        if (pointWgs84 == null || uploadId == null) {
            return List.of();
        }
        return findWithinDistance(
                uploadId, pointWgs84.toText(), radiusMeters);
    }
}
