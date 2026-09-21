package ru.moscow.heat.geojson;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты перечисления {@link ObjectType}: парсинг строковых значений
 * {@code object_type} и корректность наборов обязательных атрибутов
 * и допустимых типов геометрии
 * Состав типов и обязательных атрибутов соответствует актуальному
 * Техническому приложению ЛЦТ 2026 (раздел 1.1):
 * <ul>
 *   <li>{@code source} — без обязательных атрибутов, Point;</li>
 *   <li>{@code heat_network} — diameter, LineString;</li>
 *   <li>{@code heat_chamber} — без обязательных атрибутов, Point;</li>
 *   <li>{@code oks_connection_point} — flow_tph, Point;</li>
 *   <li>{@code restriction} — restriction_type, линейные и
 *       полигональные типы геометрии.</li>
 * </ul>
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
            "oks_connection_point,OKS_CONNECTION_POINT",
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
            "OKS_CONNECTION_POINT,OKS_CONNECTION_POINT"
    })
    void fromString_isCaseInsensitiveAndTrims(String input,
                                              ObjectType expected) {
        assertThat(ObjectType.fromString(input)).isEqualTo(expected);
    }

    /**
     * Неизвестные значения, пустая строка, строка из пробелов и
     * {@code null} дают {@code null}
     */
    @Test
    void fromString_returnsNullForUnknown() {
        assertThat(ObjectType.fromString("unknown")).isNull();
        assertThat(ObjectType.fromString("")).isNull();
        assertThat(ObjectType.fromString("   ")).isNull();
        assertThat(ObjectType.fromString(null)).isNull();
    }

    /**
     * Удаленные из актуальной модели типы не должны резолвиться
     */
    @Test
    void fromString_removedTypesReturnNull() {
        assertThat(ObjectType.fromString("oks_future")).isNull();
        assertThat(ObjectType.fromString("oks_existing")).isNull();
    }

    /**
     * heat_network: обязателен только diameter; допустима только LineString
     */
    @Test
    @DisplayName("HEAT_NETWORK required: diameter")
    void heatNetworkRequired() {
        assertThat(ObjectType.HEAT_NETWORK.getRequiredProperties())
                .containsExactly("diameter");
        assertThat(ObjectType.HEAT_NETWORK.getAllowedGeometryTypes())
                .containsExactly("LineString");
    }

    /**
     * heat_chamber: без обязательных атрибутов; допустима только Point
     */
    @Test
    @DisplayName("HEAT_CHAMBER required: пусто")
    void heatChamberRequired() {
        assertThat(ObjectType.HEAT_CHAMBER.getRequiredProperties())
                .isEmpty();
        assertThat(ObjectType.HEAT_CHAMBER.getAllowedGeometryTypes())
                .containsExactly("Point");
    }

    /**
     * oks_connection_point: обязателен flow_tph; допустима только Point
     */
    @Test
    @DisplayName("OKS_CONNECTION_POINT required: flow_tph")
    void oksConnectionPointRequired() {
        assertThat(ObjectType.OKS_CONNECTION_POINT
                .getRequiredProperties())
                .containsExactly("flow_tph");
        assertThat(ObjectType.OKS_CONNECTION_POINT
                .getAllowedGeometryTypes())
                .containsExactly("Point");
    }

    /**
     * restriction: обязателен restriction_type; допустимы только
     * линейные ({@code LineString}, {@code MultiLineString}) и
     * полигональные ({@code Polygon}, {@code MultiPolygon}) типы
     * геометрии. Точечные ограничения в актуальной модели
     * не предусмотрены
     */
    @Test
    void restrictionRequired() {
        assertThat(ObjectType.RESTRICTION.getRequiredProperties())
                .containsExactly("restriction_type");
        assertThat(ObjectType.RESTRICTION.getAllowedGeometryTypes())
                .containsExactlyInAnyOrder(
                        "LineString",
                        "MultiLineString",
                        "Polygon",
                        "MultiPolygon");
    }

    /**
     * source: без обязательных атрибутов; допустима только Point
     */
    @Test
    void sourceRequiredIsEmpty() {
        assertThat(ObjectType.SOURCE.getRequiredProperties())
                .isEmpty();
        assertThat(ObjectType.SOURCE.getAllowedGeometryTypes())
                .containsExactly("Point");
    }
}
