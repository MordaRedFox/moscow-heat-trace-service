package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import ru.moscow.heat.spatial.ChamberCostTable;
import ru.moscow.heat.trace.model.NewChamber;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChamberCostCalculatorTest {

    private final ChamberCostTable chamberCostTable = new ChamberCostTable();
    private final ChamberCostCalculator calculator = new ChamberCostCalculator(chamberCostTable);
    private final GeometryFactory gf = new GeometryFactory();

    @Test
    @DisplayName("Стоимость камеры для диапазона 50..200 мм составляет 3 000 000 руб")
    void costForSmallDiameters() {
        assertThat(calculator.calculateCost(50)).isEqualByComparingTo(BigDecimal.valueOf(3_000_000));
        assertThat(calculator.calculateCost(100)).isEqualByComparingTo(BigDecimal.valueOf(3_000_000));
        assertThat(calculator.calculateCost(200)).isEqualByComparingTo(BigDecimal.valueOf(3_000_000));
    }

    @Test
    @DisplayName("Стоимость камеры для диапазона 250..500 мм составляет 5 000 000 руб")
    void costForMediumDiameters() {
        assertThat(calculator.calculateCost(250)).isEqualByComparingTo(BigDecimal.valueOf(5_000_000));
        assertThat(calculator.calculateCost(400)).isEqualByComparingTo(BigDecimal.valueOf(5_000_000));
        assertThat(calculator.calculateCost(500)).isEqualByComparingTo(BigDecimal.valueOf(5_000_000));
    }

    @Test
    @DisplayName("Стоимость камеры для диапазона 600..1000 мм составляет 8 000 000 руб")
    void costForLargeDiameters() {
        assertThat(calculator.calculateCost(600)).isEqualByComparingTo(BigDecimal.valueOf(8_000_000));
        assertThat(calculator.calculateCost(800)).isEqualByComparingTo(BigDecimal.valueOf(8_000_000));
        assertThat(calculator.calculateCost(1000)).isEqualByComparingTo(BigDecimal.valueOf(8_000_000));
    }

    @Test
    @DisplayName("Стоимость камеры для диапазона 1200..1400 мм составляет 12 000 000 руб")
    void costForExtraLargeDiameters() {
        assertThat(calculator.calculateCost(1200)).isEqualByComparingTo(BigDecimal.valueOf(12_000_000));
        assertThat(calculator.calculateCost(1400)).isEqualByComparingTo(BigDecimal.valueOf(12_000_000));
    }

    @Test
    @DisplayName("Диаметры вне допустимого диапазона вызывают исключение")
    void invalidDiametersThrowException() {
        assertThatThrownBy(() -> calculator.calculateCost(40))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.calculateCost(1500))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Создание NewChamber автоматически заполняет расчетную стоимость")
    void createChamberPopulatesCost() {
        Point point = gf.createPoint(new Coordinate(100.0, 200.0));
        NewChamber chamber = calculator.createChamber("ch-1", point, 300);

        assertThat(chamber.getId()).isEqualTo("ch-1");
        assertThat(chamber.getGeometry()).isEqualTo(point);
        assertThat(chamber.getDiameterMm()).isEqualTo(300);
        assertThat(chamber.getCost()).isEqualByComparingTo(BigDecimal.valueOf(5_000_000));
    }
}
