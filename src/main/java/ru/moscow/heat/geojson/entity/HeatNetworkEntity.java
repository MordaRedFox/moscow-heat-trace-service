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
@Table(name = "heat_network")
public class HeatNetworkEntity extends AbstractGeoObject {

    @Column(name = "diameter")
    private Double diameter;

    @Column(name = "flow_tph")
    private Double flowTph;

    @Column(name = "upstream_object_id")
    private String upstreamObjectId;
}