package ru.moscow.heat.trace.graph;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Граф видимости (visibility graph) и поиск кратчайшего (по весу с учётом
 * Kспец) пути A* по нему (план, п.2.2 и шаги 4-5)
 * <p>Работает целиком в UTM zone 37N (EPSG:32637), метры - как и
 * {@link ObstacleModel}. Никакой трансформации координат здесь нет
 * <p>Узлы (построены один раз в {@link #build()}): углы буферов FORBIDDEN и
 * SPECIAL_CROSSING зон. Существующие тепловые сети как узлы графа в MVP
 * не добавляются
 * <p>Ребра: пара узлов видима, если отрезок между ними не проходит
 * <b>сквозь внутренность</b> ни одного FORBIDDEN-буфера (см.
 * {@link #isVisible}). Касание границы (например, движение вдоль ребра
 * полигона или через его угол) допускается — это необходимо для обхода
 * препятствий, где маршрут обязан идти вдоль их контура. Для проверки
 * используется {@code Geometry.crosses}, который возвращает {@code true}
 * только если линия входит во внутренность и выходит из нее
 * <p>Конкретные FORBIDDEN-зоны могут быть исключены из проверки для
 * рёбер, инцидентных стартовой точке — так реализуется правило ТП
 * «финальный прямой участок от точки ОКС до границы её собственного
 * полигона ОКС не проверяется на отступ к этому полигону»
 * (разъяснения п. 3).
 * <p>Пересечение SPECIAL-зоны допустимо, вес ребра при этом домножается
 * на максимальный Kспец среди пересеченных зон
 * <p>Не является Spring-бином: создается оркестратором на каждую загрузку,
 * граф строится один раз и переиспользуется для всех ОКС.
 */
public final class VisibilityGraph {

    /** Результат поиска пути: последовательность точек UTM + вес + флаг успеха */
    public static final class PathResult {
        private final List<Coordinate> pathUtm;
        private final double totalWeight;
        private final boolean found;

        public PathResult(List<Coordinate> pathUtm, double totalWeight, boolean found) {
            this.pathUtm = pathUtm;
            this.totalWeight = totalWeight;
            this.found = found;
        }

        public List<Coordinate> getPathUtm() {
            return pathUtm;
        }

        public double getTotalWeight() {
            return totalWeight;
        }

        public boolean isFound() {
            return found;
        }

        public static PathResult notFound() {
            return new PathResult(List.of(), Double.POSITIVE_INFINITY, false);
        }
    }

    private static final class Edge {
        final long toNodeId;
        final double weight;

        Edge(long toNodeId, double weight) {
            this.toNodeId = toNodeId;
            this.weight = weight;
        }
    }

    private static final class Entry {
        final long id;
        final double f;

        Entry(long id, double f) {
            this.id = id;
            this.f = f;
        }
    }

    /**
     * Отрезок между двумя видимыми точками «стягивается» на этот процент
     * с каждого конца перед проверкой пересечения с FORBIDDEN-буфером.
     * Смягчает численные погрешности на касательных (линия проходит
     * ровно через угол полигона), чтобы {@code crosses} не давал
     * ложное срабатывание из-за floating-point-погрешности
     */
    private static final double VISIBILITY_EPSILON_RATIO = 0.001;

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();
    private static final int UTM_SRID = 32637;

    private static final long START_ID = -1L;
    private static final long END_ID = -2L;

    private final ObstacleModel obstacleModel;

    private final List<GraphNode> nodes = new ArrayList<>();
    private final Map<Long, List<Edge>> adjacency = new HashMap<>();
    private long nextNodeId = 0;
    private boolean built = false;

    public VisibilityGraph(ObstacleModel obstacleModel) {
        this.obstacleModel = obstacleModel;
    }

    /**
     * Строит узлы (углы FORBIDDEN/SPECIAL буферов) и статические ребра
     * между ними. Вызывается один раз на загрузку. Сложность O(n²) по
     * числу углов - приемлемо для конкурсного масштаба данных
     */
    public void build() {
        nodes.clear();
        adjacency.clear();
        nextNodeId = 0;

        for (ObstacleModel.ForbiddenZone zone : obstacleModel.getForbiddenZones()) {
            addCornersOf(zone.getBufferedGeometryUtm(), GraphNode.Kind.FORBIDDEN_CORNER);
        }
        for (ObstacleModel.SpecialZone zone : obstacleModel.getSpecialZones()) {
            addCornersOf(zone.getBufferedGeometryUtm(), GraphNode.Kind.SPECIAL_CORNER);
        }

        for (int i = 0; i < nodes.size(); i++) {
            GraphNode a = nodes.get(i);
            for (int j = i + 1; j < nodes.size(); j++) {
                GraphNode b = nodes.get(j);
                if (!isVisible(a.getX(), a.getY(), b.getX(), b.getY(),
                        Collections.emptySet())) {
                    continue;
                }
                double weight = distanceMeters(a.getX(), a.getY(), b.getX(), b.getY())
                        * specialFactorFor(a.getX(), a.getY(), b.getX(), b.getY());
                addEdge(a.getId(), b.getId(), weight);
                addEdge(b.getId(), a.getId(), weight);
            }
        }

        built = true;
    }

    /**
     * Ищет кратчайший путь между {@code start} и {@code end}, без
     * исключений из FORBIDDEN-зон
     */
    public PathResult shortestPath(Coordinate startUtm, Coordinate endUtm) {
        return shortestPath(startUtm, endUtm, Collections.emptySet());
    }

    /**
     * Ищет кратчайший путь между {@code start} и {@code end} с
     * возможностью игнорировать указанные FORBIDDEN-зоны для ребер,
     * инцидентных {@code start}
     * <p>Так реализуется правило ТП: финальный прямой участок от точки
     * ОКС до границы её собственного полигона ОКС не проверяется на
     * отступ к этому полигону
     * @param startUtm                    координата ОКС (UTM zone 37N)
     * @param endUtm                      координата точки врезки (UTM zone 37N)
     * @param ignoredForbiddenIdsForStart id запретных зон, игнорируемых
     *                                    при проверке видимости от
     *                                    {@code start}; может быть пустым
     * @return путь или {@link PathResult#notFound()}
     */
    public PathResult shortestPath(Coordinate startUtm, Coordinate endUtm,
                                    Set<Long> ignoredForbiddenIdsForStart) {
        if (!built) {
            throw new IllegalStateException(
                    "VisibilityGraph.build() должен быть вызван перед shortestPath()");
        }
        Set<Long> ignored = ignoredForbiddenIdsForStart != null
                ? ignoredForbiddenIdsForStart
                : Collections.emptySet();

        Map<Long, Double> startEdges = visibleEdgesFrom(startUtm.x, startUtm.y, ignored);
        Map<Long, Double> endEdges = visibleEdgesFrom(endUtm.x, endUtm.y,
                Collections.emptySet());
        boolean directVisible = isVisible(startUtm.x, startUtm.y, endUtm.x, endUtm.y,
                ignored);
        double directWeight = directVisible
                ? distanceMeters(startUtm.x, startUtm.y, endUtm.x, endUtm.y)
                        * specialFactorFor(startUtm.x, startUtm.y, endUtm.x, endUtm.y)
                : Double.POSITIVE_INFINITY;

        Map<Long, Coordinate> coordOf = new HashMap<>();
        coordOf.put(START_ID, startUtm);
        coordOf.put(END_ID, endUtm);
        for (GraphNode n : nodes) {
            coordOf.put(n.getId(), new Coordinate(n.getX(), n.getY()));
        }

        Map<Long, Double> gScore = new HashMap<>();
        Map<Long, Long> cameFrom = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        PriorityQueue<Entry> open = new PriorityQueue<>(Comparator.comparingDouble(e -> e.f));

        gScore.put(START_ID, 0.0);
        open.add(new Entry(START_ID, startUtm.distance(endUtm)));

        while (!open.isEmpty()) {
            Entry current = open.poll();
            if (current.id == END_ID) {
                return reconstructPath(cameFrom, coordOf, gScore.get(END_ID));
            }
            if (!closed.add(current.id)) {
                continue;
            }
            for (Edge edge : neighborsOf(current.id, startEdges, endEdges, directWeight)) {
                if (closed.contains(edge.toNodeId)) {
                    continue;
                }
                double tentativeG = gScore.getOrDefault(current.id,
                        Double.POSITIVE_INFINITY) + edge.weight;
                if (tentativeG < gScore.getOrDefault(edge.toNodeId,
                        Double.POSITIVE_INFINITY)) {
                    gScore.put(edge.toNodeId, tentativeG);
                    cameFrom.put(edge.toNodeId, current.id);
                    double f = tentativeG
                            + coordOf.get(edge.toNodeId).distance(endUtm);
                    open.add(new Entry(edge.toNodeId, f));
                }
            }
        }

        return PathResult.notFound();
    }

    // Построение узлов

    private void addCornersOf(org.locationtech.jts.geom.Geometry bufferedGeometryUtm,
                               GraphNode.Kind kind) {
        Coordinate[] coords = bufferedGeometryUtm.getCoordinates();
        Set<Coordinate> seen = new LinkedHashSet<>();
        for (Coordinate c : coords) {
            if (seen.add(c)) {
                nodes.add(new GraphNode(nextNodeId++, c.x, c.y, kind, null));
            }
        }
    }

    private void addEdge(long fromId, long toId, double weight) {
        adjacency.computeIfAbsent(fromId, k -> new ArrayList<>())
                .add(new Edge(toId, weight));
    }

    // Видимость и веса

    /**
     * Проверяет, видна ли точка B из точки A: отрезок A-B не должен
     * <b>проходить сквозь внутренность</b> ни одного FORBIDDEN-буфера
     * (кроме тех, чьи {@code sourceRestrictionId} присутствуют в
     * {@code ignoredForbiddenIds})
     * <p>Используется {@link LineString#crosses(org.locationtech.jts.geom.Geometry)}:
     * он возвращает {@code true}, только если линия входит во внутренность
     * полигона и выходит из нее. Касание границы (движение вдоль ребра
     * или через угол) не считается пересечением и допускается - это
     * необходимо для обхода препятствий, где маршрут идёт по контуру
     */
    private boolean isVisible(double ax, double ay, double bx, double by,
                               Set<Long> ignoredForbiddenIds) {
        LineString segment = shrunkSegment(ax, ay, bx, by);
        for (ObstacleModel.ForbiddenZone zone : obstacleModel.getForbiddenZones()) {
            Long id = zone.getSourceRestrictionId();
            if (id != null && ignoredForbiddenIds.contains(id)) {
                continue;
            }
            if (segment.crosses(zone.getBufferedGeometryUtm())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Возвращает максимальный Kспец среди SPECIAL-зон, пересекаемых
     * отрезком A-B (1.0, если отрезок не входит ни в одну спецзону)
     */
    private double specialFactorFor(double ax, double ay, double bx, double by) {
        LineString segment = rawSegment(ax, ay, bx, by);
        double maxKspets = 1.0;
        for (ObstacleModel.SpecialZone zone : obstacleModel.getSpecialZones()) {
            if (segment.intersects(zone.getBufferedGeometryUtm())) {
                maxKspets = Math.max(maxKspets, zone.getKspets());
            }
        }
        return maxKspets;
    }

    private Map<Long, Double> visibleEdgesFrom(double x, double y,
                                                Set<Long> ignoredForbiddenIds) {
        Map<Long, Double> result = new HashMap<>();
        for (GraphNode n : nodes) {
            if (!isVisible(x, y, n.getX(), n.getY(), ignoredForbiddenIds)) {
                continue;
            }
            double weight = distanceMeters(x, y, n.getX(), n.getY())
                    * specialFactorFor(x, y, n.getX(), n.getY());
            result.put(n.getId(), weight);
        }
        return result;
    }

    private List<Edge> neighborsOf(long id, Map<Long, Double> startEdges,
                                    Map<Long, Double> endEdges, double directWeight) {
        List<Edge> result = new ArrayList<>();
        if (id == START_ID) {
            startEdges.forEach((nodeId, w) -> result.add(new Edge(nodeId, w)));
            if (Double.isFinite(directWeight)) {
                result.add(new Edge(END_ID, directWeight));
            }
            return result;
        }
        if (id == END_ID) {
            endEdges.forEach((nodeId, w) -> result.add(new Edge(nodeId, w)));
            if (Double.isFinite(directWeight)) {
                result.add(new Edge(START_ID, directWeight));
            }
            return result;
        }
        result.addAll(adjacency.getOrDefault(id, List.of()));
        Double toStart = startEdges.get(id);
        if (toStart != null) {
            result.add(new Edge(START_ID, toStart));
        }
        Double toEnd = endEdges.get(id);
        if (toEnd != null) {
            result.add(new Edge(END_ID, toEnd));
        }
        return result;
    }

    // Геометрические утилиты и A*

    private double distanceMeters(double ax, double ay, double bx, double by) {
        return Math.hypot(bx - ax, by - ay);
    }

    private LineString rawSegment(double ax, double ay, double bx, double by) {
        LineString ls = GEOMETRY_FACTORY.createLineString(
                new Coordinate[]{new Coordinate(ax, ay), new Coordinate(bx, by)});
        ls.setSRID(UTM_SRID);
        return ls;
    }

    private LineString shrunkSegment(double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double sx = ax + dx * VISIBILITY_EPSILON_RATIO;
        double sy = ay + dy * VISIBILITY_EPSILON_RATIO;
        double ex = bx - dx * VISIBILITY_EPSILON_RATIO;
        double ey = by - dy * VISIBILITY_EPSILON_RATIO;
        LineString ls = GEOMETRY_FACTORY.createLineString(
                new Coordinate[]{new Coordinate(sx, sy), new Coordinate(ex, ey)});
        ls.setSRID(UTM_SRID);
        return ls;
    }

    private PathResult reconstructPath(Map<Long, Long> cameFrom,
                                        Map<Long, Coordinate> coordOf,
                                        double totalWeight) {
        List<Coordinate> reversed = new ArrayList<>();
        long current = END_ID;
        reversed.add(coordOf.get(current));
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            reversed.add(coordOf.get(current));
        }
        List<Coordinate> path = new ArrayList<>(reversed);
        Collections.reverse(path);
        return new PathResult(path, totalWeight, true);
    }
}
