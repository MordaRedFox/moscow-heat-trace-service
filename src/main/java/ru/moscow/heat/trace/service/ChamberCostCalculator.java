package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import ru.moscow.heat.trace.model.NewChamber;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Калькулятор стоимости строительства новых тепловых камер.
 * Заполняет стоимость камеры из нормативного справочника ChamberCostTable по ДУ.
 */
@Service
@RequiredArgsConstructor
public class ChamberCostCalculator {

    private final ChamberCostTable chamberCostTable;

    /**
     * Рассчитывает нормативную стоимость строительства новой камеры по ДУ.
     *
     * @param diameterMm условный диаметр в мм
     * @return стоимость в рублях
     */
    public BigDecimal calculateCost(int diameterMm) {
        return chamberCostTable.getCost(diameterMm);
    }

    /**
     * Создает экземпляр NewChamber с автоматически заполненным полем стоимости cost по ДУ.
     *
     * @param id         идентификатор камеры
     * @param geometry   геометрия (точка)
     * @param diameterMm условный диаметр камеры
     * @return NewChamber с заполненной стоимостью
     */
    public NewChamber createChamber(String id, Point geometry, int diameterMm) {
        Objects.requireNonNull(id, "Идентификатор камеры не может быть null");
        BigDecimal cost = calculateCost(diameterMm);
        return new NewChamber(id, geometry, diameterMm, cost);
    }
}
