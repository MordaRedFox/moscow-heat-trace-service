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
 * не формируется: присоединение всегда идет через тепловую камеру.
 * <p>Класс immutable. Сознательно реализован без Lombok, чтобы
 * не зависеть от обработки аннотаций в IDE и на этапе javac
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
        this.distanceToNetworkM = b.distanceToNetworkM;
        this.distanceToChamberM = b.distanceToChamberM;
        this.currentAttachments = b.currentAttachments;
        this.cost = b.cost;
        this.newChamberDiameter = b.newChamberDiameter;
    }

    /**
     * Создает новый билдер кандидата
     * @return билдер
     */
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

    /** @return долгота точки присоединения в WGS 84 */
    public double getTieInLongitude() {
        return tieInLongitude;
    }

    /** @return широта точки присоединения в WGS 84 */
    public double getTieInLatitude() {
        return tieInLatitude;
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
                && Double.compare(that.distanceToNetworkM,
                        distanceToNetworkM) == 0
                && Double.compare(that.distanceToChamberM,
                        distanceToChamberM) == 0
                && currentAttachments == that.currentAttachments
                && cost == that.cost
                && Objects.equals(id, that.id)
                && Objects.equals(connectionPointId,
                        that.connectionPointId)
                && Objects.equals(heatNetworkId, that.heatNetworkId)
                && type == that.type
                && Objects.equals(existingChamberId,
                        that.existingChamberId)
                && Objects.equals(newChamberDiameter,
                        that.newChamberDiameter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, connectionPointId, heatNetworkId,
                type, existingChamberId, tieInLongitude,
                tieInLatitude, distanceToNetworkM,
                distanceToChamberM, currentAttachments, cost,
                newChamberDiameter);
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
