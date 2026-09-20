package ru.moscow.heat.geojson;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты перечисления {@link ObjectType}: парсинг строковых значений
 * {@code object_type} и корректность наборов обязательных атрибутов
 * и допустимых типов геометрии для каждого типа объекта
 */
class ObjectTypeTest {

    /**
     * Парсинг всех известных значений {@code object_type},
     * встречающихся во входном GeoJSON
     * @param input    строковое значение из GeoJSON
     * @param expected ожидаемая константа перечисления
     */
    @ParameterizedTest
    @CsvSource({
            "source,SOURCE",
            "heat_network,HEAT_NETWORK",
            "heat_chamber,HEAT_CHAMBER",
            "oks_future,OKS_FUTURE",
            "oks_connection_point,OKS_CONNECTION_POINT",
            "oks_existing,OKS_EXISTING",
            "restriction,RESTRICTION"
    })
    void fromString_parsesAllKnownValues(String input,
                                         ObjectType expected) {
        assertThat(ObjectType.fromString(input)).isEqualTo(expected);
    }

    /**
     * Парсинг не зависит от регистра и обрезает пробелы по краям
     * @param input    строковое значение из GeoJSON
     * @param expected ожидаемая константа перечисления
     */
    @ParameterizedTest
    @CsvSource({
            "SOURCE,SOURCE",
            "Heat_Network,HEAT_NETWORK",
            " heat_chamber ,HEAT_CHAMBER",
            "OKS_FUTURE,OKS_FUTURE"
    })
    void fromString_isCaseInsensitiveAndTrims(String input,
                                              ObjectType expected) {
        assertThat(ObjectType.fromString(input)).isEqualTo(expected);
    }

    /**
     * Неизвестные значения, пустая строка, строка из пробелов и
     * {@code null} дают {@code null} вместо исключения
     */
    @Test
    void fromString_returnsNullForUnknown() {
        assertThat(ObjectType.fromString("unknown")).isNull();
        assertThat(ObjectType.fromString("")).isNull();
        assertThat(ObjectType.fromString("   ")).isNull();
        assertThat(ObjectType.fromString(null)).isNull();
    }

    /**
     * heat_network: обязательны diameter, flow_tph, upstream_object_id;
     * допустима только LineString
     */
    @Test
    @DisplayName("HEAT_NETWORK required: diameter, flow_tph, "
            + "upstream_object_id")
    void heatNetworkRequired() {
        assertThat(ObjectType.HEAT_NETWORK.getRequiredProperties())
                .containsExactlyInAnyOrder(
                        "diameter",
                        "flow_tph",
                        "upstream_object_id");
        assertThat(ObjectType.HEAT_NETWORK.getAllowedGeometryTypes())
                .containsExactly("LineString");
    }

    /**
     * Регрессия: heat_chamber не требует {@code upstream_object_id}.
     * Атрибут относится к участкам сети, а не к камерам
     */
    @Test
    @DisplayName("HEAT_CHAMBER required: только diameter")
    void heatChamberRequired() {
        assertThat(ObjectType.HEAT_CHAMBER.getRequiredProperties())
                .containsExactly("diameter");
        assertThat(ObjectType.HEAT_CHAMBER.getAllowedGeometryTypes())
                .containsExactly("Point");
    }

    /**
     * oks_future: обязательны flow_tph и heat_load
     */
    @Test
    void oksFutureRequired() {
        assertThat(ObjectType.OKS_FUTURE.getRequiredProperties())
                .containsExactlyInAnyOrder("flow_tph", "heat_load");
    }

    /**
     * oks_connection_point: обязателен только oks_id
     */
    @Test
    void oksConnectionPointRequired() {
        assertThat(ObjectType.OKS_CONNECTION_POINT
                .getRequiredProperties())
                .containsExactly("oks_id");
    }

    /**
     * restriction: обязателен только restriction_type
     */
    @Test
    void restrictionRequired() {
        assertThat(ObjectType.RESTRICTION.getRequiredProperties())
                .containsExactly("restriction_type");
    }

    /**
     * source: обязательных атрибутов нет
     */
    @Test
    void sourceRequiredIsEmpty() {
        assertThat(ObjectType.SOURCE.getRequiredProperties())
                .isEmpty();
    }

    /**
     * oks_existing: обязательных атрибутов нет
     */
    @Test
    void oksExistingRequiredIsEmpty() {
        assertThat(ObjectType.OKS_EXISTING.getRequiredProperties())
                .isEmpty();
    }
}
