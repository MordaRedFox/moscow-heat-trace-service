package ru.moscow.heat.geojson.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import ru.moscow.heat.geojson.ObjectType;

import javax.persistence.*;

@Getter
@Setter
@Builder
@Entity
@Table(name = "geo_feature")
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
            allocationSize = 50)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "feature_id", nullable = false, unique = true)
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
