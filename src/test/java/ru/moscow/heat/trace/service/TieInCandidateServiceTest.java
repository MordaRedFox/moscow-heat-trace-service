package ru.moscow.heat.trace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.moscow.heat.geojson.entity.HeatChamberEntity;
import ru.moscow.heat.geojson.entity.HeatNetworkEntity;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.repository.HeatChamberRepository;
import ru.moscow.heat.geojson.repository.HeatNetworkRepository;
import ru.moscow.heat.geojson.repository.OksConnectionPointRepository;
import ru.moscow.heat.geojson.service.CoordinateTransformService;
import ru.moscow.heat.spatial.ChamberCostTable;
import ru.moscow.heat.spatial.DiameterTable;
import ru.moscow.heat.spatial.GeometryUtils;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TieInType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Модульные тесты сервиса {@link TieInCandidateService}.
 * Проверяют нормативные правила выбора точек присоединения согласно
 * разделу 3.2 ТП и разъяснениям:
 * <ul>
 *   <li>геометрический поиск ближайшей точки на сегменте теплосети;</li>
 *   <li>учёт камер в радиусе 10 м и отсечение камер за пределами радиуса;</li>
 *   <li>расчёт примыканий (1, 2, 3 участка — подходит, 4 — переполнение);</li>
 *   <li>переключение между EXISTING_CHAMBER и NEW_CHAMBER;</li>
 *   <li>разделение координат tie-in (точка на сети) и target (камера для
 *       EXISTING_CHAMBER, точка на сети для NEW_CHAMBER);</li>
 *   <li>шкала стоимости новой камеры по условным диаметрам.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Модульные тесты сервиса кандидатов (TieInCandidateService)")
class TieInCandidateServiceTest {

    @Mock
    private OksConnectionPointRepository oksConnectionPointRepository;

    @Mock
    private HeatNetworkRepository heatNetworkRepository;

    @Mock
    private HeatChamberRepository heatChamberRepository;

    private GeometryUtils geometryUtils;
    private DiameterTable diameterTable;
    private ChamberCostTable chamberCostTable;
    private TieInCandidateService service;

    private final GeometryFactory gf = new GeometryFactory();
    private final UUID uploadId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        CoordinateTransformService transformService =
                new CoordinateTransformService();
        geometryUtils = new GeometryUtils(transformService);
        diameterTable = new DiameterTable();
        chamberCostTable = new ChamberCostTable();
        service = new TieInCandidateService(
                oksConnectionPointRepository,
                heatNetworkRepository,
                heatChamberRepository,
                geometryUtils,
                diameterTable,
                chamberCostTable);
    }

    /**
     * Проверка поиска ближайшей точки на сегменте сети для точки
     * подключения через {@link GeometryUtils}
     */
    @Test
    @DisplayName("Ближайшая точка на сегменте сети")
    void nearestPointOnSegment() {
        LineString networkLine = gf.createLineString(new Coordinate[]{
                new Coordinate(37.6100, 55.7500),
                new Coordinate(37.6200, 55.7500)
        });
        networkLine.setSRID(4326);

        Point connectionPoint = gf.createPoint(
                new Coordinate(37.6150, 55.7550));
        connectionPoint.setSRID(4326);

        Point nearest = geometryUtils.nearestPointOnGeometry(
                networkLine, connectionPoint);

        assertThat((Object) nearest).isNotNull();
        assertThat(nearest.getX()).isCloseTo(
                37.6150,
                org.assertj.core.data.Offset.offset(0.0002));
        assertThat(nearest.getY()).isCloseTo(
                55.7500,
                org.assertj.core.data.Offset.offset(0.0002));

        double dist = geometryUtils.distanceMeters(
                connectionPoint, nearest);
        assertThat(dist).isGreaterThan(500.0).isLessThan(600.0);
    }

    /**
     * Существующая камера в радиусе 10 м формирует кандидата
     * {@link TieInType#EXISTING_CHAMBER}. Проверяется, что
     * {@code targetXxx} отличается от {@code tieInXxx} — маршрут
     * должен прийти в камеру, а не в точку на сети
     */
    @Test
    @DisplayName("Камера в радиусе 10 м -> EXISTING_CHAMBER, target = камера")
    void chamberWithin10mIsConsidered() {
        OksConnectionPointEntity point = pointAt(37.6150, 55.7501);
        HeatNetworkEntity net = networkAt(37.6100, 37.6200, 300);
        HeatChamberEntity chamber = chamberAt(37.61508, 55.7500);

        when(oksConnectionPointRepository
                .findByUploadIdAndFeatureId(uploadId, "oks-1"))
                .thenReturn(Optional.of(point));
        when(heatNetworkRepository.findNearest(
                eq(uploadId), any(Point.class), eq(1)))
                .thenReturn(List.of(net));
        when(heatChamberRepository.findWithinDistance(
                eq(uploadId), any(Point.class), eq(10.0)))
                .thenReturn(List.of(chamber));
        when(heatNetworkRepository.countConnectionsToChamber(
                eq(uploadId), any(Point.class), eq(1.0)))
                .thenReturn(2);

        List<TieInCandidate> candidates =
                service.findCandidates(uploadId, "oks-1");

        assertThat(candidates).hasSize(1);
        TieInCandidate c = candidates.get(0);
        assertThat(c.getType()).isEqualTo(TieInType.EXISTING_CHAMBER);
        assertThat(c.getExistingChamberId()).isEqualTo("chamber-1");
        assertThat(c.getCost()).isEqualTo(5_000_000L);
        assertThat(c.getCurrentAttachments()).isEqualTo(2);
        assertThat(c.getDistanceToChamberM())
                .isGreaterThan(0.0).isLessThanOrEqualTo(10.0);
        assertThat(c.getNewChamberDiameter()).isNull();

        // target = камера, tieIn = точка на сети → по x отличаются
        assertThat(c.getTargetLongitude())
                .isCloseTo(37.61508,
                        org.assertj.core.data.Offset.offset(1e-6));
        assertThat(c.getTieInLongitude())
                .isCloseTo(37.6150,
                        org.assertj.core.data.Offset.offset(1e-3));
        assertThat(c.getTargetLongitude())
                .isNotEqualTo(c.getTieInLongitude());
    }

    /**
     * Камер в радиусе 10 м нет - создается кандидат
     * {@link TieInType#NEW_CHAMBER}. Проверяется, что
     * {@code targetXxx} совпадает с {@code tieInXxx}: новая камера
     * создается именно в точке присоединения на сети
     */
    @Test
    @DisplayName("Камер нет -> NEW_CHAMBER, target = tie-in")
    void chamberOutside10mIsIgnored() {
        OksConnectionPointEntity point = pointAt(37.6150, 55.7501);
        HeatNetworkEntity net = networkAt(37.6100, 37.6200, 400);

        when(oksConnectionPointRepository
                .findByUploadIdAndFeatureId(uploadId, "oks-1"))
                .thenReturn(Optional.of(point));
        when(heatNetworkRepository.findNearest(
                eq(uploadId), any(Point.class), eq(1)))
                .thenReturn(List.of(net));
        when(heatChamberRepository.findWithinDistance(
                eq(uploadId), any(Point.class), eq(10.0)))
                .thenReturn(Collections.emptyList());

        List<TieInCandidate> candidates =
                service.findCandidates(uploadId, "oks-1");

        assertThat(candidates).hasSize(1);
        TieInCandidate c = candidates.get(0);
        assertThat(c.getType()).isEqualTo(TieInType.NEW_CHAMBER);
        assertThat(c.getExistingChamberId()).isNull();
        assertThat(c.getCost()).isEqualTo(5_000_000L);
        assertThat(c.getDistanceToChamberM()).isEqualTo(0.0);
        assertThat(c.getNewChamberDiameter()).isEqualTo(400);

        assertThat(c.getTargetLongitude())
                .isEqualTo(c.getTieInLongitude());
        assertThat(c.getTargetLatitude())
                .isEqualTo(c.getTieInLatitude());
    }

    @Test
    @DisplayName("1 примыкание — камера подходит")
    void connectionsCountOne() {
        assertChamberSuitability(1);
    }

    @Test
    @DisplayName("2 примыкания — камера подходит")
    void connectionsCountTwo() {
        assertChamberSuitability(2);
    }

    @Test
    @DisplayName("3 примыкания — камера подходит")
    void connectionsCountThree() {
        assertChamberSuitability(3);
    }

    @Test
    @DisplayName("4 примыкания — камера переполнена -> NEW_CHAMBER")
    void connectionsCountFour() {
        OksConnectionPointEntity point = pointAt(37.6150, 55.7501);
        HeatNetworkEntity net = networkAt(37.6100, 37.6200, 200);
        HeatChamberEntity chamber = chamberAt(37.6150, 55.7500);

        when(oksConnectionPointRepository
                .findByUploadIdAndFeatureId(uploadId, "oks-1"))
                .thenReturn(Optional.of(point));
        when(heatNetworkRepository.findNearest(
                eq(uploadId), any(Point.class), eq(1)))
                .thenReturn(List.of(net));
        when(heatChamberRepository.findWithinDistance(
                eq(uploadId), any(Point.class), eq(10.0)))
                .thenReturn(List.of(chamber));
        when(heatNetworkRepository.countConnectionsToChamber(
                eq(uploadId), any(Point.class), eq(1.0)))
                .thenReturn(4);

        List<TieInCandidate> candidates =
                service.findCandidates(uploadId, "oks-1");

        assertThat(candidates).hasSize(1);
        TieInCandidate c = candidates.get(0);
        assertThat(c.getType()).isEqualTo(TieInType.NEW_CHAMBER);
        assertThat(c.getExistingChamberId()).isNull();
        assertThat(c.getCost()).isEqualTo(3_000_000L);
        assertThat(c.getNewChamberDiameter()).isEqualTo(200);
    }

    @Test
    @DisplayName("Шкала стоимости новой камеры (таблица 3.2)")
    void newChamberCostByDiameterScale() {
        assertThat(chamberCostTable.costForDiameter(50))
                .isEqualTo(3_000_000L);
        assertThat(chamberCostTable.costForDiameter(200))
                .isEqualTo(3_000_000L);
        assertThat(chamberCostTable.costForDiameter(250))
                .isEqualTo(5_000_000L);
        assertThat(chamberCostTable.costForDiameter(500))
                .isEqualTo(5_000_000L);
        assertThat(chamberCostTable.costForDiameter(600))
                .isEqualTo(8_000_000L);
        assertThat(chamberCostTable.costForDiameter(1000))
                .isEqualTo(8_000_000L);
        assertThat(chamberCostTable.costForDiameter(1200))
                .isEqualTo(12_000_000L);
        assertThat(chamberCostTable.costForDiameter(1400))
                .isEqualTo(12_000_000L);

        assertThatThrownBy(
                () -> chamberCostTable.costForDiameter(40))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                () -> chamberCostTable.costForDiameter(1500))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Нет сетей — пустой список")
    void emptyUploadHandledGracefully() {
        OksConnectionPointEntity point = pointAt(37.6150, 55.7501);

        when(oksConnectionPointRepository
                .findByUploadIdAndFeatureId(uploadId, "oks-1"))
                .thenReturn(Optional.of(point));
        when(heatNetworkRepository.findNearest(
                eq(uploadId), any(Point.class), eq(1)))
                .thenReturn(Collections.emptyList());

        List<TieInCandidate> candidates =
                service.findCandidates(uploadId, "oks-1");

        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("Несколько камер — сортировка по расстоянию")
    void multipleChambersReturnedAndSorted() {
        OksConnectionPointEntity point = pointAt(37.6150, 55.7501);
        HeatNetworkEntity net = networkAt(37.6100, 37.6200, 500);

        HeatChamberEntity chFar = chamberAt(
                "ch-distant", 37.61511, 55.7500);
        HeatChamberEntity chNear = chamberAt(
                "ch-close", 37.61503, 55.7500);

        when(oksConnectionPointRepository
                .findByUploadIdAndFeatureId(uploadId, "oks-1"))
                .thenReturn(Optional.of(point));
        when(heatNetworkRepository.findNearest(
                eq(uploadId), any(Point.class), eq(1)))
                .thenReturn(List.of(net));
        when(heatChamberRepository.findWithinDistance(
                eq(uploadId), any(Point.class), eq(10.0)))
                .thenReturn(List.of(chFar, chNear));
        when(heatNetworkRepository.countConnectionsToChamber(
                eq(uploadId), any(Point.class), eq(1.0)))
                .thenReturn(2);

        List<TieInCandidate> candidates =
                service.findCandidates(uploadId, "oks-1");

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).getExistingChamberId())
                .isEqualTo("ch-close");
        assertThat(candidates.get(1).getExistingChamberId())
                .isEqualTo("ch-distant");

        assertThat(candidates.get(0).getTargetLongitude())
                .isCloseTo(37.61503,
                        org.assertj.core.data.Offset.offset(1e-6));
        assertThat(candidates.get(1).getTargetLongitude())
                .isCloseTo(37.61511,
                        org.assertj.core.data.Offset.offset(1e-6));
    }

    // Хелперы

    private void assertChamberSuitability(int attachments) {
        OksConnectionPointEntity point = pointAt(37.6150, 55.7501);
        HeatNetworkEntity net = networkAt(37.6100, 37.6200, 200);
        HeatChamberEntity chamber = chamberAt(37.6150, 55.7500);

        when(oksConnectionPointRepository
                .findByUploadIdAndFeatureId(uploadId, "oks-1"))
                .thenReturn(Optional.of(point));
        when(heatNetworkRepository.findNearest(
                eq(uploadId), any(Point.class), eq(1)))
                .thenReturn(List.of(net));
        when(heatChamberRepository.findWithinDistance(
                eq(uploadId), any(Point.class), eq(10.0)))
                .thenReturn(List.of(chamber));
        when(heatNetworkRepository.countConnectionsToChamber(
                eq(uploadId), any(Point.class), eq(1.0)))
                .thenReturn(attachments);

        List<TieInCandidate> candidates =
                service.findCandidates(uploadId, "oks-1");

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getType())
                .isEqualTo(TieInType.EXISTING_CHAMBER);
        assertThat(candidates.get(0).getCurrentAttachments())
                .isEqualTo(attachments);
    }

    private OksConnectionPointEntity pointAt(double lon, double lat) {
        Point p = gf.createPoint(new Coordinate(lon, lat));
        p.setSRID(4326);
        OksConnectionPointEntity point =
                OksConnectionPointEntity.builder()
                        .flowTph(50.0)
                        .build();
        point.setUploadId(uploadId);
        point.setFeatureId("oks-1");
        point.setGeometry(p);
        return point;
    }

    private HeatNetworkEntity networkAt(double lonFrom, double lonTo,
                                        int diameter) {
        LineString line = gf.createLineString(new Coordinate[]{
                new Coordinate(lonFrom, 55.7500),
                new Coordinate(lonTo, 55.7500)
        });
        line.setSRID(4326);
        HeatNetworkEntity net = HeatNetworkEntity.builder()
                .diameter(diameter)
                .build();
        net.setUploadId(uploadId);
        net.setFeatureId("net-1");
        net.setGeometry(line);
        return net;
    }

    private HeatChamberEntity chamberAt(double lon, double lat) {
        return chamberAt("chamber-1", lon, lat);
    }

    private HeatChamberEntity chamberAt(String featureId,
                                        double lon, double lat) {
        Point p = gf.createPoint(new Coordinate(lon, lat));
        p.setSRID(4326);
        HeatChamberEntity chamber = HeatChamberEntity.builder().build();
        chamber.setUploadId(uploadId);
        chamber.setFeatureId(featureId);
        chamber.setGeometry(p);
        return chamber;
    }
}
