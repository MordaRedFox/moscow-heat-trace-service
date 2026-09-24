package ru.moscow.heat.spatial;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты справочника {@link DiameterTable}, соответствующего
 * Таблице 1 Технического приложения ЛЦТ 2026
 * <p>Проверяется:
 * <ul>
 *   <li>полнота и порядок нормативных диаметров от 50 до 1400 мм;</li>
 *   <li>точное соответствие всех числовых значений (пропускная
 *       способность, предельная длина, стоимость, габариты пары
 *       труб) строкам Таблицы 1;</li>
 *   <li>подбор минимального ДУ по расходу и по паре
 *       «расход + предельная длина»;</li>
 *   <li>граничные и негативные сценарии: отрицательный расход,
 *       превышение максимума, отсутствие ДУ в таблице;</li>
 *   <li>переход к следующему диаметру и определение наибольшего
 *       ДУ из набора примыканий.</li>
 * </ul>
 */
@DisplayName("Тестирование справочника ДУ (DiameterTable)")
class DiameterTableTest {

    private DiameterTable table;

    /**
     * Создает свежий экземпляр справочника перед каждым тестом.
     * Справочник без состояния, но изоляция упрощает диагностику
     */
    @BeforeEach
    void setUp() {
        table = new DiameterTable();
    }

    /**
     * Справочник содержит ровно 18 нормативных диаметров,
     * упорядоченных по возрастанию
     */
    @Test
    @DisplayName("Таблица содержит 18 диаметров от 50 до 1400 мм")
    void shouldContainAll18CanonicalDiameters() {
        List<DiameterSpec> all = table.getAll();
        assertThat(all).hasSize(18);

        int[] expected = {
                50, 65, 80, 100, 125, 150, 200, 250, 300, 400,
                500, 600, 700, 800, 900, 1000, 1200, 1400
        };
        for (int i = 0; i < expected.length; i++) {
            assertThat(all.get(i).getDiameterMm())
                    .isEqualTo(expected[i]);
        }
    }

    /**
     * Все числовые значения каждой строки Таблицы 1 совпадают
     * с эталоном технического приложения
     * @param mm       условный диаметр, мм
     * @param capacity пропускная способность пары, т/ч
     * @param length   предельная длина участка, м
     * @param cost     стоимость строительства, руб./м
     * @param width    расчётная ширина пары труб, м
     * @param height   расчётная высота пары труб, м
     */
    @ParameterizedTest(name = "ДУ={0}: пропускная={1}, длина={2}, "
            + "цена={3}, ширина={4}, высота={5}")
    @CsvSource({
            "50, 3.5, 181, 74023.0, 0.400, 0.125",
            "65, 8.3, 245, 78631.0, 0.430, 0.140",
            "80, 13.2, 327, 83530.0, 0.470, 0.160",
            "100, 22.3, 419, 89748.0, 0.510, 0.180",
            "125, 40.2, 554, 97275.0, 0.600, 0.225",
            "150, 65.1, 696, 105507.0, 0.650, 0.250",
            "200, 152.3, 1042, 120275.0, 0.880, 0.315",
            "250, 274.9, 1379, 135323.0, 1.050, 0.400",
            "300, 437.4, 1718, 150022.0, 1.150, 0.450",
            "400, 943.1, 2477, 190299.0, 1.370, 0.560",
            "500, 1663.4, 3245, 224137.0, 1.670, 0.710",
            "600, 2627.7, 4037, 264790.0, 1.850, 0.800",
            "700, 3735.1, 4775, 324298.0, 2.050, 0.900",
            "800, 5296.8, 5644, 325996.0, 2.250, 1.000",
            "900, 7165.0, 6518, 327693.0, 2.450, 1.100",
            "1000, 9391.8, 7419, 418777.0, 2.650, 1.200",
            "1200, 15012.8, 9288, 428074.0, 3.100, 1.425",
            "1400, 22501.9, 11276, 683417.0, 3.450, 1.600"
    })
    @DisplayName("Сверка числовых значений Таблицы 1 ТП")
    void shouldMatchCanonicalTable1Values(int mm,
                                          double capacity,
                                          double length,
                                          double cost,
                                          double width,
                                          double height) {
        Optional<DiameterSpec> specOpt = table.findByDiameter(mm);
        assertThat(specOpt).isPresent();
        DiameterSpec spec = specOpt.get();
        assertThat(spec.getCapacityTph()).isEqualTo(capacity);
        assertThat(spec.getMaxLengthM()).isEqualTo(length);
        assertThat(spec.getCostPerMeter()).isEqualTo(cost);
        assertThat(spec.getPairWidthM()).isEqualTo(width);
        assertThat(spec.getPairHeightM()).isEqualTo(height);
    }

    /**
     * Диаметр, которого нет в таблице, не резолвится
     */
    @Test
    @DisplayName("Поиск по несуществующему ДУ возвращает empty")
    void shouldReturnEmptyForUnknownDiameter() {
        assertThat(table.findByDiameter(99)).isEmpty();
        assertThat(table.findByDiameter(1500)).isEmpty();
    }

    /**
     * Подбор минимального ДУ по расходу: проверяются точная
     * граница, чуть выше границы, верхняя граница таблицы и
     * нулевой расход
     */
    @Test
    @DisplayName("Подбор минимального ДУ по расходу (границы)")
    void shouldFindMinDiameterForFlow() {
        assertThat(table.minDiameterForFlow(3.5).getDiameterMm())
                .isEqualTo(50);
        assertThat(table.minDiameterForFlow(3.5001).getDiameterMm())
                .isEqualTo(65);
        assertThat(table.minDiameterForFlow(22501.9).getDiameterMm())
                .isEqualTo(1400);
        assertThat(table.minDiameterForFlow(0.0).getDiameterMm())
                .isEqualTo(50);
    }

    /**
     * Расход выше максимальной пропускной способности таблицы
     * не может быть покрыт ни одним ДУ
     */
    @Test
    @DisplayName("Превышение максимального расхода — исключение")
    void shouldThrowWhenFlowExceedsMaximum() {
        assertThatThrownBy(() -> table.minDiameterForFlow(22502.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "превышает максимальную пропускную");
    }

    /**
     * Отрицательный расход - некорректный вход
     */
    @Test
    @DisplayName("Отрицательный расход — исключение")
    void shouldThrowWhenFlowIsNegative() {
        assertThatThrownBy(() -> table.minDiameterForFlow(-1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не может быть отрицательным");
    }

    /**
     * Подбор минимального ДУ одновременно по расходу и
     * предельной длине: ДУ 50 не проходит по длине, поэтому
     * выбирается следующий подходящий
     */
    @Test
    @DisplayName("Подбор ДУ по расходу и предельной длине")
    void shouldFindMinDiameterForFlowAndLength() {
        DiameterSpec spec1 = table
                .minDiameterForFlowAndLength(2.0, 200.0);
        assertThat(spec1.getDiameterMm()).isEqualTo(65);

        DiameterSpec spec2 = table
                .minDiameterForFlowAndLength(10.0, 100.0);
        assertThat(spec2.getDiameterMm()).isEqualTo(80);

        DiameterSpec maxSpec = table
                .minDiameterForFlowAndLength(22501.9, 11276.0);
        assertThat(maxSpec.getDiameterMm()).isEqualTo(1400);
    }

    /**
     * Если длина превышает предельную даже для максимального ДУ,
     * метод бросает исключение
     */
    @Test
    @DisplayName("Превышение максимальной длины — исключение")
    void shouldThrowWhenLengthExceedsMaximum() {
        assertThatThrownBy(() -> table
                .minDiameterForFlowAndLength(10.0, 12000.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Не найден ДУ");
    }

    /**
     * Переход к следующему диаметру по нормативной шкале
     */
    @Test
    @DisplayName("Переход к следующему диаметру")
    void shouldReturnNextDiameter() {
        DiameterSpec d50 = table.findByDiameter(50).orElseThrow();
        assertThat(table.nextDiameter(d50).getDiameterMm())
                .isEqualTo(65);

        DiameterSpec d1200 = table.findByDiameter(1200)
                .orElseThrow();
        assertThat(table.nextDiameter(d1200).getDiameterMm())
                .isEqualTo(1400);
    }

    /**
     * После максимального диаметра следующий недоступен
     */
    @Test
    @DisplayName("nextDiameter после 1400 — IllegalStateException")
    void shouldThrowIllegalStateExceptionOnNextAfterMax() {
        DiameterSpec d1400 = table.findByDiameter(1400)
                .orElseThrow();
        assertThatThrownBy(() -> table.nextDiameter(d1400))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже является максимальным");
    }

    /**
     * {@code nextDiameter(null)} - некорректный вход
     */
    @Test
    @DisplayName("nextDiameter(null) — IllegalArgumentException")
    void shouldThrowOnNullInNextDiameter() {
        assertThatThrownBy(() -> table.nextDiameter(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * {@code largestOf} определяет наибольший ДУ из набора
     * примыкающих участков - используется для ДУ и стоимости
     * новой тепловой камеры
     */
    @Test
    @DisplayName("largestOf выбирает наибольший ДУ")
    void shouldFindLargestDiameterFromCollection() {
        DiameterSpec largest = table.largestOf(
                List.of(50, 200, 125, 80));
        assertThat(largest.getDiameterMm()).isEqualTo(200);

        DiameterSpec single = table.largestOf(List.of(1400));
        assertThat(single.getDiameterMm()).isEqualTo(1400);
    }

    /**
     * {@code largestOf} отклоняет пустую коллекцию, {@code null}
     * и диаметры, отсутствующие в нормативной таблице
     */
    @Test
    @DisplayName("largestOf на некорректной коллекции — исключение")
    void shouldThrowOnInvalidCollectionInLargestOf() {
        assertThatThrownBy(() -> table.largestOf(
                Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не может быть пустой");

        assertThatThrownBy(() -> table.largestOf(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> table.largestOf(List.of(100, 9999)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "отсутствует в нормативной таблице");
    }
}
