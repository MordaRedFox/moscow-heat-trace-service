package ru.moscow.heat.geojson.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.locationtech.jts.geom.Geometry;

import javax.persistence.*;
import java.util.UUID;

/**
 * Базовая сущность для объектов GeoJSON, разложенных по
 * типизированным таблицам ({@code source}, {@code heat_network},
 * {@code heat_chamber} и т.д.)
 */
@Getter
@Setter
@MappedSuperclass
@TypeDef(name = "jsonb", typeClass = JsonNodeBinaryType.class)
public abstract class AbstractGeoObject {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator = "geo_object_seq")
    @SequenceGenerator(
            name = "geo_object_seq",
            sequenceName = "geo_object_seq",
            allocationSize = 50)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * Идентификатор загрузки, к которой относится объект.
     * Вместе с {@code featureId} образует составной ключ уникальности
     */
    @Column(name = "upload_id", nullable = false)
    private UUID uploadId;

    /** Идентификатор объекта внутри исходного GeoJSON */
    @Column(name = "feature_id", nullable = false)
    private String featureId;

    /** Геометрия в EPSG:4326 (WGS 84) - как в исходном GeoJSON */
    @Column(name = "geometry", nullable = false,
            columnDefinition = "geometry(Geometry,4326)")
    private Geometry geometry;

    /** Геометрия в EPSG:32637 (UTM zone 37N) для метрических расчетов */
    @Column(name = "geometry_utm", nullable = false,
            columnDefinition = "geometry(Geometry,32637)")
    private Geometry geometryUtm;

    /** Сырые properties исходного GeoJSON */
    @Column(name = "properties", nullable = false,
            columnDefinition = "jsonb")
    @Type(type = "jsonb")
    private JsonNode properties;
}
