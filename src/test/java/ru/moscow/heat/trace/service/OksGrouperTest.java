package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.service.CoordinateTransformService;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TieInType;
import ru.moscow.heat.trace.model.OksGroup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * Unit-тесты {@link OksGrouper} (итерация 6, шаг 1).
 *
 * <p>Покрываются:
 * <ul>
 *   <li>группировка по общей существующей камере
 *       (одинаковый {@code existingChamberId});</li>
 *   <li>радиусная группировка по близости tie-in точек
 *       (в пределах {@link OksGrouper#JOINT_TIE_IN_RADIUS_M});</li>
 *   <li>одиночные ОКС, не вошедшие ни в одну группу;</li>
 *   <li>исключение ОКС без кандидатов;</li>
 *   <li>смешанные сценарии (камера + радиус + одиночки).</li>
 * </ul>
 *
 * <p>{@link CoordinateTransformService} подменяется моком: тесты
 * контролируют UTM-координаты tie-in точек, чтобы проверять радиусную
 * группировку детерминированно.
 */
@ExtendWith(MockitoExtension.class)
class OksGrouperTest {

    private static final GeometryFactory GF = new GeometryFactory();

    @Mock
    private CoordinateTransformService transformService;

    private OksGrouper grouper;

    /** Счётчик вызовов toUtm внутри одного теста. */
    private int utmCallIndex = 0;

    /** Последовательность UTM-координат, которую вернёт toUtm. */
    private double[][] utmSequence;

    @BeforeEach
    void setUp() {
        grouper = new OksGrouper(transformService);
        utmCallIndex = 0;
        utmSequence = new double[0][];

        // По умолчанию toUtm возвращает (0,0). lenient() — чтобы не падать
        // в тестах, где радиусная группировка не задействована.
        lenient().when(transformService.toUtm(any(Geometry.class)))
                .thenReturn(GF.createPoint(new Coordinate(0, 0)));
    }

    // ---------- Хелперы ----------

    private OksConnectionPointEntity oks(String featureId, double flowTph) {
        OksConnectionPointEntity entity = new OksConnectionPointEntity();
        entity.setFeatureId(featureId);
        entity.setFlowTph(flowTph);
        return entity;
    }

    private TieInCandidate chamberCandidate(String id, String chamberId) {
        return TieInCandidate.builder()
                .id(id)
                .connectionPointId(id)
                .type(TieInType.EXISTING_CHAMBER)
                .existingChamberId(chamberId)
                .tieInLongitude(0).tieInLatitude(0)
                .targetLongitude(0).targetLatitude(0)
                .distanceToNetworkM(0).distanceToChamberM(0)
                .currentAttachments(0).cost(0)
                .build();
    }

    private TieInCandidate newChamberCandidate(String id) {
        return TieInCandidate.builder()
                .id(id)
                .connectionPointId(id)
                .type(TieInType.NEW_CHAMBER)
                .tieInLongitude(0).tieInLatitude(0)
                .targetLongitude(0).targetLatitude(0)
                .distanceToNetworkM(0).distanceToChamberM(0)
                .currentAttachments(0).cost(0)
                .build();
    }

    /**
     * Задаёт последовательность UTM-координат для последовательных
     * вызовов {@link CoordinateTransformService#toUtm(Geometry)}.
     * Порядок соответствует порядку ОКС в списке, попадающем в
     * радиусную группировку.
     */
    private void mockUtmSequence(double[]... coords) {
        utmSequence = coords;
        // lenient — чтобы stub не считался лишним, если toUtm не вызовется
        lenient().when(transformService.toUtm(any(Geometry.class)))
                .thenAnswer(inv -> {
                    int idx = utmCallIndex++;
                    if (idx >= utmSequence.length) {
                        idx = utmSequence.length - 1;
                    }
                    if (idx < 0) {
                        return GF.createPoint(new Coordinate(0, 0));
                    }
                    return GF.createPoint(new Coordinate(
                            utmSequence[idx][0], utmSequence[idx][1]));
                });
    }

    // ---------- Тесты: камерная группировка ----------

    @Test
    @DisplayName("Два ОКС с одной камерой → одна группа")
    void twoOksSameChamber_oneGroup() {
        var oks1 = oks("oks-1", 20.0);
        var oks2 = oks("oks-2", 30.0);
        var c1 = chamberCandidate("c1", "chamber-A");
        var c2 = chamberCandidate("c2", "chamber-A");

        Map<String, TieInCandidate> candidates = new HashMap<>();
        candidates.put("oks-1", c1);
        candidates.put("oks-2", c2);

        List<OksGroup> groups = grouper.group(List.of(oks1, oks2), candidates);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).size()).isEqualTo(2);
        assertThat(groups.get(0).isGroupedByChamber()).isTrue();
        assertThat(groups.get(0).isMulti()).isTrue();
    }

    @Test
    @DisplayName("Три ОКС: два в одной камере, один в другой → две группы")
    void threeOksDifferentChambers_twoGroups() {
        // oks-3 пойдёт в радиусную группировку → нужен мок toUtm
        mockUtmSequence(new double[]{396000.0, 6174000.0});

        var oks1 = oks("oks-1", 20.0);
        var oks2 = oks("oks-2", 30.0);
        var oks3 = oks("oks-3", 15.0);
        var c1 = chamberCandidate("c1", "chamber-A");
        var c2 = chamberCandidate("c2", "chamber-A");
        var c3 = chamberCandidate("c3", "chamber-B");

        Map<String, TieInCandidate> candidates = new HashMap<>();
        candidates.put("oks-1", c1);
        candidates.put("oks-2", c2);
        candidates.put("oks-3", c3);

        List<OksGroup> groups = grouper.group(List.of(oks1, oks2, oks3), candidates);

        assertThat(groups).hasSize(2);
        OksGroup multi = groups.stream()
                .filter(OksGroup::isMulti).findFirst().orElseThrow();
        assertThat(multi.size()).isEqualTo(2);
        assertThat(multi.isGroupedByChamber()).isTrue();
    }

    @Test
    @DisplayName("Один ОКС с камерой → одиночная группа")
    void singleOksWithChamber_singletonGroup() {
        mockUtmSequence(new double[]{396000.0, 6174000.0});

        var oks1 = oks("oks-1", 20.0);
        var c1 = chamberCandidate("c1", "chamber-A");

        Map<String, TieInCandidate> candidates = Map.of("oks-1", c1);

        List<OksGroup> groups = grouper.group(List.of(oks1), candidates);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).size()).isEqualTo(1);
        assertThat(groups.get(0).isMulti()).isFalse();
    }

    // ---------- Тесты: радиусная группировка ----------

    @Test
    @DisplayName("Два ОКС с tie-in в пределах 30 м → одна радиусная группа")
    void twoOksWithinRadius_oneGroup() {
        mockUtmSequence(
                new double[]{396000.0, 6174000.0},
                new double[]{396010.0, 6174000.0}
        );

        var oks1 = oks("oks-1", 20.0);
        var oks2 = oks("oks-2", 30.0);
        var c1 = newChamberCandidate("c1");
        var c2 = newChamberCandidate("c2");

        Map<String, TieInCandidate> candidates = new HashMap<>();
        candidates.put("oks-1", c1);
        candidates.put("oks-2", c2);

        List<OksGroup> groups = grouper.group(List.of(oks1, oks2), candidates);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).size()).isEqualTo(2);
        assertThat(groups.get(0).isGroupedByChamber()).isFalse();
    }

    @Test
    @DisplayName("Два ОКС с tie-in дальше 30 м → две одиночные группы")
    void twoOksBeyondRadius_twoSingletons() {
        mockUtmSequence(
                new double[]{396000.0, 6174000.0},
                new double[]{396050.0, 6174000.0}
        );

        var oks1 = oks("oks-1", 20.0);
        var oks2 = oks("oks-2", 30.0);
        var c1 = newChamberCandidate("c1");
        var c2 = newChamberCandidate("c2");

        Map<String, TieInCandidate> candidates = new HashMap<>();
        candidates.put("oks-1", c1);
        candidates.put("oks-2", c2);

        List<OksGroup> groups = grouper.group(List.of(oks1, oks2), candidates);

        assertThat(groups).hasSize(2);
        assertThat(groups).allMatch(g -> g.size() == 1);
    }

    // ---------- Тесты: смешанные сценарии ----------

    @Test
    @DisplayName("Пустой список ОКС → пустой результат")
    void emptyOksList_emptyResult() {
        List<OksGroup> groups = grouper.group(List.of(), Map.of());
        assertThat(groups).isEmpty();
    }

    @Test
    @DisplayName("ОКС без кандидата не попадает ни в одну группу")
    void oksWithoutCandidate_excluded() {
        mockUtmSequence(new double[]{396000.0, 6174000.0});

        var oks1 = oks("oks-1", 20.0);
        var oks2 = oks("oks-2", 30.0);
        var c1 = chamberCandidate("c1", "chamber-A");

        // oks-2 не имеет кандидата
        Map<String, TieInCandidate> candidates = Map.of("oks-1", c1);

        List<OksGroup> groups = grouper.group(List.of(oks1, oks2), candidates);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).size()).isEqualTo(1);
    }
}
