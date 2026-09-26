package ru.moscow.heat.trace.graph;

import org.locationtech.jts.geom.Geometry;

import java.util.List;
import java.util.Objects;

/**
 * Подготовленная модель препятствий для конкретной загрузки (uploadId).
 * Строится один раз перед трассировкой всех ОКС (план, шаг 3).
 * <p>
 * Все геометрии — в UTM zone 37N (EPSG:32637), метры. Строятся напрямую
 * из {@code AbstractGeoObject.getGeometryUtm()} (колонка {@code geometry_utm}
 * уже посчитана при загрузке — см. {@code ObstacleModelBuilder}), поэтому
 * ни здесь, ни в {@link VisibilityGraph} преобразование координат не
 * требуется: все проверки пересечения/расстояния — обычный JTS в
 * метрической плоскости.
 * <p>
 * Отступ у всех препятствий взят по максимальному ДУ (риск R3 плана:
 * "MVP: брать максимальный отступ"), поэтому модель не зависит от ДУ
 * конкретного маршрута и переиспользуется для всех ОКС загрузки.
 */
public final class ObstacleModel {

    /**
     * Зона специального прохода: раздутая геометрия (UTM) + Kспец
     * + тип ограничения (для диагностики/логов) + требование угла
     * пересечения (для road/tram_tracks, иначе null).
     */
    public static final class SpecialZone {
        private final Geometry bufferedGeometryUtm;
        private final double kspets;
        private final String restrictionType;
        private final Double minCrossingAngleDeg;

        public SpecialZone(Geometry bufferedGeometryUtm, double kspets,
                            String restrictionType, Double minCrossingAngleDeg) {
            this.bufferedGeometryUtm = Objects.requireNonNull(bufferedGeometryUtm, "bufferedGeometryUtm");
            this.kspets = kspets;
            this.restrictionType = restrictionType;
            this.minCrossingAngleDeg = minCrossingAngleDeg;
        }

        public Geometry getBufferedGeometryUtm() {
            return bufferedGeometryUtm;
        }

        public double getKspets() {
            return kspets;
        }

        public String getRestrictionType() {
            return restrictionType;
        }

        /** Минимальный угол пересечения, град., или {@code null}, если не нормируется. */
        public Double getMinCrossingAngleDeg() {
            return minCrossingAngleDeg;
        }
    }

    /** Раздутые полигоны запретных зон (FORBIDDEN), UTM — внутрь заходить нельзя вообще. */
    private final List<Geometry> forbiddenBufferedGeometriesUtm;

    /** Раздутые зоны спецпрохода (SPECIAL_CROSSING), UTM — внутрь заходить можно, но с Kспец. */
    private final List<SpecialZone> specialZones;

    public ObstacleModel(List<Geometry> forbiddenBufferedGeometriesUtm, List<SpecialZone> specialZones) {
        this.forbiddenBufferedGeometriesUtm = Objects.requireNonNull(forbiddenBufferedGeometriesUtm, "forbiddenBufferedGeometriesUtm");
        this.specialZones = Objects.requireNonNull(specialZones, "specialZones");
    }

    public List<Geometry> getForbiddenBufferedGeometriesUtm() {
        return forbiddenBufferedGeometriesUtm;
    }

    public List<SpecialZone> getSpecialZones() {
        return specialZones;
    }
}
