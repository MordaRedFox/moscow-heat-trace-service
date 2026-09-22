package ru.moscow.heat.trace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import ru.moscow.heat.spatial.DiameterTable;
import ru.moscow.heat.spatial.GeometryUtils;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TieInType;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Модульные тесты сервиса {@link TieInCandidateService}.
 * Проверяют нормативные правила выбора точек присоединения согласно разделу 3.2 ТП:
 * - геометрический поиск ближайшей точки на сегменте теплосети;
 * - учет камер в радиусе 10 м и отсечение камер за пределами радиуса;
 * - расчет примыканий (1 участок, 2 участка транзитом, 3 участка, переполнение при 4);
 * - переключение между EXISTING_CHAMBER и NEW_CHAMBER;
 * - шкала стоимости новой камеры по условным диаметрам;
 * - компаратор выбора лучшего кандидата.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Модульное тестирование сервиса кандидатов на присоединение (TieInCandidateService)")
class TieInCandidateServiceTest {

    @Mock
    private HeatNetworkRepository heatNetworkRepository;

    @Mock
    private HeatChamberRepository heatChamberRepository;

    @Mock
    private OksConnectionPointRepository oksConnectionPointRepository;

    private GeometryUtils geometryUtils;
    private DiameterTable diameterTable;
    private TieInCandidateService service;

    private final GeometryFactory gf = new GeometryFactory();
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID uploadId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        CoordinateTransformService transformService = new CoordinateTransformService();
        geometryUtils = new GeometryUtils(transformService);
        diameterTable = new DiameterTable();
        service = new TieInCandidateService(
                heatNetworkRepository,
                heatChamberRepository,
                oksConnectionPointRepository,
                diameterTable,
                geometryUtils
        );
    }

    @Test
    @DisplayName("Поиск ближайшей точки на сегменте сети для точки подключения")
    void nearestPointOnSegment() {
        // Линия теплосети с запада на восток: (37.6100, 55.7500) -> (37.6200, 55.7500)
        LineString networkLine = gf.createLineString(new Coordinate[]{
                new Coordinate(37.6100, 55.7500),
                new Coordinate(37.6200, 55.7500)
        });
        networkLine.setSRID(4326);

        // Точка подключения ОКС к северу от середины сегмента
        Point connectionPoint = gf.createPoint(new Coordinate(37.6150, 55.7550));
        connectionPoint.setSRID(4326);

        Point nearest = geometryUtils.nearestPointOnGeometry(networkLine, connectionPoint);

        assertThat(nearest).isNotNull();
        assertThat(nearest.getX()).isCloseTo(37.6150, org.assertj.core.data.Offset.offset(0.0002));
        assertThat(nearest.getY()).isCloseTo(55.7500, org.assertj.core.data.Offset.offset(0.0002));

        double dist = geometryUtils.distanceMeters(connectionPoint, nearest);
        assertThat(dist).isGreaterThan(500.0).isLessThan(600.0);
    }

    @Test
    @DisplayName("Существующая камера в радиусе 10 м формирует кандидата EXISTING_CHAMBER")
    void chamberWithin10mIsConsidered() {
        Point pointGeom = gf.createPoint(new Coordinate(37.6150, 55.7501));
        pointGeom.setSRID(4326);

        OksConnectionPointEntity point = OksConnectionPointEntity.builder().flowTph(50.0).build();
        point.setUploadId(uploadId);
        point.setFeatureId("oks-1");
        point.setGeometry(pointGeom);

        LineString netGeom = gf.createLineString(new Coordinate[]{
                new Coordinate(37.6100, 55.7500),
                new Coordinate(37.6200, 55.7500)
        });
        netGeom.setSRID(4326);

        HeatNetworkEntity net = HeatNetworkEntity.builder().diameter(300).build();
        net.setUploadId(uploadId);
        net.setFeatureId("net-1");
        net.setGeometry(netGeom);

        // Камера в 5 метрах к востоку от проекции точки (37.6150, 55.7500)
        Point chamberGeom = gf.createPoint(new Coordinate(37.61508, 55.7500));
        chamberGeom.setSRID(4326);

        HeatChamberEntity chamber = HeatChamberEntity.builder().build();
        chamber.setUploadId(uploadId);
        chamber.setFeatureId("chamber-1");
        chamber.setGeometry(chamberGeom);

        when(heatNetworkRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(1000.0)))
                .thenReturn(List.of(net));
        when(heatChamberRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(10.0)))
                .thenReturn(List.of(chamber));
        when(heatNetworkRepository.countConnectionsToChamber(eq(uploadId), any(Point.class), eq(1.0)))
                .thenReturn(2);

        List<TieInCandidate> candidates = service.findCandidatesForPoint(uploadId, point);

        assertThat(candidates).hasSize(1);
        TieInCandidate c = candidates.get(0);
        assertThat(c.getTieInType()).isEqualTo(TieInType.EXISTING_CHAMBER);
        assertThat(c.getExistingChamberId()).isEqualTo("chamber-1");
        assertThat(c.getCost()).isEqualTo(5_000_000.0);
        assertThat(c.getCurrentChamberConnections()).isEqualTo(2);
        assertThat(c.getDistanceToChamberM()).isGreaterThan(0.0).isLessThanOrEqualTo(10.0);
        assertThat(c.getRequiredChamberDiameter()).isNull();
    }

    @Test
    @DisplayName("Камера за пределами 10 м игнорируется репозиторием -> создается NEW_CHAMBER")
    void chamberOutside10mIsIgnored() {
        Point pointGeom = gf.createPoint(new Coordinate(37.6150, 55.7501));
        pointGeom.setSRID(4326);

        OksConnectionPointEntity point = OksConnectionPointEntity.builder().flowTph(50.0).build();
        point.setUploadId(uploadId);
        point.setFeatureId("oks-1");
        point.setGeometry(pointGeom);

        LineString netGeom = gf.createLineString(new Coordinate[]{
                new Coordinate(37.6100, 55.7500),
                new Coordinate(37.6200, 55.7500)
        });
        netGeom.setSRID(4326);

        HeatNetworkEntity net = HeatNetworkEntity.builder().diameter(400).build();
        net.setUploadId(uploadId);
        net.setFeatureId("net-1");
        net.setGeometry(netGeom);

        when(heatNetworkRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(1000.0)))
                .thenReturn(List.of(net));
        // Камер в радиусе 10 м нет
        when(heatChamberRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(10.0)))
                .thenReturn(Collections.emptyList());
        when(heatNetworkRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(1.0)))
                .thenReturn(List.of(net));

        List<TieInCandidate> candidates = service.findCandidatesForPoint(uploadId, point);

        assertThat(candidates).hasSize(1);
        TieInCandidate c = candidates.get(0);
        assertThat(c.getTieInType()).isEqualTo(TieInType.NEW_CHAMBER);
        assertThat(c.getExistingChamberId()).isNull();
        assertThat(c.getCost()).isEqualTo(5_000_000.0); // 400 мм -> шкала 250..500 = 5 000 000 руб
        assertThat(c.getDistanceToChamberM()).isEqualTo(0.0);
        assertThat(c.getRequiredChamberDiameter()).isEqualTo(400);
        assertThat(c.getCurrentChamberConnections()).isNull();
    }

    @Test
    @DisplayName("Примыкания: 1 линия заканчивается в камере -> 1 примыкание, камера подходит")
    void connectionsCountOneLineEnd() {
        Point pointGeom = gf.createPoint(new Coordinate(37.6150, 55.7501));
        OksConnectionPointEntity point = OksConnectionPointEntity.builder().build();
        point.setUploadId(uploadId);
        point.setFeatureId("oks-1");
        point.setGeometry(pointGeom);

        LineString netGeom = gf.createLineString(new Coordinate[]{
                new Coordinate(37.6100, 55.7500), new Coordinate(37.6200, 55.7500)
        });
        HeatNetworkEntity net = HeatNetworkEntity.builder().diameter(200).build();
        net.setUploadId(uploadId);
        net.setFeatureId("net-1");
        net.setGeometry(netGeom);

        Point chamberGeom = gf.createPoint(new Coordinate(37.6150, 55.7500));
        HeatChamberEntity chamber = HeatChamberEntity.builder().build();
        chamber.setUploadId(uploadId);
        chamber.setFeatureId("ch-1");
        chamber.setGeometry(chamberGeom);

        when(heatNetworkRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(1000.0)))
                .thenReturn(List.of(net));
        when(heatChamberRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(10.0)))
                .thenReturn(List.of(chamber));
        // 1 примыкание: 1 + 1 <= 4 -> подходит
        when(heatNetworkRepository.countConnectionsToChamber(eq(uploadId), any(Point.class), eq(1.0)))
                .thenReturn(1);

        List<TieInCandidate> candidates = service.findCandidatesForPoint(uploadId, point);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getTieInType()).isEqualTo(TieInType.EXISTING_CHAMBER);
        assertThat(candidates.get(0).getCurrentChamberConnections()).isEqualTo(1);
    }

    @Test
    @DisplayName("Примыкания: 2 участка через камеру транзитом -> 2 примыкания, камера подходит")
    void connectionsCountTwoSectionsTransit() {
        Point pointGeom = gf.createPoint(new Coordinate(37.6150, 55.7501));
        OksConnectionPointEntity point = OksConnectionPointEntity.builder().build();
        point.setUploadId(uploadId);
        point.setFeatureId("oks-1");
        point.setGeometry(pointGeom);

        LineString netGeom = gf.createLineString(new Coordinate[]{
                new Coordinate(37.6100, 55.7500), new Coordinate(37.6200, 55.7500)
        });
        HeatNetworkEntity net = HeatNetworkEntity.builder().diameter(200).build();
        net.setUploadId(uploadId);
        net.setFeatureId("net-1");
        net.setGeometry(netGeom);

        Point chamberGeom = gf.createPoint(new Coordinate(37.6150, 55.7500));
        HeatChamberEntity chamber = HeatChamberEntity.builder().build();
        chamber.setUploadId(uploadId);
        chamber.setFeatureId("ch-1");
        chamber.setGeometry(chamberGeom);

        when(heatNetworkRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(1000.0)))
                .thenReturn(List.of(net));
        when(heatChamberRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(10.0)))
                .thenReturn(List.of(chamber));
        // 2 примыкания: 2 + 1 <= 4 -> подходит
        when(heatNetworkRepository.countConnectionsToChamber(eq(uploadId), any(Point.class), eq(1.0)))
                .thenReturn(2);

        List<TieInCandidate> candidates = service.findCandidatesForPoint(uploadId, point);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getTieInType()).isEqualTo(TieInType.EXISTING_CHAMBER);
        assertThat(candidates.get(0).getCurrentChamberConnections()).isEqualTo(2);
    }

    @Test
    @DisplayName("Примыкания: 3 участка -> камера подходит (3 + 1 <= 4)")
    void connectionsCountThreeSectionsSuitable() {
        Point pointGeom = gf.createPoint(new Coordinate(37.6150, 55.7501));
        OksConnectionPointEntity point = OksConnectionPointEntity.builder().build();
        point.setUploadId(uploadId);
        point.setFeatureId("oks-1");
        point.setGeometry(pointGeom);

        LineString netGeom = gf.createLineString(new Coordinate[]{
                new Coordinate(37.6100, 55.7500), new Coordinate(37.6200, 55.7500)
        });
        HeatNetworkEntity net = HeatNetworkEntity.builder().diameter(200).build();
        net.setUploadId(uploadId);
        net.setFeatureId("net-1");
        net.setGeometry(netGeom);

        Point chamberGeom = gf.createPoint(new Coordinate(37.6150, 55.7500));
        HeatChamberEntity chamber = HeatChamberEntity.builder().build();
        chamber.setUploadId(uploadId);
        chamber.setFeatureId("ch-1");
        chamber.setGeometry(chamberGeom);

        when(heatNetworkRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(1000.0)))
                .thenReturn(List.of(net));
        when(heatChamberRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(10.0)))
                .thenReturn(List.of(chamber));
        // 3 примыкания: 3 + 1 <= 4 -> подходит
        when(heatNetworkRepository.countConnectionsToChamber(eq(uploadId), any(Point.class), eq(1.0)))
                .thenReturn(3);

        List<TieInCandidate> candidates = service.findCandidatesForPoint(uploadId, point);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getTieInType()).isEqualTo(TieInType.EXISTING_CHAMBER);
        assertThat(candidates.get(0).getCurrentChamberConnections()).isEqualTo(3);
    }

    @Test
    @DisplayName("Примыкания: 4 участка -> камера переполнена (4 + 1 = 5 > 4), выбор NEW_CHAMBER")
    void connectionsCountFourSectionsNotSuitable() {
        Point pointGeom = gf.createPoint(new Coordinate(37.6150, 55.7501));
        OksConnectionPointEntity point = OksConnectionPointEntity.builder().build();
        point.setUploadId(uploadId);
        point.setFeatureId("oks-1");
        point.setGeometry(pointGeom);

        LineString netGeom = gf.createLineString(new Coordinate[]{
                new Coordinate(37.6100, 55.7500), new Coordinate(37.6200, 55.7500)
        });
        HeatNetworkEntity net = HeatNetworkEntity.builder().diameter(200).build();
        net.setUploadId(uploadId);
        net.setFeatureId("net-1");
        net.setGeometry(netGeom);

        Point chamberGeom = gf.createPoint(new Coordinate(37.6150, 55.7500));
        HeatChamberEntity chamber = HeatChamberEntity.builder().build();
        chamber.setUploadId(uploadId);
        chamber.setFeatureId("ch-1");
        chamber.setGeometry(chamberGeom);

        when(heatNetworkRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(1000.0)))
                .thenReturn(List.of(net));
        when(heatChamberRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(10.0)))
                .thenReturn(List.of(chamber));
        // 4 примыкания -> камера заполнена до предела
        when(heatNetworkRepository.countConnectionsToChamber(eq(uploadId), any(Point.class), eq(1.0)))
                .thenReturn(4);
        when(heatNetworkRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(1.0)))
                .thenReturn(List.of(net));

        List<TieInCandidate> candidates = service.findCandidatesForPoint(uploadId, point);

        assertThat(candidates).hasSize(1);
        TieInCandidate c = candidates.get(0);
        assertThat(c.getTieInType()).isEqualTo(TieInType.NEW_CHAMBER);
        assertThat(c.getExistingChamberId()).isNull();
        assertThat(c.getCost()).isEqualTo(3_000_000.0); // ДУ 200 -> 3 млн
        assertThat(c.getRequiredChamberDiameter()).isEqualTo(200);
    }

    @Test
    @DisplayName("Шкала стоимости новой камеры по нормативным диапазонам ДУ (таблица 3.2 ТП)")
    void newChamberCostByDiameterScale() {
        // Диапазон 50..200 мм -> 3 000 000 руб
        assertThat(TieInCandidateService.calculateNewChamberCost(50)).isEqualTo(3_000_000.0);
        assertThat(TieInCandidateService.calculateNewChamberCost(100)).isEqualTo(3_000_000.0);
        assertThat(TieInCandidateService.calculateNewChamberCost(200)).isEqualTo(3_000_000.0);

        // Диапазон 250..500 мм -> 5 000 000 руб
        assertThat(TieInCandidateService.calculateNewChamberCost(250)).isEqualTo(5_000_000.0);
        assertThat(TieInCandidateService.calculateNewChamberCost(400)).isEqualTo(5_000_000.0);
        assertThat(TieInCandidateService.calculateNewChamberCost(500)).isEqualTo(5_000_000.0);

        // Диапазон 600..1000 мм -> 8 000 000 руб
        assertThat(TieInCandidateService.calculateNewChamberCost(600)).isEqualTo(8_000_000.0);
        assertThat(TieInCandidateService.calculateNewChamberCost(800)).isEqualTo(8_000_000.0);
        assertThat(TieInCandidateService.calculateNewChamberCost(1000)).isEqualTo(8_000_000.0);

        // Диапазон 1200..1400 мм -> 12 000 000 руб
        assertThat(TieInCandidateService.calculateNewChamberCost(1200)).isEqualTo(12_000_000.0);
        assertThat(TieInCandidateService.calculateNewChamberCost(1400)).isEqualTo(12_000_000.0);

        // Граничные и недопустимые значения
        assertThatThrownBy(() -> TieInCandidateService.calculateNewChamberCost(40))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TieInCandidateService.calculateNewChamberCost(1500))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Компаратор выбора лучшего кандидата: стоимость -> расстояние -> свободные примыкания")
    void bestCandidateComparatorRules() {
        Point pt = gf.createPoint(new Coordinate(37.6, 55.7));

        // c1: NEW_CHAMBER ДУ 200, стоимость 3 млн (меньше 5 млн), расстояние 0.0
        TieInCandidate c1 = TieInCandidate.builder()
                .connectionPointId("p1").heatNetworkId("n1")
                .tieInType(TieInType.NEW_CHAMBER)
                .tieInPoint(pt).distanceToChamberM(0.0)
                .cost(3_000_000.0).requiredChamberDiameter(200)
                .build();

        // c2: EXISTING_CHAMBER, стоимость 5 млн, расстояние 2.0 м, примыканий 2 (свободно 2)
        TieInCandidate c2 = TieInCandidate.builder()
                .connectionPointId("p1").heatNetworkId("n1")
                .tieInType(TieInType.EXISTING_CHAMBER).existingChamberId("ch-2")
                .tieInPoint(pt).distanceToChamberM(2.0)
                .currentChamberConnections(2).cost(5_000_000.0)
                .build();

        // c3: EXISTING_CHAMBER, стоимость 5 млн, расстояние 2.0 м, примыканий 1 (свободно 3 -> лучше c2)
        TieInCandidate c3 = TieInCandidate.builder()
                .connectionPointId("p1").heatNetworkId("n1")
                .tieInType(TieInType.EXISTING_CHAMBER).existingChamberId("ch-3")
                .tieInPoint(pt).distanceToChamberM(2.0)
                .currentChamberConnections(1).cost(5_000_000.0)
                .build();

        // c4: EXISTING_CHAMBER, стоимость 5 млн, расстояние 8.0 м, примыканий 1 (расстояние больше c3)
        TieInCandidate c4 = TieInCandidate.builder()
                .connectionPointId("p1").heatNetworkId("n1")
                .tieInType(TieInType.EXISTING_CHAMBER).existingChamberId("ch-4")
                .tieInPoint(pt).distanceToChamberM(8.0)
                .currentChamberConnections(1).cost(5_000_000.0)
                .build();

        List<TieInCandidate> list = new ArrayList<>(List.of(c4, c2, c1, c3));
        list.sort(TieInCandidateService.BEST_CANDIDATE_COMPARATOR);

        // Ожидаемый порядок:
        // 1. c1 (стоимость 3 млн < 5 млн)
        // 2. c3 (стоимость 5 млн, dist 2.0 м, conns 1 [3 свободных])
        // 3. c2 (стоимость 5 млн, dist 2.0 м, conns 2 [2 свободных])
        // 4. c4 (стоимость 5 млн, dist 8.0 м)
        assertThat(list).containsExactly(c1, c3, c2, c4);

        Optional<TieInCandidate> best = service.selectBestCandidate(list);
        assertThat(best).contains(c1);
    }

    @Test
    @DisplayName("Корректная обработка загрузки без тепловых сетей (возвращается пустой список)")
    void emptyUploadHandledGracefully() {
        Point pointGeom = gf.createPoint(new Coordinate(37.6150, 55.7501));
        OksConnectionPointEntity point = OksConnectionPointEntity.builder().build();
        point.setUploadId(uploadId);
        point.setFeatureId("oks-1");
        point.setGeometry(pointGeom);

        when(heatNetworkRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(1000.0)))
                .thenReturn(Collections.emptyList());
        when(heatNetworkRepository.findNearest(eq(uploadId), any(Point.class), eq(10)))
                .thenReturn(Collections.emptyList());
        when(heatNetworkRepository.findByUploadId(uploadId))
                .thenReturn(Collections.emptyList());

        List<TieInCandidate> candidates = service.findCandidatesForPoint(uploadId, point);

        assertThat(candidates).isEmpty();
        assertThat(service.selectBestCandidate(candidates)).isEmpty();
    }

    @Test
    @DisplayName("Несколько подходящих камер в радиусе 10 м: возвращаются все и сортируются по компаратору")
    void multipleChambersReturnedAndSorted() {
        Point pointGeom = gf.createPoint(new Coordinate(37.6150, 55.7501));
        OksConnectionPointEntity point = OksConnectionPointEntity.builder().build();
        point.setUploadId(uploadId);
        point.setFeatureId("oks-1");
        point.setGeometry(pointGeom);

        LineString netGeom = gf.createLineString(new Coordinate[]{
                new Coordinate(37.6100, 55.7500), new Coordinate(37.6200, 55.7500)
        });
        HeatNetworkEntity net = HeatNetworkEntity.builder().diameter(500).build();
        net.setUploadId(uploadId);
        net.setFeatureId("net-1");
        net.setGeometry(netGeom);

        // Камера 1: расстояние 7 м
        Point chGeom1 = gf.createPoint(new Coordinate(37.61511, 55.7500));
        HeatChamberEntity ch1 = HeatChamberEntity.builder().build();
        ch1.setUploadId(uploadId);
        ch1.setFeatureId("ch-distant");
        ch1.setGeometry(chGeom1);

        // Камера 2: расстояние 2 м
        Point chGeom2 = gf.createPoint(new Coordinate(37.61503, 55.7500));
        HeatChamberEntity ch2 = HeatChamberEntity.builder().build();
        ch2.setUploadId(uploadId);
        ch2.setFeatureId("ch-close");
        ch2.setGeometry(chGeom2);

        when(heatNetworkRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(1000.0)))
                .thenReturn(List.of(net));
        when(heatChamberRepository.findWithinDistance(eq(uploadId), any(Point.class), eq(10.0)))
                .thenReturn(List.of(ch1, ch2));
        when(heatNetworkRepository.countConnectionsToChamber(eq(uploadId), eq(chGeom1), eq(1.0)))
                .thenReturn(2);
        when(heatNetworkRepository.countConnectionsToChamber(eq(uploadId), eq(chGeom2), eq(1.0)))
                .thenReturn(2);

        List<TieInCandidate> candidates = service.findCandidatesForPoint(uploadId, point);

        assertThat(candidates).hasSize(2);
        // Обе камеры подходят, ch-close ближе -> первая в списке
        assertThat(candidates.get(0).getExistingChamberId()).isEqualTo("ch-close");
        assertThat(candidates.get(1).getExistingChamberId()).isEqualTo("ch-distant");
    }
}
