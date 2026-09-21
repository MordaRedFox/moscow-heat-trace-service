package ru.moscow.heat.geojson.entity;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * Существующий участок тепловой сети. По актуальной модели
 * существующая сеть не реконструируется, расход и цепочка к
 * источнику не передаются - хранится только условный диаметр
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "heat_network",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_heat_network_upload_feature",
                columnNames = {"upload_id", "feature_id"}))
public class HeatNetworkEntity extends AbstractGeoObject {

    /** Условный диаметр, мм. */
    @Column(name = "diameter")
    private Integer diameter;
}
