package ru.moscow.heat.geojson.entity;

import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * Существующая тепловая камера. По актуальной модели в камере
 * передаются только id, object_type и геометрия - условный диаметр
 * не задается (он вычисляется при проектировании новой сети как
 * максимум по примыкающим участкам), цепочка к источнику отсутствует
 */
@Getter
@Setter
@NoArgsConstructor
@Builder
@Entity
@Table(
        name = "heat_chamber",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_heat_chamber_upload_feature",
                columnNames = {"upload_id", "feature_id"}))
public class HeatChamberEntity extends AbstractGeoObject {
}
