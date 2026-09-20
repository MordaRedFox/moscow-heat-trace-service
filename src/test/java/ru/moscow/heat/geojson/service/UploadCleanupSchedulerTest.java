package ru.moscow.heat.geojson.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.moscow.heat.geojson.UploadStatus;
import ru.moscow.heat.geojson.entity.UploadSession;
import ru.moscow.heat.geojson.repository.GeoFeatureRepository;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit-тесты планировщика очистки {@link UploadCleanupScheduler}.
 * Репозитории подменяются моками. Проверяются: удаление связанных
 * фич, временного файла и самой сессии, отсутствие действий при
 * пустом списке старых сессий, а также корректный набор статусов
 * в фильтре выборки
 */
@ExtendWith(MockitoExtension.class)
class UploadCleanupSchedulerTest {

    @Mock
    private UploadSessionRepository sessionRepository;

    @Mock
    private GeoFeatureRepository geoFeatureRepository;

    @InjectMocks
    private UploadCleanupScheduler scheduler;

    @TempDir
    private Path tempDir;

    /**
     * Устанавливает фиксированный срок хранения, чтобы тест
     * не зависел от значения в application.yml
     */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 7);
    }

    /**
     * Полный цикл очистки одной старой сессии: удаляются связанные
     * фичи, временный файл и сама сессия
     * @throws IOException при ошибке создания временного файла
     */
    @Test
    void cleanupOldSessions_deletesFeaturesFileAndSession()
            throws IOException {
        UUID id = UUID.randomUUID();
        Path tmp = Files.createFile(
                tempDir.resolve("old.geojson"));
        UploadSession old = UploadSession.builder()
                .id(id)
                .fileName("old.geojson")
                .fileSize(10)
                .status(UploadStatus.COMPLETED)
                .createdAt(OffsetDateTime.now().minusDays(30))
                .tempFilePath(tmp.toString())
                .build();

        when(sessionRepository.findByStatusInAndCreatedAtBefore(
                anyCollection(), any(OffsetDateTime.class)))
                .thenReturn(List.of(old));

        scheduler.cleanupOldSessions();

        verify(geoFeatureRepository).deleteByUploadId(id);
        verify(sessionRepository).delete(old);
        assertThat(Files.exists(tmp)).isFalse();
    }

    /**
     * Если старых сессий нет, планировщик не выполняет никаких
     * удаляющих операций
     */
    @Test
    void cleanupOldSessions_nothingToDelete_doesNothing() {
        when(sessionRepository.findByStatusInAndCreatedAtBefore(
                anyCollection(), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        scheduler.cleanupOldSessions();

        verify(geoFeatureRepository, never()).deleteByUploadId(any());
        verify(sessionRepository, never()).delete(any());
    }

    /**
     * Регрессия: планировщик должен запрашивать только завершенные
     * и упавшие сессии, не затрагивая активные (PENDING, PROCESSING)
     */
    @Test
    void cleanupOldSessions_pendingAndProcessingAreNotRequested() {
        scheduler.cleanupOldSessions();

        verify(sessionRepository).findByStatusInAndCreatedAtBefore(
                eq(List.of(UploadStatus.COMPLETED,
                        UploadStatus.FAILED)),
                any(OffsetDateTime.class));
    }
}
