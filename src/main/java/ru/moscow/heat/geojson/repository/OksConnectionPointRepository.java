package ru.moscow.heat.geojson.repository;

import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий точек подключения перспективных ОКС
 */
public interface OksConnectionPointRepository
        extends UploadAwareRepository<OksConnectionPointEntity> {

    /**
     * Поиск точки подключения по идентификатору загрузки и feature_id
     * @param uploadId  идентификатор сессии загрузки
     * @param featureId идентификатор объекта внутри GeoJSON
     * @return Optional с сущностью точки подключения
     */
    Optional<OksConnectionPointEntity> findByUploadIdAndFeatureId(UUID uploadId, String featureId);
}
