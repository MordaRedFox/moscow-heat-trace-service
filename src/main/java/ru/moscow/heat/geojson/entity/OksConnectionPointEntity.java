package ru.moscow.heat.geojson.entity;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * Точка подключения перспективного ОКС. Каждая точка -
 * самостоятельная цель подключения с собственным расчетным
 * расходом. Связь с полигоном ОКС отдельным идентификатором
 * не задается: полигон передается как {@code restriction_type = oks}
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "oks_connection_point",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_oks_connection_point_upload_feature",
                columnNames = {"upload_id", "feature_id"}))
public class OksConnectionPointEntity extends AbstractGeoObject {

    /** Расчетный расход, т/ч. */
    @Column(name = "flow_tph")
    private Double flowTph;
}
