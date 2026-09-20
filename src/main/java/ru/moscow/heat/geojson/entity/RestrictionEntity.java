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
@Table(name = "restriction")
public class RestrictionEntity extends AbstractGeoObject {
    @Column(name = "restriction_type") private String restrictionType;
}
