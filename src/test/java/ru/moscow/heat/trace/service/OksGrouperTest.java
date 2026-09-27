package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OksGrouperTest {

    private static final GeometryFactory GF = new GeometryFactory();

    @Mock
    private CoordinateTransformService transformService;

    private OksGrouper grouper;

    @BeforeEach
    void setUp() {
        grouper = new OksGrouper(transformService);
    }

    // === Вспомогательные методы ===

    private OksConnectionPointEntity oks(String featureId, double flowTph) {
        // Создаём минимальную сущность через рефлексию или мок
        // В реальном проекте — конструктор/билдер сущности
        OksConnectionPointEntity entity = new OksConnectionPointEntity();
        entity.setFeatureId(featureId);
        entity.setFlowTph(flowTph);
        return entity;
    }

    private TieInCandidate chamberCandidate(String id, String chamberId,
                                            double lon, double lat) {
        return TieInCandidate.builder()
                .id(id)
                .connectionPointId(id)
                .type(TieInType.EXISTING_CHAMBER)
                .existingChamberId(chamberId)
                .tieInLongitude(lon)
                .tieInLatitude(lat)
                .targetLongitude(lon)
                .targetLatitude(lat)
                .build();
    }

    private TieInCandidate newChamberCandidate(String id, double lon, double lat) {
        return TieInCandidate.builder()
                .id(id)
                .connectionPointId(id)
                .type(TieInType.NEW_CHAMBER)
                .tieInLongitude(lon)
                .tieInLatitude(lat)
                .targetLongitude(lon)
                .targetLatitude(lat)
                .build();
    }

    private void mockUtmTransform(double lon, double lat, double utmX, double utmY) {
        Point utmPoint = GF.createPoint(new Coordinate(utmX, utmY));
        when(transformService.toUtm(any(Geometry.class))).thenReturn(utmPoint);
    }

    // === Тесты: камерная группировка ===

    @Test
    @DisplayName("Два ОКС с одной камерой → одна группа")
    void twoOksSameChamber_oneGroup() {
        var oks1 = oks("oks-1", 20.0);
        var oks2 = oks("oks-2", 30.0);
        var c1 = chamberCandidate("c1", "chamber-A", 37.63, 55.69);
        var c2 = chamberCandidate("c2", "chamber-A", 37.63, 55.69);

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
        var oks1 = oks("oks-1", 20.0);
        var oks2 = oks("oks-2", 30.0);
        var oks3 = oks("oks-3", 15.0);
        var c1 = chamberCandidate("c1", "chamber-A", 37.63, 55.69);
        var c2 = chamberCandidate("c2", "chamber-A", 37.63, 55.69);
        var c3 = chamberCandidate("c3", "chamber-B", 37.64, 55.70);

        Map<String, TieInCandidate> candidates = new HashMap<>();
        candidates.put("oks-1", c1);
        candidates.put("oks-2", c2);
        candidates.put("oks-3", c3);

        List<OksGroup> groups = grouper.group(List.of(oks1, oks2, oks3), candidates);

        // chamber-A: 2 ОКС → группа; chamber-B: 1 ОКС → одиночный
        assertThat(groups).hasSize(2);
        OksGroup multi = groups.stream().filter(OksGroup::isMulti).findFirst().orElseThrow();
        assertThat(multi.size()).isEqualTo(2);
        assertThat(multi.isGroupedByChamber()).isTrue();
    }

    @Test
    @DisplayName("Один ОКС с камерой → одиночная группа")
    void singleOksWithChamber_singletonGroup() {
        var oks1 = oks("oks-1", 20.0);
        var c1 = chamberCandidate("c1", "chamber-A", 37.63, 55.69);

        Map<String, TieInCandidate> candidates = Map.of("oks-1", c1);

        List<OksGroup> groups = grouper.group(List.of(oks1), candidates);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).size()).isEqualTo(1);
        assertThat(groups.get(0).isMulti()).isFalse();
    }

    // === Тесты: радиусная группировка ===

    @Test
    @DisplayName("Два ОКС с tie-in в пределах 30 м → одна радиусная группа")
    void twoOksWithinRadius_oneGroup() {
        // UTM координаты: 10 м друг от друга
        mockUtmTransformForIndex(0, 396000.0, 6174000.0);
        mockUtmTransformForIndex(1, 396010.0, 6174000.0);

        var oks1 = oks("oks-1", 20.0);
        var oks2 = oks("oks-2", 30.0);
        var c1 = newChamberCandidate("c1", 37.6300, 55.6900);
        var c2 = newChamberCandidate("c2", 37.6301, 55.6900);

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
        // UTM координаты: 50 м друг от друга
        mockUtmTransformForIndex(0, 396000.0, 6174000.0);
        mockUtmTransformForIndex(1, 396050.0, 6174000.0);

        var oks1 = oks("oks-1", 20.0);
        var oks2 = oks("oks-2", 30.0);
        var c1 = newChamberCandidate("c1", 37.6300, 55.6900);
        var c2 = newChamberCandidate("c2", 37.6350, 55.6900);

        Map<String, TieInCandidate> candidates = new HashMap<>();
        candidates.put("oks-1", c1);
        candidates.put("oks-2", c2);

        List<OksGroup> groups = grouper.group(List.of(oks1, oks2), candidates);

        assertThat(groups).hasSize(2);
        assertThat(groups).allMatch(g -> g.size() == 1);
    }

    // === Тесты: смешанные сценарии ===

    @Test
    @DisplayName("Пустой список ОКС → пустой результат")
    void emptyOksList_emptyResult() {
        List<OksGroup> groups = grouper.group(List.of(), Map.of());
        assertThat(groups).isEmpty();
    }

    @Test
    @DisplayName("ОКС без кандидата не попадает ни в одну группу")
    void oksWithoutCandidate_excluded() {
        var oks1 = oks("oks-1", 20.0);
        var oks2 = oks("oks-2", 30.0);
        var c1 = chamberCandidate("c1", "chamber-A", 37.63, 55.69);

        // oks-2 не имеет кандидата
        Map<String, TieInCandidate> candidates = Map.of("oks-1", c1);

        List<OksGroup> groups = grouper.group(List.of(oks1, oks2), candidates);

        // Только один ОКС с кандидатом → одиночная группа
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).size()).isEqualTo(1);
    }

    // === Вспомогательное для моков с разными UTM ===

    private int transformCallIndex = 0;
    private double[][] utmCoords = new double[10][2];

    private void mockUtmTransformForIndex(int index, double x, double y) {
        utmCoords[index] = new double[]{x, y};
        // Мок будет вызываться последовательно; для простоты используем
        // thenAnswer с счётчиком
        when(transformService.toUtm(any(Geometry.class)))
                .thenAnswer(inv -> {
                    int idx = transformCallIndex++;
                    return GF.createPoint(new Coordinate(utmCoords[idx][0], utmCoords[idx][1]));
                });
    }
}