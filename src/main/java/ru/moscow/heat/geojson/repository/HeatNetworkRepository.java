package ru.moscow.heat.geojson.repository;

import ru.moscow.heat.geojson.entity.HeatNetworkEntity;

/**
 * Репозиторий участков существующей тепловой сети
 */
public interface HeatNetworkRepository
        extends UploadAwareRepository<HeatNetworkEntity> {
}
