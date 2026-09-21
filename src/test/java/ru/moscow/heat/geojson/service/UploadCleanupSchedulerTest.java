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
import ru.moscow.heat.geojson.repository.HeatChamberRepository;
import ru.moscow.heat.geojson.repository.HeatNetworkRepository;
import ru.moscow.heat.geojson.repository.OksConnectionPointRepository;
import ru.moscow.heat.geojson.repository.RestrictionRepository;
import ru.moscow.heat.geojson.repository.SourceRepository;
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
 * Проверяет, что при удалении старой сессии чистятся данные из
 * {@code geo_feature} и 5 типизированных таблиц, а также удаляется
 * временный файл и сама сессия
 */
@ExtendWith(MockitoExtension.class)
class UploadCleanupSchedulerTest {

    @Mock
    private UploadSessionRepository sessionRepository;

    @Mock
    private GeoFeatureRepository geoFeatureRepository;

    @Mock
    private SourceRepository sourceRepo;

    @Mock
    private HeatNetworkRepository heatNetworkRepo;

    @Mock
    private HeatChamberRepository heatChamberRepo;

    @Mock
    private OksConnectionPointRepository oksCpRepo;

    @Mock
    private RestrictionRepository restrictionRepo;

    @InjectMocks
    private UploadCleanupScheduler scheduler;

    @TempDir
    private Path tempDir;

    /**
     * Фиксированный срок хранения, чтобы тест не зависел от
     * значения в application.yml
     */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "retentionDays", 7);
    }

    /**
     * Полный цикл очистки одной старой сессии: удаляются записи
     * из {@code geo_feature}, всех типизированных таблиц, сессии
     * и временного файла
     * @throws IOException при создании временного файла
     */
    @Test
    void cleanupOldSessions_deletesFromAllTables() throws IOException {
        UUID id = UUID.randomUUID();
        Path tmp = Files.createFile(tempDir.resolve("old.geojson"));
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

        verify(sourceRepo).deleteByUploadId(id);
        verify(heatNetworkRepo).deleteByUploadId(id);
        verify(heatChamberRepo).deleteByUploadId(id);
        verify(oksCpRepo).deleteByUploadId(id);
        verify(restrictionRepo).deleteByUploadId(id);
        verify(geoFeatureRepository).deleteByUploadId(id);
        verify(sessionRepository).delete(old);
        assertThat(Files.exists(tmp)).isFalse();
    }

    /**
     * Если старых сессий нет, планировщик не выполняет удаляющих операций
     */
    @Test
    void cleanupOldSessions_nothingToDelete_doesNothing() {
        when(sessionRepository.findByStatusInAndCreatedAtBefore(
                anyCollection(), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        scheduler.cleanupOldSessions();

        verify(geoFeatureRepository, never()).deleteByUploadId(any());
        verify(sourceRepo, never()).deleteByUploadId(any());
        verify(heatNetworkRepo, never()).deleteByUploadId(any());
        verify(sessionRepository, never()).delete(any());
    }

    /**
     * Регрессия: планировщик запрашивает только завершенные
     * и упавшие сессии, не затрагивая активные
     */
    @Test
    void cleanupOldSessions_onlyCompletedAndFailedRequested() {
        scheduler.cleanupOldSessions();

        verify(sessionRepository).findByStatusInAndCreatedAtBefore(
                eq(List.of(UploadStatus.COMPLETED,
                        UploadStatus.FAILED)),
                any(OffsetDateTime.class));
    }
}
