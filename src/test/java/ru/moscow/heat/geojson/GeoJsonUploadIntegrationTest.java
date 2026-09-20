package ru.moscow.heat.geojson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import ru.moscow.heat.AbstractIntegrationTest;
import ru.moscow.heat.geojson.repository.GeoFeatureRepository;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозные интеграционные тесты загрузки GeoJSON через HTTP.
 * Поднимается реальный Spring-контекст, PostgreSQL через
 * {@link AbstractIntegrationTest} и MockMvc. Проверяется полный
 * цикл: POST /upload → асинхронная обработка → опрос статуса через
 * GET /uploads/{id} до перехода сессии в COMPLETED
 */
@AutoConfigureMockMvc
class GeoJsonUploadIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UploadSessionRepository sessionRepo;

    @Autowired
    private GeoFeatureRepository featureRepo;

    /**
     * Полный цикл на файле из двух валидных фич: обе сохраняются,
     * статус переходит в COMPLETED, счетчики совпадают, сессия
     * присутствует в БД
     * @throws Exception при ошибке HTTP-запроса или разбора JSON
     */
    @Test
    void fullCycle_uploadAndPollUntilCompleted() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "s1", "source", "Point", 37.6, 55.75));
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "s2", "source", "Point", 37.7, 55.8));

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.geojson", "application/geo+json",
                TestGeoJsonFactory.toBytes(c));

        String body = mvc
                .perform(multipart("/api/geojson/upload").file(file))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID uploadId = UUID.fromString(
                objectMapper.readTree(body)
                        .get("uploadId").asText());

        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    String statusBody = mvc
                            .perform(get("/api/geojson/uploads/{id}",
                                    uploadId))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
                    JsonNode node = objectMapper.readTree(statusBody);
                    assertThat(node.get("status").asText())
                            .isEqualTo("COMPLETED");
                    assertThat(node.get("totalCount").asInt())
                            .isEqualTo(2);
                });

        assertThat(featureRepo.countByUploadId(uploadId))
                .isEqualTo(2);
        assertThat(sessionRepo.findById(uploadId)).isPresent();
    }

    /**
     * Смешанный файл: одна валидная фича и одна с неизвестным
     * {@code object_type}. Обработка завершается успешно, статус
     * COMPLETED, счетчики: 1 сохраненный, 1 ошибка
     * @throws Exception при ошибке HTTP-запроса или разбора JSON
     */
    @Test
    void uploadWithValidationErrors_stillCompletes() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "s1", "source", "Point", 37.6, 55.75));
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "bad", "unknown_type", "Point", 37.6, 55.75));

        MockMultipartFile file = new MockMultipartFile(
                "file", "mixed.geojson", "application/geo+json",
                TestGeoJsonFactory.toBytes(c));

        String body = mvc
                .perform(multipart("/api/geojson/upload").file(file))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID uploadId = UUID.fromString(
                objectMapper.readTree(body)
                        .get("uploadId").asText());

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> {
                    String sb = mvc
                            .perform(get("/api/geojson/uploads/{id}",
                                    uploadId))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
                    JsonNode n = objectMapper.readTree(sb);
                    assertThat(n.get("status").asText())
                            .isEqualTo("COMPLETED");
                    assertThat(n.get("totalErrorsCount").asInt())
                            .isEqualTo(1);
                    assertThat(n.get("totalCount").asInt())
                            .isEqualTo(1);
                });
    }
}
