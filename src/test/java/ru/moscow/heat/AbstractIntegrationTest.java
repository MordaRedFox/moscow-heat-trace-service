package ru.moscow.heat;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Базовый класс для интеграционных тестов.
 * Поднимает PostgreSQL с расширением PostGIS в контейнере через
 * Testcontainers и связывает его со Spring-контекстом через
 * {@link DynamicPropertySource}
 */
@SpringBootTest
@ActiveProfiles("test")
@SuppressWarnings("resource")
public abstract class AbstractIntegrationTest {

    /**
     * Контейнер PostgreSQL + PostGIS. Стартует в статическом
     * блоке, останавливается автоматически при завершении JVM
     */
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>(
                DockerImageName
                        .parse("postgis/postgis:16-3.4")
                        .asCompatibleSubstituteFor("postgres"))
                .withDatabaseName("heat_test")
                .withUsername("test")
                .withPassword("test");
        POSTGRES.start();
    }

    /**
     * Подставляет координаты подключения к контейнеру
     * в Spring-контекст
     * @param registry реестр динамических свойств Spring
     */
    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username",
                POSTGRES::getUsername);
        registry.add("spring.datasource.password",
                POSTGRES::getPassword);
    }
}
