package ru.moscow.heat.trace.graph;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Граф видимости и A* по нему
 * <p>Работает в UTM zone 37N. Узлы графа — только углы FORBIDDEN-буферов.
 * Углы SPECIAL-зон в граф не добавляются: спецзоны не препятствия, они
 * лишь домножают вес ребер на Kспец
 * <p>Производительность построена на трех оптимизациях:
 * <ol>
 *     <li>углы дедуплицируются по spatial grid с шагом
 *     {@link #NODE_DEDUP_GRID_M};</li>
 *     <li>проверки видимости используют {@link ObstacleModel#findForbiddenNear}
 *     (R-tree) вместо перебора всех зон;</li>
 *     <li>внутри проверки - envelope prefilter + PreparedGeometry.crosses.</li>
 * </ol>
 */
public final class VisibilityGraph {

    /**
     * Результат поиска пути: последовательность точек в UTM, суммарный вес
     * (с учетом Kспец) и флаг успеха. Иммутабельный DTO
     */
    public static final class PathResult {

        /** Последовательность точек пути в UTM от старта к концу */
        private final List<Coordinate> pathUtm;

        /** Суммарный вес пути (длина × Kспец по всем сегментам) */
        private final double totalWeight;

        /** Признак успеха: {@code true}, если путь найден */
        private final boolean found;

        /**
         * Создаёт результат поиска пути
         * @param pathUtm     последовательность точек пути (UTM)
         * @param totalWeight суммарный вес пути
         * @param found       {@code true}, если путь найден
         */
        public PathResult(List<Coordinate> pathUtm, double totalWeight, boolean found) {
            this.pathUtm = pathUtm;
            this.totalWeight = totalWeight;
            this.found = found;
        }

        /**
         * @return последовательность точек пути в UTM
         * (пустой список, если не найден)
         * */
        public List<Coordinate> getPathUtm() {
            return pathUtm;
        }

        /**
         * @return суммарный вес пути (с учётом Kспец); {@code +∞}, если
         * путь не найден
         * */
        public double getTotalWeight() {
            return totalWeight;
        }

        /** @return {@code true}, если путь найден */
        public boolean isFound() {
            return found;
        }

        /**
         * Создаёт результат «путь не найден» с пустым списком точек и
         * бесконечным весом
         * @return результат-заглушка для отсутствующего пути
         */
        public static PathResult notFound() {
            return new PathResult(List.of(), Double.POSITIVE_INFINITY, false);
        }
    }

    /**
     * Ребро графа: идентификатор целевого узла и вес (длина × Kспец)
     */
    private static final class Edge {

        /** ID узла, в который ведет ребро */
        final long toNodeId;

        /** Вес ребра: длина сегмента, умноженная на Kспец пересеченных спецзон */
        final double weight;

        /**
         * @param toNodeId ID целевого узла
         * @param weight   вес ребра
         */
        Edge(long toNodeId, double weight) {
            this.toNodeId = toNodeId;
            this.weight = weight;
        }
    }

    /**
     * Элемент очереди с приоритетом в A*: ID узла и значение f-оценки
     */
    private static final class Entry {

        /** ID узла графа */
        final long id;

        /** Оценка f = g + h (пройденный путь плюс эвристика до цели) */
        final double f;

        /**
         * @param id ID узла
         * @param f  значение f-оценки
         */
        Entry(long id, double f) {
            this.id = id;
            this.f = f;
        }
    }

    /**
     * Коэффициент «стягивания» сегмента с обоих концов перед проверкой
     * пересечения с FORBIDDEN-буфером. Смягчает численные погрешности на
     * касательных: если линия проходит ровно через угол полигона,
     * floating-point может «решить», что она заходит внутрь на микродоли
     */
    private static final double VISIBILITY_EPSILON_RATIO = 0.001;

    /** Шаг сетки дедупликации углов, метры */
    private static final double NODE_DEDUP_GRID_M = 0.5;

    /** Фабрика JTS для построения служебных сегментов */
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    /** SRID метрической системы UTM zone 37N */
    private static final int UTM_SRID = 32637;

    /** Идентификатор виртуального узла-старта в графе */
    private static final long START_ID = -1L;

    /** Идентификатор виртуального узла-финиша в графе */
    private static final long END_ID = -2L;

    /** Модель препятствий для текущей загрузки */
    private final ObstacleModel obstacleModel;

    /** Узлы графа — углы FORBIDDEN-буферов */
    private final List<GraphNode> nodes = new ArrayList<>();

    /** Список смежности: ID узла → список исходящих ребер */
    private final Map<Long, List<Edge>> adjacency = new HashMap<>();

    /** Счетчик для выдачи новых ID узлам при построении */
    private long nextNodeId = 0;

    /** Флаг готовности графа: {@code true} после {@link #build()} */
    private boolean built = false;

    /**
     * Создает граф для заданной модели препятствий.
     * Сам граф строится отдельно через {@link #build()}
     * @param obstacleModel модель препятствий (UTM)
     */
    public VisibilityGraph(ObstacleModel obstacleModel) {
        this.obstacleModel = obstacleModel;
    }

    /**
     * Строит узлы и статические ребра. Вызывается один раз на загрузку.
     * <p>Узлы - углы FORBIDDEN-буферов, дедуплицированные по spatial grid.
     * Рёбра строятся для всех пар видимых узлов; для каждой пары
     * проверяется, что отрезок не проходит сквозь внутренность ни одного
     * FORBIDDEN-буфера. Вес ребра — длина × Kспец
     */
    public void build() {
        nodes.clear();
        adjacency.clear();
        nextNodeId = 0;

        Map<Long, Boolean> occupied = new HashMap<>();
        for (ObstacleModel.ForbiddenZone zone : obstacleModel.getForbiddenZones()) {
            addCornersOf(zone.getBufferedGeometryUtm(), occupied);
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
     * Ищет кратчайший путь между {@code start} и {@code end} без исключений
     * из FORBIDDEN-зон. Эквивалентно вызову
     * {@link #shortestPath(Coordinate, Coordinate, Set)} с пустым набором
     * @param startUtm координата старта (UTM zone 37N)
     * @param endUtm   координата финиша (UTM zone 37N)
     * @return найденный путь или {@link PathResult#notFound()}
     */
    public PathResult shortestPath(Coordinate startUtm, Coordinate endUtm) {
        return shortestPath(startUtm, endUtm, Collections.emptySet());
    }

    /**
     * Ищет кратчайший путь между {@code start} и {@code end} с возможностью
     * игнорировать указанные FORBIDDEN-зоны <>только для ребер,
     * инцидентных {@code start}
     * <p>Так реализуется правило ТП: финальный прямой участок от точки ОКС
     * до границы её собственного полигона ОКС не проверяется на отступ
     * к этому полигону (разъяснения п. 3)
     * @param startUtm                    координата ОКС (UTM zone 37N)
     * @param endUtm                      координата точки врезки (UTM zone 37N)
     * @param ignoredForbiddenIdsForStart id запретных зон, игнорируемых
     *                                    при проверке видимости от
     *                                    {@code start}; может быть пустым
     * @return путь или {@link PathResult#notFound()}
     * @throws IllegalStateException если {@link #build()} не был вызван
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

    /**
     * Добавляет углы буфера, дедуплицируя по spatial grid с шагом
     * {@link #NODE_DEDUP_GRID_M}. Углы, попавшие в одну ячейку с уже
     * добавленным, пропускаются
     * @param bufferedGeometryUtm геометрия буфера (UTM)
     * @param occupied            карта занятых ячеек сетки (мутируется)
     */
    private void addCornersOf(org.locationtech.jts.geom.Geometry bufferedGeometryUtm,
                               Map<Long, Boolean> occupied) {
        Coordinate[] coords = bufferedGeometryUtm.getCoordinates();
        for (Coordinate c : coords) {
            long key = cellKey(c.x, c.y);
            if (occupied.containsKey(key)) {
                continue;
            }
            occupied.put(key, Boolean.TRUE);
            nodes.add(new GraphNode(nextNodeId++, c.x, c.y,
                    GraphNode.Kind.FORBIDDEN_CORNER, null));
        }
    }

    /**
     * Вычисляет ключ ячейки spatial grid для точки.
     * Две точки в одной ячейке считаются «совпадающими»
     * @param x координата X в UTM
     * @param y координата Y в UTM
     * @return целочисленный ключ ячейки
     */
    private long cellKey(double x, double y) {
        long cx = Math.round(x / NODE_DEDUP_GRID_M);
        long cy = Math.round(y / NODE_DEDUP_GRID_M);
        return (cx << 32) ^ (cy & 0xffffffffL);
    }

    /**
     * Добавляет ребро в список смежности
     * @param fromId ID узла-источника
     * @param toId   ID узла-назначения
     * @param weight вес ребра (длина × Kспец)
     */
    private void addEdge(long fromId, long toId, double weight) {
        adjacency.computeIfAbsent(fromId, k -> new ArrayList<>())
                .add(new Edge(toId, weight));
    }

    /**
     * Проверяет, видна ли точка B из точки A: отрезок A-B не должен
     * проходить <b>сквозь внутренность</b> ни одного FORBIDDEN-буфера
     * (кроме тех, чьи {@code sourceRestrictionId} присутствуют в
     * {@code ignoredForbiddenIds})
     * <p>Использует трёхуровневую защиту по производительности:
     * <ol>
     *     <li>R-tree из {@link ObstacleModel} возвращает только близкие зоны;</li>
     *     <li>envelope prefilter отсекает заведомо не пересекающиеся пары;</li>
     *     <li>{@code PreparedGeometry.crosses} — JTS-индекс для полигона.</li>
     * </ol>
     *
     * @param ax                  X начала отрезка (UTM)
     * @param ay                  Y начала отрезка (UTM)
     * @param bx                  X конца отрезка (UTM)
     * @param by                  Y конца отрезка (UTM)
     * @param ignoredForbiddenIds id зон, игнорируемых при проверке
     * @return {@code true}, если отрезок не проходит сквозь внутренность
     *         ни одной запретной зоны
     */
    private boolean isVisible(double ax, double ay, double bx, double by,
                               Set<Long> ignoredForbiddenIds) {
        LineString segment = shrunkSegment(ax, ay, bx, by);
        LineString rawSeg = rawSegment(ax, ay, bx, by);
        Envelope env = rawSeg.getEnvelopeInternal();
        for (ObstacleModel.ForbiddenZone zone : obstacleModel.findForbiddenNear(env)) {
            Long id = zone.getSourceRestrictionId();
            if (id != null && ignoredForbiddenIds.contains(id)) {
                continue;
            }
            if (!env.intersects(zone.getEnvelope())) {
                continue;
            }
            // 1. Физическое тело здания / препятствия: пересечение категорически запрещено
            if (zone.getPreparedSourceGeometry() != null) {
                if (zone.getPreparedSourceGeometry().intersects(rawSeg)) {
                    return false;
                }
            } else if (zone.getSourceGeometryUtm() != null) {
                if (zone.getSourceGeometryUtm().intersects(rawSeg)) {
                    return false;
                }
            }
            // 2. Буферная зона: транзитное пересечение запрещено
            if (zone.getPreparedGeometry().crosses(segment)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Возвращает максимальный Kспец среди SPECIAL-зон, пересекаемых
     * отрезком A-B
     * <p>При наложении нескольких зон берётся максимум (план, шаг 7:
     * «Kспец = max, не сумма и не произведение»). Используется
     * {@code intersects} намеренно: касание границы спецзоны означает, что
     * участок прокладывается в ее границах и облагается Kспец
     * @param ax X начала отрезка (UTM)
     * @param ay Y начала отрезка (UTM)
     * @param bx X конца отрезка (UTM)
     * @param by Y конца отрезка (UTM)
     * @return максимальный Kспец среди пересечённых зон; 1.0, если ни одна
     *         зона не задета
     */
    private double specialFactorFor(double ax, double ay, double bx, double by) {
        LineString segment = rawSegment(ax, ay, bx, by);
        Envelope env = segment.getEnvelopeInternal();
        double maxKspets = 1.0;
        for (ObstacleModel.SpecialZone zone : obstacleModel.findSpecialNear(env)) {
            if (!env.intersects(zone.getEnvelope())) {
                continue;
            }
            if (zone.getPreparedGeometry().intersects(segment)) {
                maxKspets = Math.max(maxKspets, zone.getKspets());
            }
        }
        return maxKspets;
    }

    /**
     * Возвращает карту видимых узлов из заданной точки с весами
     * соответствующих ребер
     * @param x                   X точки (UTM)
     * @param y                   Y точки (UTM)
     * @param ignoredForbiddenIds id зон, игнорируемых при проверке
     * @return карта {@code nodeId → weight} для всех видимых узлов
     */
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

    /**
     * Возвращает список исходящих ребер для узла A*
     * <p>Для виртуальных узлов {@link #START_ID} и {@link #END_ID}
     * используются динамические списки {@code startEdges}/{@code endEdges}
     * и, если применимо, прямое ребро между стартом и финишем. Для обычных
     * узлов - статический список смежности плюс динамические рёбра в
     * старт/финиш
     * @param id           ID текущего узла
     * @param startEdges   карта видимых узлов из старта
     * @param endEdges     карта видимых узлов из финиша
     * @param directWeight вес прямого ребра старт↔финиш, либо
     *                     {@code +∞}, если прямое ребро не видно
     * @return список исходящих ребер
     */
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
        if (toStart != null) result.add(new Edge(START_ID, toStart));
        Double toEnd = endEdges.get(id);
        if (toEnd != null) result.add(new Edge(END_ID, toEnd));
        return result;
    }

    /**
     * Евклидово расстояние между двумя точками (метры UTM)
     * @param ax X первой точки
     * @param ay Y первой точки
     * @param bx X второй точки
     * @param by Y второй точки
     * @return расстояние в метрах
     */
    private double distanceMeters(double ax, double ay, double bx, double by) {
        return Math.hypot(bx - ax, by - ay);
    }

    /**
     * Строит «сырой» сегмент между двумя точками (без стягивания).
     * Используется для проверок SPECIAL-зон, где касание границы считается
     * попаданием в зону
     * @param ax X начала
     * @param ay Y начала
     * @param bx X конца
     * @param by Y конца
     * @return линия из двух точек с SRID 32637
     */
    private LineString rawSegment(double ax, double ay, double bx, double by) {
        LineString ls = GEOMETRY_FACTORY.createLineString(
                new Coordinate[]{new Coordinate(ax, ay), new Coordinate(bx, by)});
        ls.setSRID(UTM_SRID);
        return ls;
    }

    /**
     * Строит сегмент, «стянутый» с обоих концов на
     * {@link #VISIBILITY_EPSILON_RATIO} длины. Применяется в проверках
     * FORBIDDEN-зон, чтобы избежать ложных срабатываний
     * {@code PreparedGeometry.crosses} на касательных
     * @param ax X начала
     * @param ay Y начала
     * @param bx X конца
     * @param by Y конца
     * @return стянутая линия с SRID 32637
     */
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

    /**
     * Восстанавливает путь по карте {@code cameFrom} и таблице координат,
     * разворачивая его в направлении старт → финиш
     * @param cameFrom    карта {@code nodeId → predecessor}
     * @param coordOf     карта {@code nodeId → Coordinate}
     * @param totalWeight итоговый вес пути (берётся из {@code gScore})
     * @return готовый {@link PathResult}
     */
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
