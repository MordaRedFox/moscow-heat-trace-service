package ru.moscow.heat.geojson.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.moscow.heat.geojson.entity.GeoFeature;
import ru.moscow.heat.geojson.repository.GeoFeatureRepository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

/**
 * Отдельный бин для пакетной вставки {@link GeoFeature}.
 * Вызовы {@code flush} и {@code clear} заставляют Hibernate
 * отправлять данные одним batch-INSERT, что критично при
 * загрузке больших файлов. {@code clear} выполняется в
 * {@code finally}, чтобы persistence context не оставался
 * грязным после неудачного {@code flush}
 */
@Service
@RequiredArgsConstructor
public class GeoFeatureBatchWriter {

    private final GeoFeatureRepository repository;

    @PersistenceContext
    private EntityManager em;

    /**
     * Пакетная вставка объектов в одной транзакции
     * @param batch список объектов для сохранения
     */
    @Transactional
    public void saveBatch(List<GeoFeature> batch) {
        try {
            repository.saveAll(batch);
            em.flush();
        } finally {
            em.clear();
        }
    }

    /**
     * Поштучная вставка. Используется при разборе батча,
     * в котором обнаружены дубликаты id
     * @param feature сохраняемый объект
     */
    @Transactional
    public void saveSingle(GeoFeature feature) {
        try {
            repository.save(feature);
            em.flush();
        } finally {
            em.clear();
        }
    }
}
