package ru.moscow.heat.geojson.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import ru.moscow.heat.geojson.UploadStatus;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Сессия загрузки: жизненный цикл файла от {@code PENDING} до
 * {@code COMPLETED} или {@code FAILED}. Хранит путь к временному
 * файлу, счетчики объектов и ошибок, а также сводку
 */
@Getter
@Setter
@Builder
@Entity
@Table(
        name = "upload_session",
        indexes = {
                @Index(name = "idx_upload_session_status", columnList = "status"),
                @Index(name = "idx_upload_session_created", columnList = "created_at")
        })
@TypeDef(name = "jsonb", typeClass = JsonNodeBinaryType.class)
@NoArgsConstructor
@AllArgsConstructor
public class UploadSession {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private UploadStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "temp_file_path")
    private String tempFilePath;

    @Column(name = "total_count")
    private Integer totalCount;

    @Column(name = "total_errors_count")
    private Integer totalErrorsCount;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Type(type = "jsonb")
    @Column(name = "summary", columnDefinition = "jsonb")
    private JsonNode summary;
}
