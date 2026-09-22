package ru.moscow.heat.spatial;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты реестра ограничений {@link RestrictionRuleRegistry},
 * соответствующего Таблице 2 Технического приложения ЛЦТ 2026
 * <p>Проверяется:
 * <ul>
 *   <li>правила для всех запрещенных к пересечению типов
 *       (FORBIDDEN): {@code oks}, {@code park},
 *       {@code social_area}, {@code prohibited_site},
 *       {@code water}, {@code railway};</li>
 *   <li>правила для типов со специальным проходом
 *       (SPECIAL_CROSSING): {@code road}, {@code tram_tracks},
 *       {@code gas_pipeline}, {@code power_cable},
 *       {@code heat_network} с проверкой Kспец и габаритов;</li>
 *   <li>шкала отступа до полигона ОКС в зависимости от ДУ;</li>
 *   <li>поведение на неизвестных типах и некорректных входах.</li>
 * </ul>
 */
@DisplayName("Тестирование реестра ограничений")
class RestrictionRuleRegistryTest {

    private RestrictionRuleRegistry registry;

    /**
     * Создает свежий реестр перед каждым тестом
     */
    @BeforeEach
    void setUp() {
        registry = new RestrictionRuleRegistry();
    }

    /**
     * Для каждого запрещенного типа правило имеет режим
     * FORBIDDEN, заданное минимальное расстояние и не содержит
     * параметров специального прохода
     * @param type     строковый тип ограничения
     * @param distance ожидаемое минимальное расстояние, м
     */
    @ParameterizedTest(name = "Запретный тип {0}: мин. расстояние {1} м")
    @CsvSource({
            "oks, 5.0",
            "park, 1.0",
            "social_area, 1.0",
            "prohibited_site, 1.0",
            "water, 1.0",
            "railway, 1.0"
    })
    @DisplayName("Запрещенные типы ограничений (FORBIDDEN)")
    void shouldVerifyForbiddenTypes(String type, double distance) {
        Optional<RestrictionRule> ruleOpt = registry.ruleFor(type);
        assertThat(ruleOpt).isPresent();
        RestrictionRule rule = ruleOpt.get();
        assertThat(rule.getRuleType())
                .isEqualTo(RestrictionRuleType.FORBIDDEN);
        assertThat(rule.getMinHorizontalDistanceM())
                .isEqualTo(distance);
        assertThat(rule.getMinCrossingAngleDeg()).isNull();
        assertThat(rule.getKspets()).isNull();
        assertThat(rule.getSpecialZonePaddingM()).isEqualTo(0.0);
    }

    /**
     * Автодорога: специальный проход, мин. расстояние 1,5 м,
     * угол не менее 45°, Kспец 1.60, зона по 3 м за границей
     * полигона, без собственного габарита
     */
    @Test
    @DisplayName("Специальный проход: автодорога (road)")
    void shouldVerifyRoadRule() {
        RestrictionRule rule = registry.ruleFor("road")
                .orElseThrow();
        assertThat(rule.getRuleType())
                .isEqualTo(RestrictionRuleType.SPECIAL_CROSSING);
        assertThat(rule.getMinHorizontalDistanceM()).isEqualTo(1.5);
        assertThat(rule.getMinCrossingAngleDeg()).isEqualTo(45.0);
        assertThat(rule.getKspets()).isEqualTo(1.60);
        assertThat(rule.getSpecialZonePaddingM()).isEqualTo(3.0);
        assertThat(rule.isHasOwnEnvelope()).isFalse();
    }

    /**
     * Трамвайные пути: специальный проход, мин. расстояние 1,5 м,
     * угол не менее 45°, Kспец 1.75, зона по 3 м за границей
     * полигона, без собственного габарита
     */
    @Test
    @DisplayName("Специальный проход: трамвайные пути (tram_tracks)")
    void shouldVerifyTramTracksRule() {
        RestrictionRule rule = registry.ruleFor("tram_tracks")
                .orElseThrow();
        assertThat(rule.getRuleType())
                .isEqualTo(RestrictionRuleType.SPECIAL_CROSSING);
        assertThat(rule.getMinHorizontalDistanceM()).isEqualTo(1.5);
        assertThat(rule.getMinCrossingAngleDeg()).isEqualTo(45.0);
        assertThat(rule.getKspets()).isEqualTo(1.75);
        assertThat(rule.getSpecialZonePaddingM()).isEqualTo(3.0);
        assertThat(rule.isHasOwnEnvelope()).isFalse();
    }

    /**
     * Газопровод: специальный проход, мин. расстояние 2,0 м,
     * Kспец 1.25, габарит 0,40 на 0,40 м, глубина верха 2,8 м,
     * зона по 2 м от точки пересечения
     */
    @Test
    @DisplayName("Специальный проход: газопровод (gas_pipeline)")
    void shouldVerifyGasPipelineRule() {
        RestrictionRule rule = registry.ruleFor("gas_pipeline")
                .orElseThrow();
        assertThat(rule.getRuleType())
                .isEqualTo(RestrictionRuleType.SPECIAL_CROSSING);
        assertThat(rule.getMinHorizontalDistanceM()).isEqualTo(2.0);
        assertThat(rule.getMinCrossingAngleDeg()).isNull();
        assertThat(rule.getKspets()).isEqualTo(1.25);
        assertThat(rule.getSpecialZonePaddingM()).isEqualTo(2.0);
        assertThat(rule.isHasOwnEnvelope()).isTrue();
        assertThat(rule.getEnvelopeWidthM()).isEqualTo(0.40);
        assertThat(rule.getEnvelopeHeightM()).isEqualTo(0.40);
        assertThat(rule.getEnvelopeTopDepthM()).isEqualTo(2.8);
    }

    /**
     * Силовой кабель до 35 кВ: специальный проход,
     * мин. расстояние 2,0 м, Kспец 1.15, габарит 0,20 на 0,20 м,
     * глубина верха 2,7 м, зона по 2 м от точки пересечения
     */
    @Test
    @DisplayName("Специальный проход: силовой кабель (power_cable)")
    void shouldVerifyPowerCableRule() {
        RestrictionRule rule = registry.ruleFor("power_cable")
                .orElseThrow();
        assertThat(rule.getRuleType())
                .isEqualTo(RestrictionRuleType.SPECIAL_CROSSING);
        assertThat(rule.getMinHorizontalDistanceM()).isEqualTo(2.0);
        assertThat(rule.getMinCrossingAngleDeg()).isNull();
        assertThat(rule.getKspets()).isEqualTo(1.15);
        assertThat(rule.getSpecialZonePaddingM()).isEqualTo(2.0);
        assertThat(rule.isHasOwnEnvelope()).isTrue();
        assertThat(rule.getEnvelopeWidthM()).isEqualTo(0.20);
        assertThat(rule.getEnvelopeHeightM()).isEqualTo(0.20);
        assertThat(rule.getEnvelopeTopDepthM()).isEqualTo(2.7);
    }

    /**
     * Существующая тепловая сеть без врезки: специальный проход,
     * мин. расстояние 1,0 м, Kспец 1.05, глубина до верха 3,0 м,
     * зона по 2 м от точки пересечения
     */
    @Test
    @DisplayName("Специальный проход: теплосеть без врезки (heat_network)")
    void shouldVerifyHeatNetworkRule() {
        RestrictionRule rule = registry.ruleFor("heat_network")
                .orElseThrow();
        assertThat(rule.getRuleType())
                .isEqualTo(RestrictionRuleType.SPECIAL_CROSSING);
        assertThat(rule.getMinHorizontalDistanceM()).isEqualTo(1.0);
        assertThat(rule.getMinCrossingAngleDeg()).isNull();
        assertThat(rule.getKspets()).isEqualTo(1.05);
        assertThat(rule.getSpecialZonePaddingM()).isEqualTo(2.0);
        assertThat(rule.isHasOwnEnvelope()).isTrue();
        assertThat(rule.getEnvelopeTopDepthM()).isEqualTo(3.0);
    }

    /**
     * Минимальный отступ до полигона ОКС зависит от ДУ:
     * менее 500 мм - 5 м, от 500 до 800 мм - 7 м, от 900 мм - 9 м
     * @param mm               условный диаметр, мм
     * @param expectedDistance ожидаемый отступ, м
     */
    @ParameterizedTest(name = "ДУ={0} -> отступ={1} м")
    @CsvSource({
            "50, 5",
            "400, 5",
            "499, 5",
            "500, 7",
            "600, 7",
            "800, 7",
            "900, 9",
            "1000, 9",
            "1400, 9"
    })
    @DisplayName("Отступ до ОКС в зависимости от ДУ")
    void shouldReturnCorrectDistanceForDiameter(int mm,
                                                int expectedDistance) {
        assertThat(registry.minDistanceForDiameter(mm))
                .isEqualTo(expectedDistance);
    }

    /**
     * Нулевой или отрицательный ДУ не является допустимым входом
     * @param invalidMm некорректный диаметр, мм
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1, -500})
    @DisplayName("minDistanceForDiameter: неположительный ДУ")
    void shouldThrowOnInvalidDiameter(int invalidMm) {
        assertThatThrownBy(
                () -> registry.minDistanceForDiameter(invalidMm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("должен быть положительным");
    }

    /**
     * Неизвестные типы, {@code null} и строка из пробелов
     * возвращают {@code Optional.empty}. Это допустимо по ТЗ:
     * поддержка дополнительных типов не обязательна
     */
    @Test
    @DisplayName("Неизвестный тип или null -> empty")
    void shouldReturnEmptyForUnknownTypes() {
        assertThat(registry.ruleFor("unknown_type")).isEmpty();
        assertThat(registry.ruleFor("building")).isEmpty();
        assertThat(registry.ruleFor(null)).isEmpty();
        assertThat(registry.ruleFor("   ")).isEmpty();
    }

    /**
     * Поиск правила не зависит от регистра и игнорирует пробелы по краям
     */
    @Test
    @DisplayName("Поиск правила не зависит от регистра")
    void shouldBeCaseInsensitive() {
        assertThat(registry.ruleFor("ROAD")).isPresent();
        assertThat(registry.ruleFor("  Road  ")).isPresent();
        assertThat(registry.ruleFor("Gas_Pipeline")).isPresent();
    }
}
