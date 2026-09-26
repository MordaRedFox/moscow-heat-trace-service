package ru.moscow.heat.trace.graph;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import java.util.ArrayList;
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
 * Kспец) пути A* по нему (план, п.2.2 и шаги 4-5).
 * <p>
 * Работает целиком в UTM zone 37N (EPSG:32637), метры — как и
 * {@link ObstacleModel}. Никакой трансформации координат здесь нет:
 * все проверки пересечения — обычный JTS {@code Geometry.intersects()},
 * все расстояния — евклидова метрика в этой же метрической плоскости.
 * Поэтому граф не зависит от {@code GeometryUtils}/{@code CoordinateTransformService}.
 * <p>
 * Узлы (построены один раз в {@link #build()}): углы буферов FORBIDDEN и
 * SPECIAL_CROSSING зон из {@link ObstacleModel}. Существующие тепловые сети
 * как узлы графа в MVP не добавляются (см. {@link GraphNode.Kind#EXISTING_NETWORK_ENDPOINT} —
 * зарезервировано на будущее).
 * <p>
 * Рёбра: пара узлов видима, если отрезок между ними не пересекает ни один
 * FORBIDDEN-буфер ({@link #isVisible}). Пересечение SPECIAL-зоны допустимо,
 * вес ребра при этом домножается на максимальный Kспец среди пересечённых
 * зон ({@link #specialFactorFor}).
 * <p>
 * MVP-упрощение: угол пересечения road/tram_tracks (≥45°, риск R2 плана)
 * в этой версии графа НЕ проверяется при построении рёбер — веса и
 * видимость считаются без него. Проверку угла нужно делать постфактум
 * над найденным путём (см. {@code AngleChecker} в пакете trace/service)
 * и при нарушении локально переставлять точку через границу зоны.
 * <p>
 * Не является Spring-бином: создаётся оркестратором на каждую загрузку
 * (`new VisibilityGraph(obstacleModel)`), граф строится один раз
 * ({@link #build()}) и переиспользуется для всех ОКС загрузки.
 */
public final class VisibilityGraph {

    /** Результат поиска пути: последовательность точек UTM + суммарный вес (с учётом Kспец). */
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
     * Отрезок между двумя видимыми точками "стягивается" на этот процент
     * с каждого конца перед проверкой пересечения с FORBIDDEN-буфером.
     * Без этого смежные углы ОДНОГО И ТОГО ЖЕ полигона (пара, образующая
     * его же ребро) будут считаться "невидимыми" из-за касания в общей
     * вершине — {@code Geometry.intersects()} расценивает касание как
     * пересечение. Это прагматичное MVP-решение, не строгая геометрия
     * видимости (см. TODO в {@link #isVisible}).
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
     * Строит узлы (углы FORBIDDEN/SPECIAL буферов) и статические рёбра
     * между ними. Вызывается один раз на загрузку (план, шаг 4).
     * Сложность O(n^2) по числу углов — приемлемо для конкурсного
     * масштаба данных (риск R1 плана).
     */
    public void build() {
        nodes.clear();
        adjacency.clear();
        nextNodeId = 0;

        for (Geometry g : obstacleModel.getForbiddenBufferedGeometriesUtm()) {
            addCornersOf(g, GraphNode.Kind.FORBIDDEN_CORNER);
        }
        for (ObstacleModel.SpecialZone zone : obstacleModel.getSpecialZones()) {
            addCornersOf(zone.getBufferedGeometryUtm(), GraphNode.Kind.SPECIAL_CORNER);
        }

        for (int i = 0; i < nodes.size(); i++) {
            GraphNode a = nodes.get(i);
            for (int j = i + 1; j < nodes.size(); j++) {
                GraphNode b = nodes.get(j);
                if (!isVisible(a.getX(), a.getY(), b.getX(), b.getY())) {
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
     * Ищет кратчайший (по весу с учётом Kспец) путь между start и end,
     * временно добавляя их как узлы графа (id={@value #START_ID}/{@value #END_ID})
     * и проверяя видимость до всех статических узлов (план, шаг 5,
     * A* с евклидовой эвристикой).
     *
     * @param startUtm координата ОКС, UTM zone 37N (метры)
     * @param endUtm   координата tie-in (камера или точка на сети), UTM zone 37N (метры)
     * @return найденный путь или {@link PathResult#notFound()}, если граф
     * несвязный между start и end
     */
    public PathResult shortestPath(Coordinate startUtm, Coordinate endUtm) {
        if (!built) {
            throw new IllegalStateException("VisibilityGraph.build() должен быть вызван перед shortestPath()");
        }

        Map<Long, Double> startEdges = visibleEdgesFrom(startUtm.x, startUtm.y);
        Map<Long, Double> endEdges = visibleEdgesFrom(endUtm.x, endUtm.y);
        boolean directVisible = isVisible(startUtm.x, startUtm.y, endUtm.x, endUtm.y);
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
                double tentativeG = gScore.getOrDefault(current.id, Double.POSITIVE_INFINITY) + edge.weight;
                if (tentativeG < gScore.getOrDefault(edge.toNodeId, Double.POSITIVE_INFINITY)) {
                    gScore.put(edge.toNodeId, tentativeG);
                    cameFrom.put(edge.toNodeId, current.id);
                    double f = tentativeG + coordOf.get(edge.toNodeId).distance(endUtm);
                    open.add(new Entry(edge.toNodeId, f));
                }
            }
        }

        return PathResult.notFound();
    }

    // ------------------------------------------------------------------
    // Построение узлов
    // ------------------------------------------------------------------

    private void addCornersOf(Geometry bufferedGeometryUtm, GraphNode.Kind kind) {
        Coordinate[] coords = bufferedGeometryUtm.getCoordinates();
        // Полигон JTS повторяет первую точку в конце кольца — де-дуп через Set
        // убирает эту замыкающую точку и возможные совпадения между кольцами.
        Set<Coordinate> seen = new LinkedHashSet<>();
        for (Coordinate c : coords) {
            if (seen.add(c)) {
                nodes.add(new GraphNode(nextNodeId++, c.x, c.y, kind, null));
            }
        }
    }

    private void addEdge(long fromId, long toId, double weight) {
        adjacency.computeIfAbsent(fromId, k -> new ArrayList<>()).add(new Edge(toId, weight));
    }

    // ------------------------------------------------------------------
    // Видимость и веса
    // ------------------------------------------------------------------

    /**
     * Проверяет, видна ли точка B из точки A: отрезок A-B (слегка
     * "стянутый" с концов, см. {@link #VISIBILITY_EPSILON_RATIO}) не
     * должен пересекать ни один FORBIDDEN-буфер из {@link ObstacleModel}.
     * <p>
     * TODO (не критично для MVP): заменить на строгий visibility-check
     * (например, через relate()/crosses() с анализом расположения
     * соседних рёбер полигона), чтобы не полагаться на epsilon-трюк.
     */
    private boolean isVisible(double ax, double ay, double bx, double by) {
        LineString segment = shrunkSegment(ax, ay, bx, by);
        for (Geometry forbidden : obstacleModel.getForbiddenBufferedGeometriesUtm()) {
            if (segment.intersects(forbidden)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Возвращает максимальный Kспец среди SPECIAL-зон, пересекаемых
     * отрезком A-B (1.0, если отрезок не входит ни в одну спецзону).
     * При наложении нескольких зон Kспец = max (план, шаг 7).
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

    private Map<Long, Double> visibleEdgesFrom(double x, double y) {
        Map<Long, Double> result = new HashMap<>();
        for (GraphNode n : nodes) {
            if (!isVisible(x, y, n.getX(), n.getY())) {
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

    // ------------------------------------------------------------------
    // Геометрические утилиты (чистый JTS, без трансформации координат) и A*
    // ------------------------------------------------------------------

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

    private PathResult reconstructPath(Map<Long, Long> cameFrom, Map<Long, Coordinate> coordOf, double totalWeight) {
        List<Coordinate> reversed = new ArrayList<>();
        long current = END_ID;
        reversed.add(coordOf.get(current));
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            reversed.add(coordOf.get(current));
        }
        List<Coordinate> path = new ArrayList<>(reversed);
        java.util.Collections.reverse(path);
        return new PathResult(path, totalWeight, true);
    }
}
