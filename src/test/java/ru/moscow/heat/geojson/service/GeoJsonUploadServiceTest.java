package ru.moscow.heat.geojson.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import ru.moscow.heat.geojson.UploadStatus;
import ru.moscow.heat.geojson.dto.UploadAcceptedResponse;
import ru.moscow.heat.geojson.dto.UploadStatusResponse;
import ru.moscow.heat.geojson.dto.UploadSummary;
import ru.moscow.heat.geojson.entity.UploadSession;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты оркестратора загрузки {@link GeoJsonUploadService}.
 * Используется {@link MockitoExtension}: репозиторий сессий и
 * асинхронный обработчик подменяются моками. Проверяются сценарии
 * приема файла, дефолтное имя файла, чтение статуса, восстановление
 * сводки и обработка отсутствующей загрузки
 */
@ExtendWith(MockitoExtension.class)
class GeoJsonUploadServiceTest {

    @Mock
    private UploadSessionRepository sessionRepository;

    @Mock
    private GeoJsonAsyncProcessor asyncProcessor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private GeoJsonUploadService service;

    @TempDir
    private Path tempDir;

    /**
     * Подставляет временную директорию и {@link ObjectMapper} в
     * тестируемый бин, чтобы не поднимать Spring-контекст
     */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                service, "tempDir", tempDir.toString());
        ReflectionTestUtils.setField(
                service, "objectMapper", objectMapper);
    }

    /**
     * Успешный прием файла: сессия сохраняется со статусом PENDING,
     * временный файл создается на диске, асинхронный обработчик
     * получает идентификатор загрузки. Ответ содержит uploadId,
     * статус и URL для опроса статуса
     * @throws Exception при ошибке чтения или записи файла
     */
    @Test
    void acceptUpload_savesFileAndSessionAndTriggersAsync()
            throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.geojson",
                "application/geo+json", "{}".getBytes());

        UploadAcceptedResponse resp = service.acceptUpload(file);

        assertThat(resp.getUploadId()).isNotNull();
        assertThat(resp.getStatus())
                .isEqualTo(UploadStatus.PENDING);
        assertThat(resp.getStatusUrl())
                .contains(resp.getUploadId().toString());

        ArgumentCaptor<UploadSession> captor =
                ArgumentCaptor.forClass(UploadSession.class);
        verify(sessionRepository).save(captor.capture());
        UploadSession saved = captor.getValue();
        assertThat(saved.getFileName())
                .isEqualTo("test.geojson");
        assertThat(saved.getStatus())
                .isEqualTo(UploadStatus.PENDING);
        assertThat(saved.getTempFilePath()).isNotNull();
        assertThat(Files.exists(Path.of(saved.getTempFilePath())))
                .isTrue();

        verify(asyncProcessor).processAsync(resp.getUploadId());
    }

    /**
     * Регрессия: если {@code originalFilename} равен {@code null},
     * в сессию записывается значение по умолчанию
     * {@code unnamed.geojson}, а не пустая строка
     * @throws Exception при ошибке чтения или записи файла
     */
    @Test
    void acceptUpload_defaultsToUnnamedWhenFilenameNull()
            throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "application/geo+json", "{}".getBytes());

        service.acceptUpload(file);

        ArgumentCaptor<UploadSession> captor =
                ArgumentCaptor.forClass(UploadSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getFileName())
                .isEqualTo("unnamed.geojson");
    }

    /**
     * Получение статуса завершенной загрузки: все поля сессии
     * переносятся в ответ, включая разобранную сводку
     */
    @Test
    void getStatus_returnsFullSummary() {
        UUID id = UUID.randomUUID();
        UploadSummary summary = UploadSummary.builder()
                .totalErrorsCount(2)
                .bbox(List.of(1.0, 2.0, 3.0, 4.0))
                .build();
        ObjectNode summaryNode = objectMapper.valueToTree(summary);

        UploadSession s = UploadSession.builder()
                .id(id)
                .fileName("x.geojson")
                .fileSize(100)
                .status(UploadStatus.COMPLETED)
                .createdAt(OffsetDateTime.now())
                .totalCount(10)
                .totalErrorsCount(2)
                .summary(summaryNode)
                .build();
        when(sessionRepository.findById(id))
                .thenReturn(Optional.of(s));

        UploadStatusResponse r = service.getStatus(id);

        assertThat(r.getUploadId()).isEqualTo(id);
        assertThat(r.getStatus())
                .isEqualTo(UploadStatus.COMPLETED);
        assertThat(r.getFileName()).isEqualTo("x.geojson");
        assertThat(r.getTotalCount()).isEqualTo(10);
    }

    /**
     * Битый JSON в поле {@code summary}: сервис не должен падать,
     * поля сводки возвращаются как {@code null}
     */
    @Test
    void getStatus_summaryCorrupted_returnsNulls() {
        UUID id = UUID.randomUUID();
        ObjectNode badSummary = objectMapper.createObjectNode();
        badSummary.put("countsByType", "not-a-map");

        UploadSession s = UploadSession.builder()
                .id(id)
                .fileName("x.geojson")
                .fileSize(100)
                .status(UploadStatus.COMPLETED)
                .createdAt(OffsetDateTime.now())
                .summary(badSummary)
                .build();
        when(sessionRepository.findById(id))
                .thenReturn(Optional.of(s));

        UploadStatusResponse r = service.getStatus(id);

        assertThat(r.getUploadId()).isEqualTo(id);
        assertThat(r.getCountsByType()).isNull();
    }

    /**
     * Загрузка с указанным идентификатором отсутствует в БД:
     * сервис бросает {@link UploadNotFoundException}, контроллер
     * преобразует это в HTTP 404
     */
    @Test
    void getStatus_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(sessionRepository.findById(id))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatus(id))
                .isInstanceOf(UploadNotFoundException.class)
                .hasMessageContaining("не найдена");
    }
}
