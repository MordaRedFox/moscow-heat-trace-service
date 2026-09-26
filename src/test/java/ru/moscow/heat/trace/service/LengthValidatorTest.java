package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import ru.moscow.heat.spatial.DiameterTable;
import ru.moscow.heat.trace.model.LayingMethod;
import ru.moscow.heat.trace.model.RouteNode;
import ru.moscow.heat.trace.model.RouteNodeType;
import ru.moscow.heat.trace.model.RouteSegment;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты {@link LengthValidator}.
 * <p>
 * Проверяется правило ТП п. 2.4: суммарная длина непрерывной части сети
 * одного ДУ не должна превышать {@code maxLengthM} для этого ДУ. Группа
 * прерывается сменой ДУ; камера или технический узел без смены ДУ новую
 * группу не начинают
 */
@DisplayName("Unit-тесты LengthValidator")
class LengthValidatorTest {

    private static final GeometryFactory GF = new GeometryFactory();

    private LengthValidator validator;

    @BeforeEach
    void setUp() {
        validator = new LengthValidator(new DiameterTable());
    }

    @Test
    @DisplayName("Пустой список — valid")
    void emptyList_Valid() {
        LengthValidator.ValidationResult res = validator.validate(
                Collections.emptyList());
        assertThat(res.isValid()).isTrue();
    }

    @Test
    @DisplayName("Длина в пределах ДУ 100 (419 м) — valid")
    void lengthWithinLimit_Valid() {
        List<RouteSegment> segments = List.of(
                segment(0, 200, 200.0, 100),
                segment(200, 400, 200.0, 100));

        LengthValidator.ValidationResult res = validator.validate(segments);
        assertThat(res.isValid()).isTrue();
    }

    @Test
    @DisplayName("Длина превышает предел ДУ 100 — invalid")
    void lengthExceeded_Invalid() {
        List<RouteSegment> segments = List.of(
                segment(0, 250, 250.0, 100),
                segment(250, 500, 250.0, 100));

        LengthValidator.ValidationResult res = validator.validate(segments);
        assertThat(res.isValid()).isFalse();
        assertThat(res.getViolationDetails()).contains("100");
        assertThat(res.getViolationDetails()).contains("Превышена");
    }

    @Test
    @DisplayName("Смена ДУ сбрасывает накопленную длину")
    void diameterChangeResetsAccumulator() {
        List<RouteSegment> segments = List.of(
                segment(0, 400, 400.0, 100),
                segment(400, 900, 500.0, 200));

        LengthValidator.ValidationResult res = validator.validate(segments);
        assertThat(res.isValid()).isTrue();
    }

    @Test
    @DisplayName("Неизвестный ДУ (нет в таблице) — invalid")
    void unknownDiameter_Invalid() {
        List<RouteSegment> segments = List.of(
                segment(0, 100, 100.0, 999));

        LengthValidator.ValidationResult res = validator.validate(segments);
        assertThat(res.isValid()).isFalse();
        assertThat(res.getViolationDetails()).contains("999");
    }

    private RouteSegment segment(double x0, double x1,
                                  double lengthM, int diameterMm) {
        RouteNode from = new RouteNode(UUID.randomUUID(),
                RouteNodeType.CORNER, new Coordinate(x0, 0), null);
        RouteNode to = new RouteNode(UUID.randomUUID(),
                RouteNodeType.CORNER, new Coordinate(x1, 0), null);
        LineString geom = GF.createLineString(new Coordinate[]{
                new Coordinate(x0, 0), new Coordinate(x1, 0)
        });
        return new RouteSegment(UUID.randomUUID(), from, to, geom,
                BigDecimal.valueOf(10.0), diameterMm,
                LayingMethod.BASE, 1.0, lengthM, null);
    }
}
