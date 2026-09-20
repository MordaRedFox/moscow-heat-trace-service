package ru.moscow.heat.geojson.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.locationtech.jts.geom.Geometry;

import javax.persistence.*;

@Getter
@Setter
@MappedSuperclass
@TypeDef(name = "jsonb", typeClass = JsonNodeBinaryType.class)
public abstract class AbstractGeoObject {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "geo_object_seq")
    @SequenceGenerator(
            name = "geo_object_seq",
            sequenceName = "geo_object_seq",
            allocationSize = 50)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "feature_id", nullable = false, unique = true)
    private String featureId;

    /** Геометрия в EPSG:4326 (WGS84) — как в исходном GeoJSON. */
    @Column(name = "geometry", nullable = false, columnDefinition = "geometry")
    private Geometry geometry;

    /** Геометрия в EPSG:32637 (UTM zone 37N, метры) — для расчётов. */
    @Column(name = "geometry_utm", nullable = false, columnDefinition = "geometry")
    private Geometry geometryUtm;

    @Column(name = "properties", nullable = false, columnDefinition = "jsonb")
    @Type(type = "jsonb")
    private JsonNode properties;
}