package ru.moscow.heat.trace.service;

import org.locationtech.jts.geom.Coordinate;
import org.springframework.stereotype.Service;
import ru.moscow.heat.trace.graph.ObstacleModel;

import java.util.List;

/**
 * Постобработка сырого пути из {@code VisibilityGraph.shortestPath}
 * (план, шаг 6).
 * <p>
 * Задачи:
 * <ul>
 *     <li>убрать коллинеарные вершины (угол ≈ 180°);</li>
 *     <li>убедиться, что максимальный угол поворота ≤ 90° (требование ТП);</li>
 *     <li>убрать «зигзаги» — если несколько коротких сегментов можно заменить
 *     одним прямым (A-C не пересекает запретов), убрать промежуточный узел.</li>
 * </ul>
 */
@Service
public class RouteSimplifier {

    /**
     * Упрощает путь, сохраняя геометрическую корректность относительно
     * препятствий.
     *
     * @param rawPathUtm     путь из visibility graph, координаты в UTM, по порядку
     * @param obstacleModel  модель препятствий — нужна, чтобы проверять,
     *                       что "срезание" зигзага не пересекает FORBIDDEN
     * @return упрощённый путь
     */
    public List<Coordinate> simplify(List<Coordinate> rawPathUtm, ObstacleModel obstacleModel) {
        // TODO:
        // 1. пройтись по тройкам соседних точек, убрать коллинеарные (угол ~180°);
        // 2. для непрямых углов проверить, что поворот <= 90° (иначе — задача
        //    для отдельного анализа, т.к. в чистом visibility graph такого
        //    обычно не возникает, но препятствие сложной формы может дать
        //    более острый локальный изгиб);
        // 3. "срезание" зигзагов: для не-соседних A,C на пути проверить
        //    isVisible(A,C); если видно — выбросить всё между ними.
        throw new UnsupportedOperationException("TODO: итерация 5, шаг 6");
    }
}
