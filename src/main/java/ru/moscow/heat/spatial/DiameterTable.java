package ru.moscow.heat.spatial;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Нормативный справочник условных диаметров тепловых сетей (ДУ).
 * Загружает и предоставляет данные Таблицы 1 Технического приложения ЛЦТ-2026.
 * Все числовые значения строго синхронизированы с tp-tables.md
 */
@Component
public class DiameterTable {

    private final List<DiameterSpec> specs;
    private final Map<Integer, DiameterSpec> byDiameterMap;

    public DiameterTable() {
        List<DiameterSpec> list = List.of(
                new DiameterSpec(50, 3.5, 181, 74023.0, 0.400, 0.125),
                new DiameterSpec(65, 8.3, 245, 78631.0, 0.430, 0.140),
                new DiameterSpec(80, 13.2, 327, 83530.0, 0.470, 0.160),
                new DiameterSpec(100, 22.3, 419, 89748.0, 0.510, 0.180),
                new DiameterSpec(125, 40.2, 554, 97275.0, 0.600, 0.225),
                new DiameterSpec(150, 65.1, 696, 105507.0, 0.650, 0.250),
                new DiameterSpec(200, 152.3, 1042, 120275.0, 0.880, 0.315),
                new DiameterSpec(250, 274.9, 1379, 135323.0, 1.050, 0.400),
                new DiameterSpec(300, 437.4, 1718, 150022.0, 1.150, 0.450),
                new DiameterSpec(400, 943.1, 2477, 190299.0, 1.370, 0.560),
                new DiameterSpec(500, 1663.4, 3245, 224137.0, 1.670, 0.710),
                new DiameterSpec(600, 2627.7, 4037, 264790.0, 1.850, 0.800),
                new DiameterSpec(700, 3735.1, 4775, 324298.0, 2.050, 0.900),
                new DiameterSpec(800, 5296.8, 5644, 325996.0, 2.250, 1.000),
                new DiameterSpec(900, 7165.0, 6518, 327693.0, 2.450, 1.100),
                new DiameterSpec(1000, 9391.8, 7419, 418777.0, 2.650, 1.200),
                new DiameterSpec(1200, 15012.8, 9288, 428074.0, 3.100, 1.425),
                new DiameterSpec(1400, 22501.9, 11276, 683417.0, 3.450, 1.600)
        );
        this.specs = list.stream()
                .sorted(Comparator.comparingInt(DiameterSpec::getDiameterMm))
                .collect(Collectors.toUnmodifiableList());

        Map<Integer, DiameterSpec> map = new LinkedHashMap<>();
        for (DiameterSpec spec : this.specs) {
            map.put(spec.getDiameterMm(), spec);
        }
        this.byDiameterMap = Collections.unmodifiableMap(map);
    }

    /**
     * Возвращает неизменяемый список всех спецификаций ДУ,
     * отсортированный по возрастанию диаметра
     */
    public List<DiameterSpec> getAll() {
        return specs;
    }

    /**
     * Поиск спецификации по точной величине условного диаметра в мм
     * @param mm диаметр в миллиметрах (например, 100, 500)
     * @return Optional со спецификацией или empty,
     *         если такого диаметра нет в таблице
     */
    public Optional<DiameterSpec> findByDiameter(int mm) {
        return Optional.ofNullable(byDiameterMap.get(mm));
    }

    /**
     * Выбирает минимальный ДУ, пропускная способность которого
     * достаточна для заданного расхода
     * @param flowTph расчетный расход теплоносителя, т/ч
     * @return минимальный подходящий DiameterSpec
     * @throws IllegalArgumentException если расход превышает
     *         максимальную пропускную способность (22501.9 т/ч)
     *         или является отрицательным
     */
    public DiameterSpec minDiameterForFlow(double flowTph) {
        if (flowTph < 0) {
            throw new IllegalArgumentException(
                    "Расчетный расход не может быть отрицательным: " + flowTph);
        }
        for (DiameterSpec spec : specs) {
            if (spec.getCapacityTph() >= flowTph) {
                return spec;
            }
        }
        throw new IllegalArgumentException(String.format(
                "Расход %.2f т/ч превышает максимальную пропускную "
                        + "способность таблицы (22501.9 т/ч)",
                flowTph));
    }

    /**
     * Выбирает минимальный ДУ, одновременно удовлетворяющий
     * расчетному расходу и предельной длине участка.
     * Ключевой метод подбора диаметра по правилам раздела 2.3
     * Технического приложения
     * @param flowTph расчетный расход, т/ч
     * @param lengthM длина непрерывного участка сети, м
     * @return минимальный подходящий DiameterSpec
     * @throws IllegalArgumentException если параметры
     *         отрицательные или ни один ДУ из таблицы
     *         не удовлетворяет одновременно обоим условиям
     */
    public DiameterSpec minDiameterForFlowAndLength(
            double flowTph, double lengthM) {
        if (flowTph < 0 || lengthM < 0) {
            throw new IllegalArgumentException(String.format(
                    "Параметры не могут быть отрицательными: "
                            + "flowTph=%.2f, lengthM=%.2f",
                    flowTph, lengthM));
        }
        for (DiameterSpec spec : specs) {
            if (spec.getCapacityTph() >= flowTph
                    && spec.getMaxLengthM() >= lengthM) {
                return spec;
            }
        }
        throw new IllegalArgumentException(String.format(
                "Не найден ДУ для расхода %.2f т/ч и длины %.2f м "
                        + "(максимум таблицы: 22501.9 т/ч и 11276 м)",
                flowTph, lengthM));
    }

    /**
     * Возвращает следующий по величине условный диаметр.
     * Используется при невозможности удовлетворить предельную
     * длину текущим диаметром
     * @param current текущая спецификация ДУ
     * @return следующая спецификация ДУ
     * @throws IllegalArgumentException если current == null
     *         или отсутствует в таблице
     * @throws IllegalStateException если current уже
     *         является максимальным диаметром (1400 мм)
     */
    public DiameterSpec nextDiameter(DiameterSpec current) {
        if (current == null) {
            throw new IllegalArgumentException(
                    "Текущая спецификация ДУ не может быть null");
        }
        int index = specs.indexOf(current);
        if (index == -1) {
            throw new IllegalArgumentException(
                    "Диаметр " + current.getDiameterMm()
                            + " отсутствует в нормативной таблице");
        }
        if (index == specs.size() - 1) {
            throw new IllegalStateException(
                    "Диаметр " + current.getDiameterMm()
                            + " мм уже является максимальным");
        }
        return specs.get(index + 1);
    }

    /**
     * Определяет спецификацию для наибольшего ДУ из переданной
     * коллекции диаметров примыкающих участков.
     * Применяется для определения ДУ и стоимости тепловой камеры
     * по разделу 3.2
     * @param diameters коллекция диаметров примыкающих участков в мм
     * @return DiameterSpec для максимального диаметра из коллекции
     * @throws IllegalArgumentException если коллекция пуста,
     *         содержит null или неизвестный диаметр
     */
    public DiameterSpec largestOf(Collection<Integer> diameters) {
        if (diameters == null || diameters.isEmpty()) {
            throw new IllegalArgumentException(
                    "Коллекция диаметров не может быть пустой или null");
        }
        int max = -1;
        for (Integer d : diameters) {
            if (d == null) {
                throw new IllegalArgumentException(
                        "Коллекция диаметров содержит null");
            }
            if (!byDiameterMap.containsKey(d)) {
                throw new IllegalArgumentException(
                        "Диаметр " + d
                                + " мм отсутствует в нормативной таблице");
            }
            if (d > max) {
                max = d;
            }
        }
        return byDiameterMap.get(max);
    }
}
