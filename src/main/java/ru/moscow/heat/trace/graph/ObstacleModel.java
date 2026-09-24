package ru.moscow.heat.trace.graph;

import org.locationtech.jts.geom.Geometry;

import java.util.List;
import java.util.Objects;

/**
 * Подготовленная модель препятствий для конкретной загрузки (uploadId).
 * Строится один раз перед трассировкой всех ОКС (план, шаг 3).
 * <p>
 * Отступ у всех препятствий взят по максимальному ДУ (см. риск R3 плана:
 * "MVP: брать максимальный отступ"), поэтому модель не зависит от ДУ
 * конкретного маршрута и переиспользуется для всех ОКС загрузки.
 */
public final class ObstacleModel {

    /**
     * Зона специального прохода: раздутая геометрия + коэффициент Kспец
     * + тип ограничения (для диагностики/логов).
     */
    public static final class SpecialZone {
        private final Geometry bufferedGeometryUtm;
        private final double kspets;
        private final String restrictionType;

        public SpecialZone(Geometry bufferedGeometryUtm, double kspets, String restrictionType) {
            this.bufferedGeometryUtm = Objects.requireNonNull(bufferedGeometryUtm, "bufferedGeometryUtm");
            this.kspets = kspets;
            this.restrictionType = restrictionType;
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
    }

    /** Раздутые полигоны запретных зон (FORBIDDEN) — внутрь заходить нельзя вообще. */
    private final List<Geometry> forbiddenBufferedGeometriesUtm;

    /** Раздутые зоны спецпрохода (SPECIAL) — внутрь заходить можно, но с Kспец. */
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
