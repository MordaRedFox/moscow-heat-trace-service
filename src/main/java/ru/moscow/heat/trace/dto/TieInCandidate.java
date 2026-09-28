package ru.moscow.heat.trace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import java.util.Objects;

/**
 * Кандидат на присоединение новой сети к существующей
 */
@Schema(description = "Кандидат на присоединение новой сети к существующей")
public final class TieInCandidate {

    private static final GeometryFactory GF = new GeometryFactory();

    @Schema(description = "Уникальный идентификатор кандидата",
            example = "cand_oks_1_ch_42")
    private final String id;

    @Schema(description = "Идентификатор точки подключения ОКС",
            example = "oks_point_1")
    private final String connectionPointId;

    @Schema(description = "Идентификатор участка существующей сети",
            example = "heat_net_105")
    private final String heatNetworkId;

    @Schema(description = "Тип присоединения")
    private final TieInType type;

    @Schema(description = "Идентификатор существующей камеры (только для EXISTING_CHAMBER)",
            example = "heat_chamber_42")
    private final String existingChamberId;

    @Schema(description = "Долгота точки присоединения на сети в WGS 84 (EPSG:4326)",
            example = "37.6175")
    private final double tieInLongitude;

    @Schema(description = "Широта точки присоединения на сети в WGS 84 (EPSG:4326)",
            example = "55.7522")
    private final double tieInLatitude;

    @Schema(description = "Долгота целевой точки маршрута в WGS 84 (EPSG:4326)",
            example = "37.6176")
    private final double targetLongitude;

    @Schema(description = "Широта целевой точки маршрута в WGS 84 (EPSG:4326)",
            example = "55.7523")
    private final double targetLatitude;

    @Schema(description = "Расстояние от точки подключения до сети, м",
            example = "35.4")
    private final double distanceToNetworkM;

    @Schema(description = "Расстояние до существующей камеры, м (0 для NEW_CHAMBER)",
            example = "7.8")
    private final double distanceToChamberM;

    @Schema(description = "Текущее число примыканий к камере",
            example = "2")
    private final int currentAttachments;

    @Schema(description = "Ориентировочная стоимость присоединения, руб.",
            example = "5000000")
    private final long cost;

    @Schema(description = "ДУ новой камеры (только для NEW_CHAMBER), мм",
            example = "300")
    private final Integer newChamberDiameter;

    private TieInCandidate(Builder b) {
        this.id = b.id;
        this.connectionPointId = b.connectionPointId;
        this.heatNetworkId = b.heatNetworkId;
        this.type = b.type;
        this.existingChamberId = b.existingChamberId;
        this.tieInLongitude = b.tieInLongitude;
        this.tieInLatitude = b.tieInLatitude;
        this.targetLongitude = b.targetLongitude;
        this.targetLatitude = b.targetLatitude;
        this.distanceToNetworkM = b.distanceToNetworkM;
        this.distanceToChamberM = b.distanceToChamberM;
        this.currentAttachments = b.currentAttachments;
        this.cost = b.cost;
        this.newChamberDiameter = b.newChamberDiameter;
    }

    /** @return новый билдер кандидата */
    public static Builder builder() {
        return new Builder();
    }

    /** @return уникальный идентификатор кандидата */
    public String getId() {
        return id;
    }

    /** @return идентификатор точки подключения ОКС */
    public String getConnectionPointId() {
        return connectionPointId;
    }

    /** @return идентификатор участка сети в точке присоединения */
    public String getHeatNetworkId() {
        return heatNetworkId;
    }

    /** @return тип присоединения */
    public TieInType getType() {
        return type;
    }

    /** @return тип присоединения (синоним для обратной совместимости) */
    public TieInType getTieInType() {
        return type;
    }

    /** @return идентификатор существующей камеры либо {@code null} */
    public String getExistingChamberId() {
        return existingChamberId;
    }

    /** @return долгота точки присоединения на сети в WGS 84 */
    public double getTieInLongitude() {
        return tieInLongitude;
    }

    /** @return широта точки присоединения на сети в WGS 84 */
    public double getTieInLatitude() {
        return tieInLatitude;
    }

    /**
     * @return долгота целевой точки маршрута в WGS 84.
     *         Для {@link TieInType#EXISTING_CHAMBER} - координата
     *         камеры; для {@link TieInType#NEW_CHAMBER} - точка
     *         присоединения на сети
     */
    public double getTargetLongitude() {
        return targetLongitude;
    }

    /**
     * @return широта целевой точки маршрута в WGS 84. См.
     *         {@link #getTargetLongitude()}.
     */
    public double getTargetLatitude() {
        return targetLatitude;
    }

    /** @return геометрия точки присоединения на сети */
    public Point getTieInPoint() {
        return GF.createPoint(new Coordinate(tieInLongitude, tieInLatitude));
    }

    /** @return геометрия целевой точки маршрута */
    public Point getTargetPoint() {
        return GF.createPoint(new Coordinate(targetLongitude, targetLatitude));
    }

    /** @return расстояние от точки подключения до сети, м */
    public double getDistanceToNetworkM() {
        return distanceToNetworkM;
    }

    /** @return расстояние до камеры, м */
    public double getDistanceToChamberM() {
        return distanceToChamberM;
    }

    /** @return текущее число примыканий у камеры */
    public int getCurrentAttachments() {
        return currentAttachments;
    }

    /** @return текущее число примыканий у камеры (синоним для обратной совместимости) */
    public Integer getCurrentChamberConnections() {
        return currentAttachments;
    }

    /** @return стоимость присоединения, руб. */
    public long getCost() {
        return cost;
    }

    /** @return ДУ новой камеры либо {@code null} */
    public Integer getNewChamberDiameter() {
        return newChamberDiameter;
    }

    /** @return ДУ новой камеры (синоним для обратной совместимости) */
    public Integer getRequiredChamberDiameter() {
        return newChamberDiameter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TieInCandidate)) {
            return false;
        }
        TieInCandidate that = (TieInCandidate) o;
        return Double.compare(that.tieInLongitude, tieInLongitude) == 0
                && Double.compare(that.tieInLatitude, tieInLatitude) == 0
                && Double.compare(that.targetLongitude, targetLongitude) == 0
                && Double.compare(that.targetLatitude, targetLatitude) == 0
                && Double.compare(that.distanceToNetworkM, distanceToNetworkM) == 0
                && Double.compare(that.distanceToChamberM, distanceToChamberM) == 0
                && currentAttachments == that.currentAttachments
                && cost == that.cost
                && Objects.equals(id, that.id)
                && Objects.equals(connectionPointId, that.connectionPointId)
                && Objects.equals(heatNetworkId, that.heatNetworkId)
                && type == that.type
                && Objects.equals(existingChamberId, that.existingChamberId)
                && Objects.equals(newChamberDiameter, that.newChamberDiameter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, connectionPointId, heatNetworkId,
                type, existingChamberId, tieInLongitude, tieInLatitude,
                targetLongitude, targetLatitude, distanceToNetworkM,
                distanceToChamberM, currentAttachments, cost, newChamberDiameter);
    }

    @Override
    public String toString() {
        return "TieInCandidate{"
                + "id='" + id + '\''
                + ", connectionPointId='" + connectionPointId + '\''
                + ", heatNetworkId='" + heatNetworkId + '\''
                + ", type=" + type
                + ", existingChamberId='" + existingChamberId + '\''
                + ", tieInLongitude=" + tieInLongitude
                + ", tieInLatitude=" + tieInLatitude
                + ", targetLongitude=" + targetLongitude
                + ", targetLatitude=" + targetLatitude
                + ", distanceToNetworkM=" + distanceToNetworkM
                + ", distanceToChamberM=" + distanceToChamberM
                + ", currentAttachments=" + currentAttachments
                + ", cost=" + cost
                + ", newChamberDiameter=" + newChamberDiameter
                + '}';
    }

    /**
     * Билдер для {@link TieInCandidate}
     */
    public static final class Builder {

        private String id;
        private String connectionPointId;
        private String heatNetworkId;
        private TieInType type;
        private String existingChamberId;
        private double tieInLongitude;
        private double tieInLatitude;
        private double targetLongitude;
        private double targetLatitude;
        private double distanceToNetworkM;
        private double distanceToChamberM;
        private int currentAttachments;
        private long cost;
        private Integer newChamberDiameter;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder connectionPointId(String connectionPointId) {
            this.connectionPointId = connectionPointId;
            return this;
        }

        public Builder heatNetworkId(String heatNetworkId) {
            this.heatNetworkId = heatNetworkId;
            return this;
        }

        public Builder type(TieInType type) {
            this.type = type;
            return this;
        }

        public Builder tieInType(TieInType type) {
            this.type = type;
            return this;
        }

        public Builder existingChamberId(String existingChamberId) {
            this.existingChamberId = existingChamberId;
            return this;
        }

        public Builder tieInLongitude(double tieInLongitude) {
            this.tieInLongitude = tieInLongitude;
            return this;
        }

        public Builder tieInLatitude(double tieInLatitude) {
            this.tieInLatitude = tieInLatitude;
            return this;
        }

        public Builder tieInPoint(Point point) {
            if (point != null) {
                this.tieInLongitude = point.getX();
                this.tieInLatitude = point.getY();
            }
            return this;
        }

        public Builder targetLongitude(double targetLongitude) {
            this.targetLongitude = targetLongitude;
            return this;
        }

        public Builder targetLatitude(double targetLatitude) {
            this.targetLatitude = targetLatitude;
            return this;
        }

        public Builder targetPoint(Point point) {
            if (point != null) {
                this.targetLongitude = point.getX();
                this.targetLatitude = point.getY();
            }
            return this;
        }

        public Builder distanceToNetworkM(double distanceToNetworkM) {
            this.distanceToNetworkM = distanceToNetworkM;
            return this;
        }

        public Builder distanceToChamberM(double distanceToChamberM) {
            this.distanceToChamberM = distanceToChamberM;
            return this;
        }

        public Builder currentAttachments(int currentAttachments) {
            this.currentAttachments = currentAttachments;
            return this;
        }

        public Builder currentChamberConnections(Integer conns) {
            this.currentAttachments = conns != null ? conns : 0;
            return this;
        }

        public Builder cost(long cost) {
            this.cost = cost;
            return this;
        }

        public Builder cost(double cost) {
            this.cost = (long) cost;
            return this;
        }

        public Builder newChamberDiameter(Integer newChamberDiameter) {
            this.newChamberDiameter = newChamberDiameter;
            return this;
        }

        public Builder requiredChamberDiameter(Integer diam) {
            this.newChamberDiameter = diam;
            return this;
        }

        public TieInCandidate build() {
            return new TieInCandidate(this);
        }
    }
}
