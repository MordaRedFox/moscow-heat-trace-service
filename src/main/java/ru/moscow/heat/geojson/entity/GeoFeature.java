package ru.moscow.heat.geojson.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.locationtech.jts.geom.Geometry;
import ru.moscow.heat.geojson.ObjectType;

import javax.persistence.*;
import java.util.UUID;

/**
 * Загруженный объект GeoJSON. Изолирован по {@code upload_id}:
 * уникальность обеспечивается парой (upload_id, feature_id),
 * что позволяет параллельно обрабатывать несколько файлов.
 *
 * <p>Геометрия хранится в трёх представлениях:
 * <ul>
 *   <li>{@code geometry} (jsonb) — исходный GeoJSON-узел, как пришёл из файла;</li>
 *   <li>{@code geom} (PostGIS, SRID 4326) — JTS-геометрия для пространственных запросов;</li>
 *   <li>{@code geom_utm} (PostGIS, SRID 32637) — материализованная проекция UTM 37N
 *       для метрических расчётов (длины, буферы, ST_DWithin).</li>
 * </ul>
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
        indexes = {
                @Index(name = "idx_geo_feature_upload", columnList = "upload_id"),
                @Index(name = "idx_geo_feature_object_type", columnList = "object_type")
        })
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

    /** Исходная геометрия GeoJSON (jsonb) */
    @ToString.Exclude
    @Column(name = "geometry", nullable = false, columnDefinition = "jsonb")
    @Type(type = "jsonb")
    private JsonNode geometry;

    @ToString.Exclude
    @Column(name = "properties", nullable = false, columnDefinition = "jsonb")
    @Type(type = "jsonb")
    private JsonNode properties;

    /** PostGIS-геометрия в WGS 84 (SRID 4326), GIST-индекс создаётся SpatialIndexInitializer */
    @ToString.Exclude
    @Column(name = "geom", columnDefinition = "geometry(Geometry,4326)")
    private Geometry geom;

    /** Материализованная проекция UTM zone 37N (SRID 32637) для метрических расчётов */
    @ToString.Exclude
    @Column(name = "geom_utm", columnDefinition = "geometry(Geometry,32637)")
    private Geometry geomUtm;
}