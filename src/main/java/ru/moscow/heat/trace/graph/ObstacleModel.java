package ru.moscow.heat.trace.graph;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.locationtech.jts.index.strtree.STRtree;

import java.util.List;
import java.util.Objects;

/**
 * Подготовленная модель препятствий для конкретной загрузки (uploadId)
 * <p>Все геометрии - в UTM zone 37N (EPSG:32637), метры
 * <p>Каждая зона кэширует envelope и PreparedGeometry. Дополнительно
 * модель строит два {@link STRtree}-индекса (R-tree) по envelope'ам
 * FORBIDDEN и SPECIAL зон. Это критично для производительности: без
 * индексов проверка одного сегмента перебирала бы все ~90 зон, что на
 * десятках миллионов пар давало минуты и часы. С STRtree на каждый
 * сегмент находятся только 3-5 реально близких зон
 */
public final class ObstacleModel {

    /**
     * Запретная зона: буфер + id restriction + envelope + prepared-геометрия
     */
    public static final class ForbiddenZone {
        private final Long sourceRestrictionId;
        private final Geometry bufferedGeometryUtm;
        private final Geometry sourceGeometryUtm;
        private final Envelope envelope;
        private final PreparedGeometry preparedGeometry;
        private final PreparedGeometry preparedSourceGeometry;

        public ForbiddenZone(Long sourceRestrictionId, Geometry bufferedGeometryUtm) {
            this(sourceRestrictionId, bufferedGeometryUtm, null);
        }

        public ForbiddenZone(Long sourceRestrictionId, Geometry bufferedGeometryUtm,
                             Geometry sourceGeometryUtm) {
            this.sourceRestrictionId = sourceRestrictionId;
            this.bufferedGeometryUtm = Objects.requireNonNull(
                    bufferedGeometryUtm, "bufferedGeometryUtm");
            this.sourceGeometryUtm = sourceGeometryUtm;
            this.envelope = bufferedGeometryUtm.getEnvelopeInternal();
            this.preparedGeometry = PreparedGeometryFactory
                    .prepare(bufferedGeometryUtm);
            this.preparedSourceGeometry = sourceGeometryUtm != null
                    ? PreparedGeometryFactory.prepare(sourceGeometryUtm)
                    : null;
        }

        public Long getSourceRestrictionId() {
            return sourceRestrictionId;
        }

        public Geometry getBufferedGeometryUtm() {
            return bufferedGeometryUtm;
        }

        public Geometry getSourceGeometryUtm() {
            return sourceGeometryUtm;
        }

        public Envelope getEnvelope() {
            return envelope;
        }

        public PreparedGeometry getPreparedGeometry() {
            return preparedGeometry;
        }

        public PreparedGeometry getPreparedSourceGeometry() {
            return preparedSourceGeometry;
        }
    }

    /**
     * Зона специального прохода: буфер + исходная геометрия + envelope +
     * prepared-геометрия + Kспец + тип + требование угла
     */
    public static final class SpecialZone {
        private final Geometry bufferedGeometryUtm;
        private final Geometry sourceGeometryUtm;
        private final Envelope envelope;
        private final PreparedGeometry preparedGeometry;
        private final double kspets;
        private final String restrictionType;
        private final Double minCrossingAngleDeg;

        public SpecialZone(Geometry bufferedGeometryUtm,
                            Geometry sourceGeometryUtm,
                            double kspets,
                            String restrictionType,
                            Double minCrossingAngleDeg) {
            this.bufferedGeometryUtm = Objects.requireNonNull(
                    bufferedGeometryUtm, "bufferedGeometryUtm");
            this.sourceGeometryUtm = Objects.requireNonNull(
                    sourceGeometryUtm, "sourceGeometryUtm");
            this.envelope = bufferedGeometryUtm.getEnvelopeInternal();
            this.preparedGeometry = PreparedGeometryFactory
                    .prepare(bufferedGeometryUtm);
            this.kspets = kspets;
            this.restrictionType = restrictionType;
            this.minCrossingAngleDeg = minCrossingAngleDeg;
        }

        public Geometry getBufferedGeometryUtm() {
            return bufferedGeometryUtm;
        }

        public Geometry getSourceGeometryUtm() {
            return sourceGeometryUtm;
        }

        public Envelope getEnvelope() {
            return envelope;
        }

        public PreparedGeometry getPreparedGeometry() {
            return preparedGeometry;
        }

        public double getKspets() {
            return kspets;
        }

        public String getRestrictionType() {
            return restrictionType;
        }

        public Double getMinCrossingAngleDeg() {
            return minCrossingAngleDeg;
        }
    }

    private final List<ForbiddenZone> forbiddenZones;
    private final List<SpecialZone> specialZones;

    /** R-tree по envelope'ам FORBIDDEN-зон — быстрый поиск близких к сегменту */
    private final STRtree forbiddenIndex;

    /** R-tree по envelope'ам SPECIAL-зон */
    private final STRtree specialIndex;

    public ObstacleModel(List<ForbiddenZone> forbiddenZones,
                          List<SpecialZone> specialZones) {
        this.forbiddenZones = Objects.requireNonNull(
                forbiddenZones, "forbiddenZones");
        this.specialZones = Objects.requireNonNull(
                specialZones, "specialZones");

        this.forbiddenIndex = new STRtree();
        for (ForbiddenZone z : forbiddenZones) {
            forbiddenIndex.insert(z.getEnvelope(), z);
        }
        forbiddenIndex.build();

        this.specialIndex = new STRtree();
        for (SpecialZone z : specialZones) {
            specialIndex.insert(z.getEnvelope(), z);
        }
        specialIndex.build();
    }

    public List<ForbiddenZone> getForbiddenZones() {
        return forbiddenZones;
    }

    public List<SpecialZone> getSpecialZones() {
        return specialZones;
    }

    /**
     * Возвращает FORBIDDEN-зоны, чьи envelope пересекаются с заданным.
     * Через R-tree это O(log n) вместо O(n)
     */
    @SuppressWarnings("unchecked")
    public List<ForbiddenZone> findForbiddenNear(Envelope segmentEnvelope) {
        return (List<ForbiddenZone>) (List<?>) forbiddenIndex
                .query(segmentEnvelope);
    }

    /**
     * Возвращает SPECIAL-зоны, чьи envelope пересекаются с заданным
     */
    @SuppressWarnings("unchecked")
    public List<SpecialZone> findSpecialNear(Envelope segmentEnvelope) {
        return (List<SpecialZone>) (List<?>) specialIndex
                .query(segmentEnvelope);
    }
}
