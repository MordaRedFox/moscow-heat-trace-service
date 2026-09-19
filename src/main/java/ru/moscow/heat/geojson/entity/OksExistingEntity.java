package ru.moscow.heat.geojson.entity;

import lombok.*;
import javax.persistence.Entity;
import javax.persistence.Table;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "oks_existing")
public class OksExistingEntity extends AbstractGeoObject { }