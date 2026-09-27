package ru.moscow.heat.trace.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

/**
 * Кандидат на присоединение новой тепловой сети к существующей
 * для одной точки подключения ОКС
 * <p>Кандидат описывает либо врезку в существующую тепловую камеру
 * ({@link TieInType#EXISTING_CHAMBER}), либо строительство новой
 * камеры на выбранном участке сети
 * ({@link TieInType#NEW_CHAMBER}). Отдельный объект {@code tie_in}
 * не формируется: присоединение всегда идёт через тепловую камеру
 * <p>Координаты кандидата разделены на два смысловых поля:
 * <ul>
 *     <li>{@code tieInLongitude}/{@code tieInLatitude} — точка
 *     присоединения <b>на существующей сети</b>, результат
 *     {@code GeometryUtils.nearestPointOnGeometry}. Для отладки
 *     и для расчёта расстояний;</li>
 *     <li>{@code targetLongitude}/{@code targetLatitude} — фактическая
 *     конечная точка маршрута. Для {@link TieInType#EXISTING_CHAMBER}
 *     это координаты <b>камеры</b> (ТП, п. 2.4: «Новый участок сети
 *     заканчивается в ней»), для {@link TieInType#NEW_CHAMBER} —
 *     точка на сети, где создаётся новая камера.</li>
 * </ul>
 * Именно {@code targetXxx} используется {@code TraceOrchestrator}
 * как {@code endUtm} при поиске пути
 */
@Schema(description = "Кандидат на присоединение")
public final class TieInCandidate {

    private final String id;
    private final String connectionPointId;
    private final String heatNetworkId;
    private final TieInType type;
    private final String existingChamberId;
    private final double tieInLongitude;
    private final double tieInLatitude;
    private final double targetLongitude;
    private final double targetLatitude;
    private final double distanceToNetworkM;
    private final double distanceToChamberM;
    private final int currentAttachments;
    private final long cost;
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

    /** @return стоимость присоединения, руб. */
    public long getCost() {
        return cost;
    }

    /** @return ДУ новой камеры либо {@code null} */
    public Integer getNewChamberDiameter() {
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

        public Builder targetLongitude(double targetLongitude) {
            this.targetLongitude = targetLongitude;
            return this;
        }

        public Builder targetLatitude(double targetLatitude) {
            this.targetLatitude = targetLatitude;
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

        public Builder cost(long cost) {
            this.cost = cost;
            return this;
        }

        public Builder newChamberDiameter(Integer newChamberDiameter) {
            this.newChamberDiameter = newChamberDiameter;
            return this;
        }

        public TieInCandidate build() {
            return new TieInCandidate(this);
        }
    }
}
