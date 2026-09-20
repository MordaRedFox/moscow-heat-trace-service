package ru.moscow.heat.geojson.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.UploadStatus;
import ru.moscow.heat.geojson.dto.GeoJsonUploadResponse;
import ru.moscow.heat.geojson.dto.UploadSummary;
import ru.moscow.heat.geojson.entity.UploadSession;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Асинхронный обработчик загрузки: читает временный файл,
 * вызывает парсер, обновляет сессию и удаляет временный файл
 * в блоке {@code finally} независимо от результата
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoJsonAsyncProcessor {

    private final UploadSessionRepository sessionRepository;
    private final GeoJsonParserService parserService;
    private final ObjectMapper objectMapper;

    /**
     * Обрабатывает загрузку в фоновом потоке. Гарантирует удаление временного
     * файла независимо от успеха или ошибки
     * @param uploadId идентификатор сессии загрузки
     */
    @Async("geoJsonTaskExecutor")
    public void processAsync(UUID uploadId) {
        MDC.put("uploadId", uploadId.toString());
        try {
            UploadSession session = sessionRepository.findById(uploadId)
                    .orElseThrow(() -> new IllegalStateException(
                            "UploadSession not found: " + uploadId));

            session.setStatus(UploadStatus.PROCESSING);
            session.setStartedAt(OffsetDateTime.now());
            sessionRepository.save(session);

            Path temp = Path.of(session.getTempFilePath());

            GeoJsonUploadResponse result;
            try (InputStream in = Files.newInputStream(temp)) {
                result = parserService.processStream(in, uploadId);
            }

            UploadSummary summary = UploadSummary.builder()
                    .countsByType(result.getCountsByType())
                    .bbox(result.getBbox())
                    .totalErrorsCount(result.getTotalErrorsCount())
                    .errors(result.getErrors())
                    .errorsTruncated(result.isErrorsTruncated())
                    .build();

            session.setStatus(UploadStatus.COMPLETED);
            session.setCompletedAt(OffsetDateTime.now());
            session.setTotalCount(result.getTotalCount());
            session.setTotalErrorsCount(result.getTotalErrorsCount());
            session.setSummary(objectMapper.valueToTree(summary));
            sessionRepository.save(session);

            log.info("Upload {} завершён: {} объектов, {} ошибок",
                    uploadId, result.getTotalCount(),
                    result.getTotalErrorsCount());

        } catch (Exception e) {
            log.error("Ошибка обработки загрузки {}", uploadId, e);
            sessionRepository.findById(uploadId).ifPresent(s -> {
                s.setStatus(UploadStatus.FAILED);
                s.setCompletedAt(OffsetDateTime.now());
                s.setErrorMessage(truncate(e.getMessage(), 3900));
                sessionRepository.save(s);
            });
        } finally {
            cleanupTempFile(uploadId);
            MDC.remove("uploadId");
        }
    }

    /**
     * Удаляет временный файл сессии и обнуляет путь в БД
     * @param uploadId идентификатор сессии загрузки
     */
    private void cleanupTempFile(UUID uploadId) {
        sessionRepository.findById(uploadId).ifPresent(s -> {
            String path = s.getTempFilePath();
            if (path == null) {
                return;
            }
            try {
                boolean deleted = Files.deleteIfExists(Path.of(path));
                if (!deleted) {
                    log.debug("Временный файл {} уже отсутствует",
                            path);
                }
            } catch (Exception ex) {
                log.warn("Не удалось удалить временный файл {}",
                        path, ex);
            }
            s.setTempFilePath(null);
            sessionRepository.save(s);
        });
    }

    /**
     * Обрезает строку до максимальной длины. Используется для
     * умещения сообщения об ошибке в колонку {@code error_message}
     * @param s   исходная строка (может быть {@code null})
     * @param max максимальная длина результата
     * @return обрезанная строка или {@code null}
     */
    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
