package ru.moscow.heat.geojson.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Создает GIST-индексы по пространственным колонкам всех таблиц
 * после старта приложения (то есть после того, как Hibernate
 * {@code ddl-auto} создаст/обновит схему). Все операторы
 * идемпотентны ({@code IF NOT EXISTS}), повторный запуск безопасен
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpatialIndexInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            // Сырая таблица geo_feature
            gist("geo_feature", "geom");
            gist("geo_feature", "geom_utm");

            // Типизированные таблицы
            gist("source", "geometry");
            gist("source", "geometry_utm");
            gist("heat_network", "geometry");
            gist("heat_network", "geometry_utm");
            gist("heat_chamber", "geometry");
            gist("heat_chamber", "geometry_utm");
            gist("oks_connection_point", "geometry");
            gist("oks_connection_point", "geometry_utm");
            gist("restriction", "geometry");
            gist("restriction", "geometry_utm");

            log.info("GIST-индексы проверены/созданы");
        } catch (Exception e) {
            log.error("Не удалось создать GIST-индексы: {}",
                    e.getMessage(), e);
        }
    }

    /**
     * Идемпотентно создает GIST-индекс на указанной колонке
     */
    private void gist(String table, String column) {
        String indexName = "idx_" + table + "_" + column + "_gist";
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS "
                + indexName + " ON " + table
                + " USING GIST (" + column + ")");
    }
}
