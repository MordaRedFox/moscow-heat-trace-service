package ru.moscow.heat.geojson.entity;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * Пространственное ограничение: дороги, газопроводы, кабели,
 * парки и т.п. Конкретный тип определяется атрибутом {@code restriction_type}
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "restriction",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_restriction_upload_feature",
                columnNames = {"upload_id", "feature_id"}))
public class RestrictionEntity extends AbstractGeoObject {

    /** Тип ограничения (road, gas_pipeline, park и т.д.) */
    @Column(name = "restriction_type")
    private String restrictionType;
}
