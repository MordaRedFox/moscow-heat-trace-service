package ru.moscow.heat.geojson.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.moscow.heat.geojson.UploadStatus;
import ru.moscow.heat.geojson.entity.UploadSession;
import ru.moscow.heat.geojson.repository.GeoFeatureRepository;
import ru.moscow.heat.geojson.repository.HeatChamberRepository;
import ru.moscow.heat.geojson.repository.HeatNetworkRepository;
import ru.moscow.heat.geojson.repository.OksConnectionPointRepository;
import ru.moscow.heat.geojson.repository.RestrictionRepository;
import ru.moscow.heat.geojson.repository.SourceRepository;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Периодическая очистка завершенных и упавших сессий старше
 * заданного числа дней. Удаляет объекты {@code geo_feature},
 * строки из 5 типизированных таблиц ({@code source},
 * {@code heat_network}, {@code heat_chamber},
 * {@code oks_connection_point}, {@code restriction}),
 * временный файл и саму сессию
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadCleanupScheduler {

    private final UploadSessionRepository sessionRepository;
    private final GeoFeatureRepository geoFeatureRepository;
    private final SourceRepository sourceRepo;
    private final HeatNetworkRepository heatNetworkRepo;
    private final HeatChamberRepository heatChamberRepo;
    private final OksConnectionPointRepository oksCpRepo;
    private final RestrictionRepository restrictionRepo;

    @Value("${heat.upload.session-retention-days:7}")
    private int retentionDays;

    /**
     * Запускается ежедневно в 03:00. Удаляет сессии в статусах
     * {@code COMPLETED} и {@code FAILED}, созданные ранее порога хранения
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldSessions() {
        OffsetDateTime threshold = OffsetDateTime.now()
                .minusDays(retentionDays);
        List<UploadSession> old =
                sessionRepository.findByStatusInAndCreatedAtBefore(
                        List.of(UploadStatus.COMPLETED,
                                UploadStatus.FAILED),
                        threshold);

        if (old.isEmpty()) {
            return;
        }

        log.info("Очистка старых загрузок: {}", old.size());
        for (UploadSession s : old) {
            try {
                cleanupUploadData(s.getId());
                if (s.getTempFilePath() != null) {
                    Files.deleteIfExists(
                            Path.of(s.getTempFilePath()));
                }
                sessionRepository.delete(s);
            } catch (Exception e) {
                log.warn("Не удалось удалить сессию {}",
                        s.getId(), e);
            }
        }
    }

    /**
     * Удаляет все данные указанной загрузки из {@code geo_feature}
     * и 5 типизированных таблиц
     */
    private void cleanupUploadData(UUID uploadId) {
        sourceRepo.deleteByUploadId(uploadId);
        heatNetworkRepo.deleteByUploadId(uploadId);
        heatChamberRepo.deleteByUploadId(uploadId);
        oksCpRepo.deleteByUploadId(uploadId);
        restrictionRepo.deleteByUploadId(uploadId);
        geoFeatureRepository.deleteByUploadId(uploadId);
    }
}
