package ru.moscow.heat.trace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.moscow.heat.spatial.DiameterSpec;
import ru.moscow.heat.spatial.DiameterTable;
import ru.moscow.heat.trace.model.RouteTree;
import ru.moscow.heat.trace.model.TreeEdge;
import ru.moscow.heat.trace.model.TreeNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Назначает условные диаметры рёбрам дерева маршрутов группы ОКС
 * (итерация 6, шаг 4). Заменяет линейную логику {@code DiameterAssigner}
 * на дереве.
 *
 * <p>Правила (ТП, раздел 2.3; разъяснения п. 1–2):
 * <ul>
 *   <li>расход каждого ребра — сумма расходов обслуживаемых им ОКС
 *       (заполняется {@code FlowAggregator}, шаг 3);</li>
 *   <li>базовый ДУ ребра — минимальный по расходу;</li>
 *   <li>инвариант: по направлению от ОКС (лист) к тай-ину (корень)
 *       ДУ не убывает;</li>
 *   <li>предельная длина проверяется отдельно по каждому непрерывному
 *       пути лист→корень; общий участок учитывается в каждом пути;
 *       параллельные ветви между собой не суммируются;</li>
 *   <li>если минимальный по расходу ДУ не удовлетворяет предельной длине,
 *       выбирается следующий минимальный ДУ, удовлетворяющий обоим условиям;
 *       произвольное завышение не допускается.</li>
 * </ul>
 *
 * <p>Алгоритм:
 * <ol>
 *   <li>база: каждому ребру — минимальный ДУ по расходу;</li>
 *   <li>обеспечение монотонности ДУ от листа к корню (каскадно);</li>
 *   <li>итеративная проверка предельной длины по каждому пути и повышение
 *       ДУ нарушающих участков до сходимости.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TreeDiameterAssigner {

    /**
     * Предельное число итераций сходимости. Ограничено количеством ДУ
     * в таблице плюс запас: на каждой итерации ДУ хотя бы одного участка
     * строго возрастает, а максимальный ДУ конечен.
     */
    private static final int MAX_CONVERGENCE_ITERATIONS = 50;

    private final DiameterTable diameterTable;

    /**
     * Назначает условные диаметры всем рёбрам дерева. Модифицирует
     * {@link TreeEdge#setDiameterMm(int)} в переданном дереве.
     *
     * @param tree дерево маршрутов с уже агрегированными расходами
     *             (после {@code FlowAggregator})
     */
    public void assign(RouteTree tree) {
        assignByFlow(tree);
        enforceMonotonicity(tree);
        enforceMaxLength(tree);
        log.debug("ДУ назначены: {} рёбер, {} листьев",
                tree.getEdges().size(), tree.getLeaves().size());
    }

    /** Фаза 1: минимальный ДУ каждого ребра по его расходу. */
    private void assignByFlow(RouteTree tree) {
        for (TreeEdge edge : tree.getEdges()) {
            DiameterSpec spec = diameterTable.minDiameterForFlow(edge.getFlowTph());
            edge.setDiameterMm(spec.getDiameterMm());
        }
    }

    /**
     * Фаза 2: ДУ не убывает от листа к корню. Для каждого ребра его ДУ
     * становится не меньше максимального ДУ в поддереве ребёнка.
     * Каскад распространяется к корню.
     */
    private void enforceMonotonicity(RouteTree tree) {
        raiseMonotonic(tree.getRoot());
    }

    /**
     * Рекурсивно обеспечивает монотонность в поддереве узла.
     *
     * @param node текущий узел
     * @return максимальный ДУ среди всех рёбер поддерева узла
     *         (рёбра от узла к детям и ниже); 0 для листа
     */
    private int raiseMonotonic(TreeNode node) {
        int maxDiameter = 0;
        for (TreeEdge childEdge : node.getChildEdges()) {
            int childSubtreeMax = raiseMonotonic(childEdge.getTo());
            if (childEdge.getDiameterMm() < childSubtreeMax) {
                childEdge.setDiameterMm(childSubtreeMax);
            }
            maxDiameter = Math.max(maxDiameter, childEdge.getDiameterMm());
        }
        return maxDiameter;
    }

    /**
     * Фаза 3: предельная длина по каждому пути лист→корень.
     * Итеративно повышает ДУ нарушающих участков до сходимости.
     * После каждого повышения заново обеспечивается монотонность.
     */
    private void enforceMaxLength(RouteTree tree) {
        boolean changed = true;
        int iteration = 0;
        while (changed && iteration < MAX_CONVERGENCE_ITERATIONS) {
            changed = false;
            for (TreeNode leaf : tree.getLeaves()) {
                changed |= fixPathMaxLength(leaf);
            }
            if (changed) {
                enforceMonotonicity(tree);
            }
            iteration++;
        }
        if (changed) {
            throw new IllegalStateException(
                    "Не удалось обеспечить предельную длину дерева за "
                            + MAX_CONVERGENCE_ITERATIONS + " итераций");
        }
    }

    /**
     * Проверяет один путь лист→корень и повышает ДУ участков,
     * нарушающих предельную длину.
     *
     * @param leaf лист (точка подключения ОКС)
     * @return {@code true}, если хотя бы одно ребро изменило ДУ
     */
    private boolean fixPathMaxLength(TreeNode leaf) {
        boolean changed = false;
        List<TreeEdge> path = collectPathToRoot(leaf);

        int i = 0;
        while (i < path.size()) {
            int diameter = path.get(i).getDiameterMm();
            int j = i;
            double runLength = 0.0;
            double runMaxFlow = 0.0;
            List<TreeEdge> runEdges = new ArrayList<>();

            while (j < path.size() && path.get(j).getDiameterMm() == diameter) {
                runLength += path.get(j).getLengthM();
                runMaxFlow = Math.max(runMaxFlow, path.get(j).getFlowTph());
                runEdges.add(path.get(j));
                j++;
            }

            DiameterSpec spec = diameterTable.findByDiameter(diameter)
                    .orElseThrow(() -> new IllegalStateException(
                            "ДУ " + diameter + " отсутствует в нормативной таблице"));

            if (runLength > spec.getMaxLengthM()) {
                DiameterSpec raised = diameterTable
                        .minDiameterForFlowAndLength(runMaxFlow, runLength);
                for (TreeEdge edge : runEdges) {
                    if (edge.getDiameterMm() < raised.getDiameterMm()) {
                        edge.setDiameterMm(raised.getDiameterMm());
                        changed = true;
                    }
                }
            }
            i = j;
        }
        return changed;
    }

    /** Собирает рёбра пути от листа к корню (первое — у листа). */
    private List<TreeEdge> collectPathToRoot(TreeNode leaf) {
        List<TreeEdge> path = new ArrayList<>();
        TreeNode node = leaf;
        while (node.getParentEdge() != null) {
            path.add(node.getParentEdge());
            node = node.getParentEdge().getFrom();
        }
        return path;
    }
}
