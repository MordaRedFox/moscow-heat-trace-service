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

@DisplayName("Тестирование нормативного реестра ограничений (RestrictionRuleRegistry)")
class RestrictionRuleRegistryTest {

    private RestrictionRuleRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RestrictionRuleRegistry();
    }

    @ParameterizedTest(name = "Запретный тип {0}: мин. расстояние {1} м")
    @CsvSource({
            "oks, 5.0",
            "park, 1.0",
            "social_area, 1.0",
            "prohibited_site, 1.0",
            "water, 1.0",
            "railway, 1.0"
    })
    @DisplayName("Проверка запрещенных типов ограничений (FORBIDDEN)")
    void shouldVerifyForbiddenTypes(String type, double distance) {
        Optional<RestrictionRule> ruleOpt = registry.ruleFor(type);
        assertThat(ruleOpt).isPresent();
        RestrictionRule rule = ruleOpt.get();
        assertThat(rule.getRuleType()).isEqualTo(RestrictionRuleType.FORBIDDEN);
        assertThat(rule.getMinHorizontalDistanceM()).isEqualTo(distance);
        assertThat(rule.getMinCrossingAngleDeg()).isNull();
        assertThat(rule.getKspets()).isNull();
        assertThat(rule.getSpecialZonePaddingM()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Проверка специального прохода: автодорога (road)")
    void shouldVerifyRoadRule() {
        RestrictionRule rule = registry.ruleFor("road").orElseThrow();
        assertThat(rule.getRuleType()).isEqualTo(RestrictionRuleType.SPECIAL_CROSSING);
        assertThat(rule.getMinHorizontalDistanceM()).isEqualTo(1.5);
        assertThat(rule.getMinCrossingAngleDeg()).isEqualTo(45.0);
        assertThat(rule.getKspets()).isEqualTo(1.60);
        assertThat(rule.getSpecialZonePaddingM()).isEqualTo(3.0);
        assertThat(rule.isHasOwnEnvelope()).isFalse();
    }

    @Test
    @DisplayName("Проверка специального прохода: трамвайные пути (tram_tracks)")
    void shouldVerifyTramTracksRule() {
        RestrictionRule rule = registry.ruleFor("tram_tracks").orElseThrow();
        assertThat(rule.getRuleType()).isEqualTo(RestrictionRuleType.SPECIAL_CROSSING);
        assertThat(rule.getMinHorizontalDistanceM()).isEqualTo(1.5);
        assertThat(rule.getMinCrossingAngleDeg()).isEqualTo(45.0);
        assertThat(rule.getKspets()).isEqualTo(1.75);
        assertThat(rule.getSpecialZonePaddingM()).isEqualTo(3.0);
        assertThat(rule.isHasOwnEnvelope()).isFalse();
    }

    @Test
    @DisplayName("Проверка специального прохода: газопровод (gas_pipeline)")
    void shouldVerifyGasPipelineRule() {
        RestrictionRule rule = registry.ruleFor("gas_pipeline").orElseThrow();
        assertThat(rule.getRuleType()).isEqualTo(RestrictionRuleType.SPECIAL_CROSSING);
        assertThat(rule.getMinHorizontalDistanceM()).isEqualTo(2.0);
        assertThat(rule.getMinCrossingAngleDeg()).isNull();
        assertThat(rule.getKspets()).isEqualTo(1.25);
        assertThat(rule.getSpecialZonePaddingM()).isEqualTo(2.0);
        assertThat(rule.isHasOwnEnvelope()).isTrue();
        assertThat(rule.getEnvelopeWidthM()).isEqualTo(0.40);
        assertThat(rule.getEnvelopeHeightM()).isEqualTo(0.40);
        assertThat(rule.getEnvelopeTopDepthM()).isEqualTo(2.8);
    }

    @Test
    @DisplayName("Проверка специального прохода: силовой кабель (power_cable)")
    void shouldVerifyPowerCableRule() {
        RestrictionRule rule = registry.ruleFor("power_cable").orElseThrow();
        assertThat(rule.getRuleType()).isEqualTo(RestrictionRuleType.SPECIAL_CROSSING);
        assertThat(rule.getMinHorizontalDistanceM()).isEqualTo(2.0);
        assertThat(rule.getMinCrossingAngleDeg()).isNull();
        assertThat(rule.getKspets()).isEqualTo(1.15);
        assertThat(rule.getSpecialZonePaddingM()).isEqualTo(2.0);
        assertThat(rule.isHasOwnEnvelope()).isTrue();
        assertThat(rule.getEnvelopeWidthM()).isEqualTo(0.20);
        assertThat(rule.getEnvelopeHeightM()).isEqualTo(0.20);
        assertThat(rule.getEnvelopeTopDepthM()).isEqualTo(2.7);
    }

    @Test
    @DisplayName("Проверка специального прохода: теплосеть без врезки (heat_network)")
    void shouldVerifyHeatNetworkRule() {
        RestrictionRule rule = registry.ruleFor("heat_network").orElseThrow();
        assertThat(rule.getRuleType()).isEqualTo(RestrictionRuleType.SPECIAL_CROSSING);
        assertThat(rule.getMinHorizontalDistanceM()).isEqualTo(1.0);
        assertThat(rule.getMinCrossingAngleDeg()).isNull();
        assertThat(rule.getKspets()).isEqualTo(1.05);
        assertThat(rule.getSpecialZonePaddingM()).isEqualTo(2.0);
        assertThat(rule.isHasOwnEnvelope()).isTrue();
        assertThat(rule.getEnvelopeTopDepthM()).isEqualTo(3.0);
    }

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
    @DisplayName("Шкала минимального отступа до ОКС в зависимости от ДУ")
    void shouldReturnCorrectDistanceForDiameter(int mm, int expectedDistance) {
        assertThat(registry.minDistanceForDiameter(mm)).isEqualTo(expectedDistance);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -500})
    @DisplayName("minDistanceForDiameter бросает исключение на неположительном ДУ")
    void shouldThrowOnInvalidDiameter(int invalidMm) {
        assertThatThrownBy(() -> registry.minDistanceForDiameter(invalidMm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("должен быть положительным");
    }

    @Test
    @DisplayName("ruleFor возвращает empty для неизвестных типов или null")
    void shouldReturnEmptyForUnknownTypes() {
        assertThat(registry.ruleFor("unknown_type")).isEmpty();
        assertThat(registry.ruleFor("building")).isEmpty();
        assertThat(registry.ruleFor(null)).isEmpty();
        assertThat(registry.ruleFor("   ")).isEmpty();
    }

    @Test
    @DisplayName("ruleFor работает без учета регистра и пробелов")
    void shouldBeCaseInsensitive() {
        assertThat(registry.ruleFor("ROAD")).isPresent();
        assertThat(registry.ruleFor("  Road  ")).isPresent();
        assertThat(registry.ruleFor("Gas_Pipeline")).isPresent();
    }
}
