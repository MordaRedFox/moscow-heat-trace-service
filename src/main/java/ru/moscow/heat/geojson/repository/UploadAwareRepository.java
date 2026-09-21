package ru.moscow.heat.geojson.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.UUID;

/**
 * Базовый интерфейс для репозиториев типизированных объектов.
 * Добавляет поиск и удаление по {@code upload_id}, чтобы каждый
 * репозиторий не дублировал эти методы
 * @param <T> тип сущности-наследника {@code AbstractGeoObject}
 */
@NoRepositoryBean
public interface UploadAwareRepository<T>
        extends JpaRepository<T, Long> {

    /**
     * Возвращает все объекты указанной загрузки
     * @param uploadId идентификатор сессии загрузки
     * @return список объектов
     */
    List<T> findByUploadId(UUID uploadId);

    /**
     * Удаляет все объекты указанной загрузки. Используется
     * планировщиком очистки
     * @param uploadId идентификатор сессии загрузки
     */
    void deleteByUploadId(UUID uploadId);
}
