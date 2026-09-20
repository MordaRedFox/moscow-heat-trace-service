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
@Table(name = "oks_connection_point")
public class OksConnectionPointEntity extends AbstractGeoObject {
    @Column(name = "oks_id") private String oksId;
}