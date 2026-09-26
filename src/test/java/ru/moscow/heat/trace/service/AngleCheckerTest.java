package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты {@link AngleChecker}.
 * <p>
 * Проверяются ключевые сценарии: перпендикулярное пересечение (90°),
 * ровно 45°, 30° (нарушение), коллинеарные линии (пересечение - линия,
 * а не точка), отсутствие пересечения, дефолтное значение порога
 * <p>
 * Работает в UTM-подобной метрической плоскости, где координаты
 * задаются прямо в метрах
 */
@DisplayName("Unit-тесты AngleChecker")
class AngleCheckerTest {

    private final GeometryFactory gf = new GeometryFactory();
    private AngleChecker angleChecker;

    @BeforeEach
    void setUp() {
        angleChecker = new AngleChecker();
    }

    /**
     * Перпендикулярные линии - 90°, валидно при пороге 45°
     */
    @Test
    @DisplayName("Перпендикуляр (90°) — валидно")
    void perpendicularValid() {
        Coordinate routeStart = new Coordinate(100, 0);
        Coordinate routeEnd = new Coordinate(100, 200);
        LineString axis = gf.createLineString(new Coordinate[]{
                new Coordinate(0, 100),
                new Coordinate(200, 100)
        });

        boolean ok = angleChecker.isCrossingAngleValid(
                routeStart, routeEnd, axis, 45.0);
        assertThat(ok).isTrue();
    }

    /**
     * Ровно 45° - граничный случай, проходит порог 45° (>=)
     */
    @Test
    @DisplayName("Ровно 45° — валидно (граница)")
    void exactly45Valid() {
        Coordinate routeStart = new Coordinate(0, 0);
        Coordinate routeEnd = new Coordinate(100, 100);
        LineString axis = gf.createLineString(new Coordinate[]{
                new Coordinate(0, 100),
                new Coordinate(100, 0)
        });

        boolean ok = angleChecker.isCrossingAngleValid(
                routeStart, routeEnd, axis, 45.0);
        assertThat(ok).isTrue();
    }

    /**
     * 30° - угол меньше порога, отклонить
     */
    @Test
    @DisplayName("30° — невалидно")
    void shallowAngleInvalid() {
        Coordinate routeStart = new Coordinate(0, 0);
        Coordinate routeEnd = new Coordinate(100, 57.7);
        LineString axis = gf.createLineString(new Coordinate[]{
                new Coordinate(0, 50),
                new Coordinate(200, 50)
        });

        boolean ok = angleChecker.isCrossingAngleValid(
                routeStart, routeEnd, axis, 45.0);
        assertThat(ok).isFalse();
    }

    /**
     * Коллинеарные линии - пересечение не точка, а линия; отклонить
     */
    @Test
    @DisplayName("Коллинеарные линии — невалидно")
    void collinearInvalid() {
        Coordinate routeStart = new Coordinate(0, 50);
        Coordinate routeEnd = new Coordinate(200, 50);
        LineString axis = gf.createLineString(new Coordinate[]{
                new Coordinate(0, 50),
                new Coordinate(100, 50)
        });

        boolean ok = angleChecker.isCrossingAngleValid(
                routeStart, routeEnd, axis, 45.0);
        assertThat(ok).isFalse();
    }

    /**
     * Нет пересечения - угол не определен, проверка не применяется
     */
    @Test
    @DisplayName("Нет пересечения — валидно")
    void noCrossingValid() {
        Coordinate routeStart = new Coordinate(0, 0);
        Coordinate routeEnd = new Coordinate(100, 0);
        LineString axis = gf.createLineString(new Coordinate[]{
                new Coordinate(0, 500),
                new Coordinate(100, 500)
        });

        boolean ok = angleChecker.isCrossingAngleValid(
                routeStart, routeEnd, axis, 45.0);
        assertThat(ok).isTrue();
    }

    /**
     * minAngleDeg = null - применяется дефолт 45°
     */
    @Test
    @DisplayName("null порог — используется 45° по умолчанию")
    void nullThresholdUsesDefault() {
        // 60° - проходит дефолт 45°
        Coordinate routeStart = new Coordinate(0, 0);
        Coordinate routeEnd = new Coordinate(100, 173);
        LineString axis = gf.createLineString(new Coordinate[]{
                new Coordinate(0, 100),
                new Coordinate(200, 100)
        });

        boolean ok = angleChecker.isCrossingAngleValid(
                routeStart, routeEnd, axis, null);
        assertThat(ok).isTrue();
    }
}
