package ru.moscow.heat;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Базовый класс для интеграционных тестов.
 * Поднимает PostgreSQL в контейнере через Testcontainers и
 * связывает его со Spring-контекстом через
 * {@link DynamicPropertySource}, чтобы не зависеть от внешней
 * базы данных и от значений в {@code application.yml}
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    /**
     * Контейнер PostgreSQL 14 (Alpine). Параметры подключения
     * (URL, логин, пароль) передаются в Spring-контекст через
     * {@link #datasourceProps(DynamicPropertyRegistry)}
     */
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:14-alpine")
                    .withDatabaseName("heat_test")
                    .withUsername("test")
                    .withPassword("test");

    /**
     * Подставляет координаты подключения к контейнеру в
     * Spring-контекст. Вызывается до создания бинов, поэтому
     * datasource инициализируется уже с реальными значениями
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
