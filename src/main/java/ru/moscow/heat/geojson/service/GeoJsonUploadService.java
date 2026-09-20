package ru.moscow.heat.geojson.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.moscow.heat.geojson.UploadStatus;
import ru.moscow.heat.geojson.dto.UploadAcceptedResponse;
import ru.moscow.heat.geojson.dto.UploadStatusResponse;
import ru.moscow.heat.geojson.dto.UploadSummary;
import ru.moscow.heat.geojson.entity.UploadSession;
import ru.moscow.heat.geojson.exception.UploadNotFoundException;
import ru.moscow.heat.geojson.repository.UploadSessionRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Оркестратор загрузки GeoJSON: принимает файл, создает сессию
 * и запускает асинхронную обработку. Парсинг делегируется
 * {@link GeoJsonParserService}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoJsonUploadService {

    private final UploadSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;
    private final GeoJsonAsyncProcessor asyncProcessor;

    @Value("${heat.upload.temp-dir}")
    private String tempDir;

    /**
     * Сохраняет файл во временное хранилище и ставит задачу
     * в очередь на асинхронную обработку
     * @param file загруженный multipart-файл (до 3 ГБ)
     * @return данные о принятой загрузке: идентификатор,
     *         статус и URL для опроса статуса
     * @throws IOException при ошибке чтения или записи файла
     */
    public UploadAcceptedResponse acceptUpload(MultipartFile file)
            throws IOException {
        UUID uploadId = UUID.randomUUID();
        Path dir = Path.of(tempDir);
        Files.createDirectories(dir);
        Path temp = dir.resolve(uploadId + ".geojson");

        try (InputStream in = file.getInputStream();
             OutputStream out = Files.newOutputStream(temp,
                     StandardOpenOption.CREATE_NEW,
                     StandardOpenOption.WRITE)) {
            in.transferTo(out);
        }

        UploadSession session = UploadSession.builder()
                .id(uploadId)
                .fileName(Optional
                        .ofNullable(file.getOriginalFilename())
                        .filter(s -> !s.isBlank())
                        .orElse("unnamed.geojson"))
                .fileSize(file.getSize())
                .status(UploadStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .tempFilePath(temp.toString())
                .build();
        sessionRepository.save(session);

        asyncProcessor.processAsync(uploadId);

        return new UploadAcceptedResponse(uploadId,
                UploadStatus.PENDING,
                "/api/geojson/uploads/" + uploadId);
    }

    /**
     * Возвращает текущий статус загрузки, включая сводку
     * и список ошибок валидации
     * @param uploadId идентификатор сессии загрузки
     * @return статус, временные метки, счётчики, bbox,
     *         ошибки и признак усечения списка ошибок
     * @throws UploadNotFoundException если загрузка не найдена
     */
    public UploadStatusResponse getStatus(UUID uploadId) {
        UploadSession s = sessionRepository.findById(uploadId)
                .orElseThrow(() -> new UploadNotFoundException(
                        "Загрузка не найдена: " + uploadId));

        UploadSummary summary = null;
        if (s.getSummary() != null && !s.getSummary().isNull()) {
            try {
                summary = objectMapper.treeToValue(
                        s.getSummary(), UploadSummary.class);
            } catch (Exception e) {
                log.warn("Не удалось разобрать summary для {}",
                        uploadId, e);
            }
        }

        return UploadStatusResponse.builder()
                .uploadId(s.getId())
                .status(s.getStatus())
                .fileName(s.getFileName())
                .fileSize(s.getFileSize())
                .createdAt(s.getCreatedAt())
                .startedAt(s.getStartedAt())
                .completedAt(s.getCompletedAt())
                .errorMessage(s.getErrorMessage())
                .totalCount(s.getTotalCount())
                .totalErrorsCount(s.getTotalErrorsCount())
                .countsByType(summary != null
                        ? summary.getCountsByType() : null)
                .bbox(summary != null ? summary.getBbox() : null)
                .errors(summary != null
                        ? summary.getErrors() : null)
                .errorsTruncated(summary != null
                        && summary.isErrorsTruncated())
                .build();
    }
}
