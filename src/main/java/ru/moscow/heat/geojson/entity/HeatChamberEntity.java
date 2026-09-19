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
@Table(name = "heat_chamber")
public class HeatChamberEntity extends AbstractGeoObject {
    private Double diameter;
    @Column(name = "upstream_object_id") private String upstreamObjectId;
}
