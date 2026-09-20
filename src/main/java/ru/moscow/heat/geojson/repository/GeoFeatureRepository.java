package ru.moscow.heat.geojson.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.moscow.heat.geojson.entity.GeoFeature;

import java.util.List;
import java.util.UUID;

/**
 * Репозиторий загруженных объектов GeoJSON. Все операции
 * изолированы по {@code upload_id}.
 *
 * <p>Пространственные запросы написаны нативным SQL с функциями PostGIS
 * и опираются на GIST-индексы по колонкам {@code geom} / {@code geom_utm}.
 */
public interface GeoFeatureRepository
        extends JpaRepository<GeoFeature, Long> {

    /**
     * Возвращает количество объектов в рамках одной загрузки
     */
    long countByUploadId(UUID uploadId);

    /**
     * Удаляет все объекты указанной загрузки. Используется при очистке
     * старых сессий
     */
    void deleteByUploadId(UUID uploadId);

    /**
     * Объекты, попадающие в ограничивающий прямоугольник (WGS 84).
     * Оператор {@code &&} работает через GIST-индекс по {@code geom}
     *
     * @param minX/minY/maxX/maxY границы bbox в градусах EPSG:4326
     */
    @Query(value = "SELECT * FROM geo_feature f WHERE f.upload_id = :uploadId "
            + "AND f.geom && ST_MakeEnvelope(:minX, :minY, :maxX, :maxY, 4326)",
            nativeQuery = true)
    List<GeoFeature> findWithinBbox(
            @Param("uploadId") UUID uploadId,
            @Param("minX") double minX,
            @Param("minY") double minY,
            @Param("maxX") double maxX,
            @Param("maxY") double maxY);

    /**
     * Объекты, пересекающие заданную геометрию (WKT в EPSG:4326).
     * Пригодится в итерации трассировки для поиска ограничений под маршрутом
     */
    @Query(value = "SELECT * FROM geo_feature f WHERE f.upload_id = :uploadId "
            + "AND ST_Intersects(f.geom, ST_GeomFromText(:wkt, 4326))",
            nativeQuery = true)
    List<GeoFeature> findIntersecting(
            @Param("uploadId") UUID uploadId,
            @Param("wkt") String wkt);

    /**
     * Комбинированный поиск: тип объекта + пересечение с геометрией
     *
     * @param objectType имя константы enum (значение колонки object_type)
     */
    @Query(value = "SELECT * FROM geo_feature f WHERE f.upload_id = :uploadId "
            + "AND f.object_type = :objectType "
            + "AND ST_Intersects(f.geom, ST_GeomFromText(:wkt, 4326))",
            nativeQuery = true)
    List<GeoFeature> findByObjectTypeAndGeometryIntersects(
            @Param("uploadId") UUID uploadId,
            @Param("objectType") String objectType,
            @Param("wkt") String wkt);

    /**
     * Объекты в радиусе (метры) от заданной геометрии. Расчёт идёт в UTM 37N
     * через материализованную колонку {@code geom_utm} (GIST + ST_DWithin)
     *
     * @param wkt          геометрия-центр в EPSG:4326 (WKT)
     * @param radiusMeters радиус поиска в метрах
     */
    @Query(value = "SELECT * FROM geo_feature f WHERE f.upload_id = :uploadId "
            + "AND ST_DWithin(f.geom_utm, ST_Transform(ST_GeomFromText(:wkt, 4326), 32637), :radiusMeters)",
            nativeQuery = true)
    List<GeoFeature> findWithinDistanceMeters(
            @Param("uploadId") UUID uploadId,
            @Param("wkt") String wkt,
            @Param("radiusMeters") double radiusMeters);
}