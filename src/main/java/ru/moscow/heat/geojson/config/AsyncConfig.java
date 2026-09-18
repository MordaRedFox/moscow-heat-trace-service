package ru.moscow.heat.geojson.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Конфигурация асинхронного выполнения и планировщика
 * Включает {@code @Async} для обработки загрузок и {@code @Scheduled}
 * для очистки сессий
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * Executor для парсинга GeoJSON. Ограничен 2-4 потоками, чтобы не
     * перегружать БД и память при загрузке файлов до 3 ГБ
     * @return пул потоков для асинхронной обработки загрузок
     */
    @Bean(name = "geoJsonTaskExecutor")
    public Executor geoJsonTaskExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(64);
        ex.setThreadNamePrefix("geojson-");
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(120);
        ex.initialize();
        return ex;
    }
}
