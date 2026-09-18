package ru.moscow.heat.geojson.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import ru.moscow.heat.geojson.ObjectType;

import javax.persistence.*;
import java.util.UUID;

/**
 * Загруженный объект GeoJSON. Изолирован по {@code upload_id}:
 * уникальность обеспечивается парой (upload_id, feature_id),
 * что позволяет параллельно обрабатывать несколько файлов
 */
@Getter
@Setter
@Builder
@Entity
@Table(
        name = "geo_feature",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_geo_feature_upload_feature",
                columnNames = {"upload_id", "feature_id"}),
        indexes = @Index(name = "idx_geo_feature_upload", columnList = "upload_id"))
@TypeDef(name = "jsonb", typeClass = JsonNodeBinaryType.class)
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class GeoFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "geo_feature_seq")
    @SequenceGenerator(
            name = "geo_feature_seq",
            sequenceName = "geo_feature_seq",
            allocationSize = 500)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "upload_id", nullable = false)
    private UUID uploadId;

    @Column(name = "feature_id", nullable = false)
    private String featureId;

    @Enumerated(EnumType.STRING)
    @Column(name = "object_type", nullable = false)
    private ObjectType objectType;

    @Column(name = "geometry_type", nullable = false)
    private String geometryType;

    @ToString.Exclude
    @Column(name = "geometry", nullable = false, columnDefinition = "jsonb")
    @Type(type = "jsonb")
    private JsonNode geometry;

    @ToString.Exclude
    @Column(name = "properties", nullable = false, columnDefinition = "jsonb")
    @Type(type = "jsonb")
    private JsonNode properties;
}
