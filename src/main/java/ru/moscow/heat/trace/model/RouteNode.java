package ru.moscow.heat.trace.model;

import org.locationtech.jts.geom.Coordinate;

import java.util.Objects;
import java.util.UUID;

/**
 * Узел маршрута новой тепловой сети.
 * <p>
 * Координата — UTM zone 37N (EPSG:32637), метры, как и {@code RouteSegment.geometryUtm}
 * и весь граф видимости ({@code AbstractGeoObject.geometryUtm} уже хранит
 * метрическую геометрию, поэтому внутренний конвейер трассировки работает
 * в UTM целиком; конвертация в WGS84 для GeoJSON-выгрузки — задача итерации 6-7).
 * <p>
 * Иммутабельный DTO — по аналогии с {@code TieInCandidate} из итерации 4.
 */
public final class RouteNode {

    private final UUID id;
    private final RouteNodeType type;
    private final Coordinate coordinateUtm;

    /**
     * feature_id исходного доменного объекта, если узел ему соответствует
     * (например, connection_point_id ОКС или feature_id существующей
     * камеры). {@code null} для чисто геометрических узлов (CORNER,
     * TECHNICAL_NODE, NEW_CHAMBER до сохранения в БД).
     */
    private final String sourceFeatureId;

    public RouteNode(UUID id, RouteNodeType type, Coordinate coordinateUtm, String sourceFeatureId) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.coordinateUtm = Objects.requireNonNull(coordinateUtm, "coordinateUtm");
        this.sourceFeatureId = sourceFeatureId;
    }

    public UUID getId() {
        return id;
    }

    public RouteNodeType getType() {
        return type;
    }

    public Coordinate getCoordinateUtm() {
        return coordinateUtm;
    }

    public String getSourceFeatureId() {
        return sourceFeatureId;
    }

    @Override
    public String toString() {
        return "RouteNode{" +
                "id=" + id +
                ", type=" + type +
                ", coordinateUtm=" + coordinateUtm +
                '}';
    }
}
