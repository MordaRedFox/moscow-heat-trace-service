package ru.moscow.heat.geojson.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Создаёт GIST-индексы по пространственным колонкам geo_feature после старта
 * приложения (т.е. после того, как Hibernate ddl-auto создаст/обновит таблицу).
 * Все операторы идемпотентны (IF NOT EXISTS), повторный запуск безопасен.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpatialIndexInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_geo_feature_geom_gist "
                    + "ON geo_feature USING GIST (geom)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_geo_feature_geom_utm_gist "
                    + "ON geo_feature USING GIST (geom_utm)");
            log.info("GIST-индексы geo_feature(geom, geom_utm) проверены/созданы");
        } catch (Exception e) {
            // Индексы критичны для производительности, но не для работоспособности:
            // не роняем приложение, проблему будет видно в логах
            log.error("Не удалось создать GIST-индексы: {}", e.getMessage(), e);
        }
    }
}