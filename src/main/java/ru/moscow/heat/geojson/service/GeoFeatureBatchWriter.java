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
 * Пакетная вставка {@link GeoFeature} в двух представлениях:
 * <ul>
 *   <li>сырая таблица {@code geo_feature} — исходный JSONB + PostGIS;</li>
 *   <li>типизированная таблица ({@code source}, {@code heat_network}
 *       и т.д.) — через {@link GeoObjectPersister}.</li>
 * </ul>
 * Оба представления пишутся в одной транзакции, поэтому при
 * ошибке откатываются согласованно. {@code em.clear()} в
 * {@code finally} очищает persistence context даже после
 * неудачного {@code flush}
 */
@Service
@RequiredArgsConstructor
public class GeoFeatureBatchWriter {

    private final GeoFeatureRepository repository;
    private final GeoObjectPersister persister;

    @PersistenceContext
    private EntityManager em;

    /**
     * Пакетная вставка объектов в одной транзакции
     * @param batch список объектов для сохранения
     */
    @Transactional
    public void saveBatch(List<GeoFeature> batch) {
        try {
            for (GeoFeature f : batch) {
                persister.persist(f);
            }
            repository.saveAll(batch);
            em.flush();
        } finally {
            em.clear();
        }
    }

    /**
     * Поштучная вставка. Используется при разборе батча, в котором
     * обнаружены дубликаты id, чтобы локализовать проблемную фичу
     * @param feature сохраняемый объект
     */
    @Transactional
    public void saveSingle(GeoFeature feature) {
        try {
            persister.persist(feature);
            repository.save(feature);
            em.flush();
        } finally {
            em.clear();
        }
    }
}
