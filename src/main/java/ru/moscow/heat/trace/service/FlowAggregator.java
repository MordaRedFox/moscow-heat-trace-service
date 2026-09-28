package ru.moscow.heat.trace.service;

import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.trace.model.OksGroup;
import ru.moscow.heat.trace.model.RouteTree;
import ru.moscow.heat.trace.model.TreeEdge;
import ru.moscow.heat.trace.model.TreeNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Агрегирует расчётный расход по рёбрам дерева маршрутов (итерация 6, шаг 3).
 * <p>
 * Направление теплоносителя — от листьев (ОКС) к корню (тай-ин). Расход
 * ребра равен сумме расходов всех ОКС, находящихся в поддереве его
 * {@code to}-узла. Таким образом на общем стволе у корня оказывается
 * суммарный расход всей группы, а на концевых ветвях — расход одного ОКС.
 * <p>
 * Одновременно заполняется {@code servedOksIds} каждого ребра — список
 * ОКС, идущих через это ребро к тай-ину. Это потребуется на шаге 4 для
 * проверки предельной длины по каждому пути отдельно.
 */
@Service
public class FlowAggregator {

    /**
     * Заполняет {@code flowTph} и {@code servedOksIds} всех рёбер дерева.
     *
     * @param tree           дерево маршрутов группы
     * @param flowByFeatureId мапа: feature_id ОКС → расход, т/ч
     */
    public void aggregate(RouteTree tree, Map<String, Double> flowByFeatureId) {
        Objects.requireNonNull(tree, "tree");
        Objects.requireNonNull(flowByFeatureId, "flowByFeatureId");
        propagate(tree.getRoot(), flowByFeatureId);
    }

    /**
     * convenience-перегрузка: строит мапу расходов из группы и агрегирует.
     * Используется оркестратором в рантайме.
     */
    public void aggregate(RouteTree tree, OksGroup group) {
        aggregate(tree, flowMapOf(group));
    }

    /**
     * Строит мапу {@code featureId → flow_tph} из ОКС группы.
     * {@code null}-расход трактуется как 0.
     */
    public static Map<String, Double> flowMapOf(OksGroup group) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (OksConnectionPointEntity oks : group.getPoints()) {
            double flow = oks.getFlowTph() != null ? oks.getFlowTph() : 0.0;
            map.put(oks.getFeatureId(), flow);
        }
        return map;
    }

    /**
     * Рекурсивный обход от корня. Возвращает суммарный поток в поддереве
     * узла и заполняет исходящие рёбра.
     */
    private SubtreeInfo propagate(TreeNode node, Map<String, Double> flowByFeatureId) {
        double flow = 0.0;
        List<String> served = new ArrayList<>();

        if (node.isLeaf()) {
            flow += flowByFeatureId.getOrDefault(node.getOksFeatureId(), 0.0);
            served.add(node.getOksFeatureId());
        }

        for (TreeEdge edge : node.getChildEdges()) {
            SubtreeInfo child = propagate(edge.getTo(), flowByFeatureId);
            edge.setFlowTph(child.flow);
            edge.setServedOksIds(List.copyOf(child.servedOks));
            flow += child.flow;
            served.addAll(child.servedOks);
        }

        return new SubtreeInfo(flow, served);
    }

    /** Внутренний результат обхода одного поддерева. */
    private static final class SubtreeInfo {
        private final double flow;
        private final List<String> servedOks;

        private SubtreeInfo(double flow, List<String> servedOks) {
            this.flow = flow;
            this.servedOks = servedOks;
        }
    }
}
