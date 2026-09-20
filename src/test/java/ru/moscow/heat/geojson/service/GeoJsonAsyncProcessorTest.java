package ru.moscow.heat.geojson.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.moscow.heat.geojson.UploadStatus;
import ru.moscow.heat.geojson.dto.GeoJsonUploadResponse;
import ru.moscow.heat.geojson.entity.UploadSession;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Тесты асинхронного обработчика загрузки {@link GeoJsonAsyncProcessor}.
 * Проверяют жизненный цикл сессии (PENDING → COMPLETED/FAILED),
 * корректное заполнение полей сессии и гарантированное удаление
 * временного файла независимо от результата обработки
 */
@ExtendWith(MockitoExtension.class)
class GeoJsonAsyncProcessorTest {

    @Mock
    private UploadSessionRepository sessionRepository;

    @Mock
    private GeoJsonParserService parserService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private GeoJsonAsyncProcessor processor;

    @TempDir
    private Path tempDir;

    /**
     * Подменяет реальный {@link ObjectMapper} в тестируемом бине,
     * чтобы не поднимать Spring-контекст
     */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(
                processor, "objectMapper", objectMapper);
    }

    /**
     * Успешный сценарий: парсер завершается без ошибок, сессия
     * переводится в статус COMPLETED, временный файл удаляется,
     * ссылка на него в сессии обнуляется
     * @throws Exception при ошибке создания временного файла
     */
    @Test
    void processAsync_success_marksCompletedAndDeletesFile()
            throws Exception {
        UUID id = UUID.randomUUID();
        Path tmp = Files.createFile(tempDir.resolve("x.geojson"));
        UploadSession session = UploadSession.builder()
                .id(id)
                .fileName("x.geojson")
                .fileSize(10)
                .status(UploadStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .tempFilePath(tmp.toString())
                .build();

        when(sessionRepository.findById(id))
                .thenReturn(Optional.of(session));
        when(parserService.processStream(any(), eq(id)))
                .thenReturn(new GeoJsonUploadResponse());

        processor.processAsync(id);

        verify(sessionRepository, atLeastOnce()).save(session);
        assertThat(session.getStatus())
                .isEqualTo(UploadStatus.COMPLETED);
        assertThat(session.getTempFilePath()).isNull();
        assertThat(Files.exists(tmp)).isFalse();
    }

    /**
     * Парсер падает с {@link IOException}. Сессия переводится
     * в статус FAILED, в нее записывается текст ошибки, временный
     * файл удаляется несмотря на исключение
     * @throws Exception при ошибке создания временного файла
     */
    @Test
    void processAsync_parserFails_marksFailedAndDeletesFile()
            throws Exception {
        UUID id = UUID.randomUUID();
        Path tmp = Files.createFile(tempDir.resolve("y.geojson"));
        UploadSession session = UploadSession.builder()
                .id(id)
                .fileName("y.geojson")
                .fileSize(10)
                .status(UploadStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .tempFilePath(tmp.toString())
                .build();

        when(sessionRepository.findById(id))
                .thenReturn(Optional.of(session));
        when(parserService.processStream(any(), eq(id)))
                .thenThrow(new IOException("boom"));

        processor.processAsync(id);

        assertThat(session.getStatus())
                .isEqualTo(UploadStatus.FAILED);
        assertThat(session.getErrorMessage()).contains("boom");
        assertThat(Files.exists(tmp)).isFalse();
    }

    /**
     * Сессия не найдена: обработчик не должен бросать исключение
     * и не должен пытаться сохранять что-либо в репозиторий
     */
    @Test
    void processAsync_sessionNotFound_doesNotThrow() {
        UUID id = UUID.randomUUID();
        when(sessionRepository.findById(id))
                .thenReturn(Optional.empty());

        processor.processAsync(id);

        verify(sessionRepository, never()).save(any());
    }
}
