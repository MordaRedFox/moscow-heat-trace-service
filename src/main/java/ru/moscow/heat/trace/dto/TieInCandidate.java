package ru.moscow.heat.trace.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.locationtech.jts.geom.Point;

import java.util.Locale;
import java.util.Objects;

/**
 * Неизменяемый объект кандидата на присоединение перспективного ОКС к тепловой сети.
 * Содержит полную информацию о точке врезки, выбранном типе присоединения
 * (существующая камера или строительство новой), стоимости и параметрах подключения.
 */
@Getter
@EqualsAndHashCode
@Builder
public class TieInCandidate {

    /** Идентификатор точки подключения ОКС (feature_id из oks_connection_point) */
    private final String connectionPointId;

    /** Идентификатор существующего участка тепловой сети (feature_id из heat_network) */
    private final String heatNetworkId;

    /** Тип точки присоединения: существующая камера или новая камера */
    private final TieInType tieInType;

    /**
     * Идентификатор существующей камеры (feature_id из heat_chamber).
     * Заполняется только для {@link TieInType#EXISTING_CHAMBER}, для новой камеры равен {@code null}.
     */
    private final String existingChamberId;

    /** Точка присоединения в координатах WGS 84 (EPSG:4326) */
    private final Point tieInPoint;

    /**
     * Расстояние в метрах (в проекции UTM zone 37N) от точки на сети до камеры.
     * Для {@link TieInType#NEW_CHAMBER} строго равно 0.0.
     */
    private final double distanceToChamberM;

    /**
     * Текущее количество примыканий участков сети к существующей камере.
     * Заполняется для {@link TieInType#EXISTING_CHAMBER}, для новой камеры равно {@code null}.
     */
    private final Integer currentChamberConnections;

    /** Стоимость кандидата в рублях по шкале нормативов ТП */
    private final double cost;

    /**
     * Расчетный условный диаметр камеры в мм по наибольшему ДУ примыкающих участков.
     * Заполняется для {@link TieInType#NEW_CHAMBER}, для существующей камеры равен {@code null}.
     */
    private final Integer requiredChamberDiameter;

    public TieInCandidate(
            String connectionPointId,
            String heatNetworkId,
            TieInType tieInType,
            String existingChamberId,
            Point tieInPoint,
            double distanceToChamberM,
            Integer currentChamberConnections,
            double cost,
            Integer requiredChamberDiameter) {
        this.connectionPointId = Objects.requireNonNull(connectionPointId, "connectionPointId must not be null");
        this.heatNetworkId = Objects.requireNonNull(heatNetworkId, "heatNetworkId must not be null");
        this.tieInType = Objects.requireNonNull(tieInType, "tieInType must not be null");
        this.existingChamberId = existingChamberId;
        this.tieInPoint = Objects.requireNonNull(tieInPoint, "tieInPoint must not be null");
        this.distanceToChamberM = distanceToChamberM;
        this.currentChamberConnections = currentChamberConnections;
        this.cost = cost;
        this.requiredChamberDiameter = requiredChamberDiameter;
    }

    @Override
    public String toString() {
        return "TieInCandidate{" +
                "point='" + connectionPointId + '\'' +
                ", network='" + heatNetworkId + '\'' +
                ", type=" + tieInType +
                (existingChamberId != null ? ", chamber='" + existingChamberId + '\'' : "") +
                ", cost=" + String.format(Locale.ROOT, "%.0f", cost) +
                ", dist=" + String.format(Locale.ROOT, "%.2f", distanceToChamberM) +
                (currentChamberConnections != null ? ", conns=" + currentChamberConnections : "") +
                (requiredChamberDiameter != null ? ", reqDN=" + requiredChamberDiameter : "") +
                '}';
    }
}
