package ru.moscow.heat.geojson.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.moscow.heat.geojson.UploadStatus;
import ru.moscow.heat.geojson.entity.UploadSession;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Репозиторий сессий загрузки GeoJSON
 */
public interface UploadSessionRepository
        extends JpaRepository<UploadSession, UUID> {

    /**
     * Возвращает сессии в заданных статусах, созданные раньше
     * указанного момента. Используется планировщиком очистки
     * @param statuses набор интересующих статусов
     * @param before   верхняя граница по {@code created_at}
     * @return список подходящих сессий
     */
    List<UploadSession> findByStatusInAndCreatedAtBefore(
            Collection<UploadStatus> statuses, OffsetDateTime before);
}
