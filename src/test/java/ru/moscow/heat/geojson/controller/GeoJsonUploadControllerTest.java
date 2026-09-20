package ru.moscow.heat.geojson.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import ru.moscow.heat.geojson.UploadStatus;
import ru.moscow.heat.geojson.dto.UploadAcceptedResponse;
import ru.moscow.heat.geojson.dto.UploadStatusResponse;
import ru.moscow.heat.geojson.exception.GeoJsonParseException;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.geojson.service.GeoJsonUploadService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тесты контроллера загрузки GeoJSON
 * Используется срез {@link WebMvcTest}: поднимается только веб-слой,
 * сервис {@link GeoJsonUploadService} подменяется моком. Проверяется
 * корректность HTTP-кодов и структуры JSON-ответов на успешные и
 * ошибочные сценарии
 */
@WebMvcTest(GeoJsonUploadController.class)
class GeoJsonUploadControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private GeoJsonUploadService service;

    /**
     * Успешная загрузка файла: сервис возвращает идентификатор
     * и статус PENDING. Ожидается HTTP 202 Accepted и тело
     * с полями {@code uploadId} и {@code status}
     * @throws Exception при ошибке выполнения HTTP-запроса
     */
    @Test
    void upload_returns202WithStatusUrl() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.acceptUpload(any()))
                .thenReturn(new UploadAcceptedResponse(
                        id,
                        UploadStatus.PENDING,
                        "/api/geojson/uploads/" + id));

        MockMultipartFile f = new MockMultipartFile(
                "file",
                "x.geojson",
                "application/geo+json",
                "{}".getBytes());

        mvc.perform(multipart("/api/geojson/upload").file(f))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.uploadId").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    /**
     * Запрос на загрузку без обязательной части {@code file}.
     * Spring MVC должен вернуть HTTP 400 Bad Request
     * @throws Exception при ошибке выполнения HTTP-запроса
     */
    @Test
    void upload_withoutFile_returns400() throws Exception {
        mvc.perform(multipart("/api/geojson/upload")
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest());
    }

    /**
     * Получение статуса существующей загрузки. Сервис возвращает
     * завершённую сессию, ожидается HTTP 200 OK и соответствующие
     * поля в теле ответа
     * @throws Exception при ошибке выполнения HTTP-запроса
     */
    @Test
    void status_returnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getStatus(id))
                .thenReturn(UploadStatusResponse.builder()
                        .uploadId(id)
                        .status(UploadStatus.COMPLETED)
                        .fileName("x.geojson")
                        .build());

        mvc.perform(get("/api/geojson/uploads/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadId")
                        .value(id.toString()))
                .andExpect(jsonPath("$.status")
                        .value("COMPLETED"));
    }

    /**
     * Загрузка не найдена: сервис бросает
     * {@link UploadNotFoundException}. Ожидается HTTP 404 Not Found
     * и текст ошибки в поле {@code error}
     * @throws Exception при ошибке выполнения HTTP-запроса
     */
    @Test
    void status_notFound_shouldReturn404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getStatus(id))
                .thenThrow(new UploadNotFoundException("не найдена"));

        mvc.perform(get("/api/geojson/uploads/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value(
                        "не найдена"));
    }

    /**
     * Ошибка парсинга: сервис бросает
     * {@link GeoJsonParseException}. Ожидается HTTP 400 Bad Request
     * и текст ошибки в поле {@code error}
     * @throws Exception при ошибке выполнения HTTP-запроса
     */
    @Test
    void parseException_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getStatus(id))
                .thenThrow(new GeoJsonParseException("битый JSON"));

        mvc.perform(get("/api/geojson/uploads/{id}", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "битый JSON"));
    }
}
