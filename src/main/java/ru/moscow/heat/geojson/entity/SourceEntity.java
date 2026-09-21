package ru.moscow.heat.geojson.entity;

import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * Существующий источник теплоснабжения. Уникальность задается парой
 * ({@code upload_id}, {@code feature_id}) - данные разных загрузок изолированы
 */
@Getter
@Setter
@NoArgsConstructor
@Builder
@Entity
@Table(
        name = "source",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_source_upload_feature",
                columnNames = {"upload_id", "feature_id"}))
public class SourceEntity extends AbstractGeoObject {
}
