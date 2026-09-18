package ru.moscow.heat.geojson.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.moscow.heat.geojson.entity.GeoFeature;

import java.util.UUID;

/**
 * Репозиторий загруженных объектов GeoJSON. Все операции
 * изолированы по {@code upload_id}
 */
public interface GeoFeatureRepository
        extends JpaRepository<GeoFeature, Long> {

    /**
     * Возвращает количество объектов в рамках одной загрузки
     * @param uploadId идентификатор сессии загрузки
     * @return число сохраненных объектов
     */
    long countByUploadId(UUID uploadId);

    /**
     * Удаляет все объекты указанной загрузки. Используется при очистке
     * старых сессий
     * @param uploadId идентификатор сессии загрузки
     */
    void deleteByUploadId(UUID uploadId);
}
