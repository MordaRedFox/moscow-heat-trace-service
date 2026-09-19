package ru.moscow.heat.geojson.entity;

import lombok.*;
import javax.persistence.*;
import javax.persistence.Entity;
import javax.persistence.Table;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "oks_future")
public class OksFutureEntity extends AbstractGeoObject {
    @Column(name = "flow_tph")  private Double flowTph;
    @Column(name = "heat_load") private Double heatLoad;
}
