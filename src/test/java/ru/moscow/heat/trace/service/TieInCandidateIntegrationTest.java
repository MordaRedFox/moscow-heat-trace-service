package ru.moscow.heat.trace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import ru.moscow.heat.AbstractIntegrationTest;
import ru.moscow.heat.geojson.entity.HeatChamberEntity;
import ru.moscow.heat.geojson.entity.HeatNetworkEntity;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.repository.HeatChamberRepository;
import ru.moscow.heat.geojson.repository.HeatNetworkRepository;
import ru.moscow.heat.geojson.repository.OksConnectionPointRepository;
import ru.moscow.heat.geojson.service.CoordinateTransformService;
import ru.moscow.heat.trace.dto.TieInCandidate;
import ru.moscow.heat.trace.dto.TieInType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты поиска и оценки кандидатов на присоединение к тепловым сетям
 * с использованием реальной базы данных PostgreSQL + PostGIS через Testcontainers.
 * <p>
 * Проверяются:
 * <ul>
 *   <li>Подсчет примыканий native-запросом к точке камеры (1 участок, 2 участка, переполнение, транзитная линия без концов);</li>
 *   <li>Поиск кандидатов на синтетическом датасете (существующая камера, переполненная камера, удаленная камера);</li>
 *   <li>Изоляция выборок и кандидатов по {@code upload_id};</li>
 *   <li>Пакетный поиск лучших кандидатов по всем точкам загрузки.</li>
 * </ul>
 */
@DisplayName("Интеграционное тестирование кандидатов на присоединение (TieInCandidateIntegrationTest)")
class TieInCandidateIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TieInCandidateService tieInService;

    @Autowired
    private HeatNetworkRepository heatNetworkRepo;

    @Autowired
    private HeatChamberRepository heatChamberRepo;

    @Autowired
    private OksConnectionPointRepository oksCpRepo;

    @Autowired
    private CoordinateTransformService transformService;

    private final GeometryFactory gf = new GeometryFactory();
    private final ObjectMapper mapper = new ObjectMapper();

    private UUID uploadId;
    private double baseX;
    private double baseY;

    @BeforeEach
    void setUp() {
        uploadId = UUID.randomUUID();
        double[] baseUtm = transformService.transformPoint(37.6175, 55.7522);
        baseX = baseUtm[0];
        baseY = baseUtm[1];
    }

    private HeatNetworkEntity saveNetwork(UUID uid, String featureId, int diameter, LineString utmLine) {
        utmLine.setSRID(CoordinateTransformService.SRID_UTM_37N);
        LineString wgsLine = (LineString) transformService.toWgs84(utmLine);

        HeatNetworkEntity net = HeatNetworkEntity.builder()
                .diameter(diameter)
                .build();
        net.setUploadId(uid);
        net.setFeatureId(featureId);
        net.setGeometry(wgsLine);
        net.setGeometryUtm(utmLine);
        net.setProperties(mapper.createObjectNode());
        return heatNetworkRepo.save(net);
    }

    private HeatChamberEntity saveChamber(UUID uid, String featureId, Point utmPoint) {
        utmPoint.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point wgsPoint = (Point) transformService.toWgs84(utmPoint);

        HeatChamberEntity chamber = HeatChamberEntity.builder().build();
        chamber.setUploadId(uid);
        chamber.setFeatureId(featureId);
        chamber.setGeometry(wgsPoint);
        chamber.setGeometryUtm(utmPoint);
        chamber.setProperties(mapper.createObjectNode());
        return heatChamberRepo.save(chamber);
    }

    private OksConnectionPointEntity saveConnectionPoint(UUID uid, String featureId, double flowTph, Point utmPoint) {
        utmPoint.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point wgsPoint = (Point) transformService.toWgs84(utmPoint);

        OksConnectionPointEntity point = OksConnectionPointEntity.builder()
                .flowTph(flowTph)
                .build();
        point.setUploadId(uid);
        point.setFeatureId(featureId);
        point.setGeometry(wgsPoint);
        point.setGeometryUtm(utmPoint);
        point.setProperties(mapper.createObjectNode());
        return oksCpRepo.save(point);
    }

    @Test
    @DisplayName("Подсчет примыканий native-запросом: тупик, транзит, 4 примыкания, транзитная линия без концов")
    void countConnectionsNativeQuery() {
        // Камера в базовой точке
        Point chamberUtm = gf.createPoint(new Coordinate(baseX, baseY));
        HeatChamberEntity chamber = saveChamber(uploadId, "ch-center", chamberUtm);
        Point chamberWgs = (Point) chamber.getGeometry();

        // До добавления сетей примыканий 0
        int initialCount = heatNetworkRepo.countConnectionsToChamber(uploadId, chamberWgs, 1.0);
        assertThat(initialCount).isEqualTo(0);

        // 1. Добавляем 1 участок, заканчивающийся ровно в камере: (baseX - 40, baseY) -> (baseX, baseY)
        LineString line1 = gf.createLineString(new Coordinate[]{
                new Coordinate(baseX - 40, baseY),
                new Coordinate(baseX, baseY)
        });
        saveNetwork(uploadId, "net-1", 300, line1);

        int count1 = heatNetworkRepo.countConnectionsToChamber(uploadId, chamberWgs, 1.0);
        assertThat(count1).isEqualTo(1);

        // 2. Добавляем 2-й участок, выходящий из камеры на восток: (baseX, baseY) -> (baseX + 40, baseY)
        LineString line2 = gf.createLineString(new Coordinate[]{
                new Coordinate(baseX, baseY),
                new Coordinate(baseX + 40, baseY)
        });
        saveNetwork(uploadId, "net-2", 300, line2);

        int count2 = heatNetworkRepo.countConnectionsToChamber(uploadId, chamberWgs, 1.0);
        assertThat(count2).isEqualTo(2);

        // 3. Добавляем 3-й и 4-й участки (север и юг)
        LineString line3 = gf.createLineString(new Coordinate[]{
                new Coordinate(baseX, baseY),
                new Coordinate(baseX, baseY + 40)
        });
        saveNetwork(uploadId, "net-3", 200, line3);

        LineString line4 = gf.createLineString(new Coordinate[]{
                new Coordinate(baseX, baseY - 40),
                new Coordinate(baseX, baseY)
        });
        saveNetwork(uploadId, "net-4", 200, line4);

        int count4 = heatNetworkRepo.countConnectionsToChamber(uploadId, chamberWgs, 1.0);
        assertThat(count4).isEqualTo(4);

        // 4. Добавляем параллельную линию, проходящую рядом (в 5 метрах), но её концы далеко (за 40 м)
        LineString passingLine = gf.createLineString(new Coordinate[]{
                new Coordinate(baseX - 40, baseY + 5),
                new Coordinate(baseX + 40, baseY + 5)
        });
        saveNetwork(uploadId, "net-passing", 200, passingLine);

        // Количество примыканий к камере не должно измениться — только концы участков считаются примыканиями
        int countStill4 = heatNetworkRepo.countConnectionsToChamber(uploadId, chamberWgs, 1.0);
        assertThat(countStill4).isEqualTo(4);
    }

    @Test
    @DisplayName("Синтетический датасет: выбор EXISTING_CHAMBER vs NEW_CHAMBER в зависимости от дистанции и примыканий")
    void candidatesOnSyntheticDataset() {
        // Создаем магистральный трубопровод ДУ 500 с запада на восток: (baseX - 100, baseY) -> (baseX + 100, baseY)
        // Разделен камерой ch-1 на отметке (baseX, baseY)
        Point ch1Utm = gf.createPoint(new Coordinate(baseX, baseY));
        saveChamber(uploadId, "ch-1", ch1Utm);

        LineString westNet = gf.createLineString(new Coordinate[]{
                new Coordinate(baseX - 100, baseY),
                new Coordinate(baseX, baseY)
        });
        saveNetwork(uploadId, "net-west", 500, westNet);

        LineString eastNet = gf.createLineString(new Coordinate[]{
                new Coordinate(baseX, baseY),
                new Coordinate(baseX + 100, baseY)
        });
        saveNetwork(uploadId, "net-east", 500, eastNet);

        // В камере ch-1 сходятся 2 конца (net-west и net-east). Свободно: 4 - 2 = 2 примыкания.

        // Точка подключения ОКС №1: в 4 метрах к северу от камеры ch-1 (baseX, baseY + 4)
        // Ближайшая точка на сети — (baseX, baseY), расстояние до камеры ch-1 ~ 0 м (< 10 м).
        Point oks1Utm = gf.createPoint(new Coordinate(baseX, baseY + 4));
        OksConnectionPointEntity oks1 = saveConnectionPoint(uploadId, "oks-1", 10.0, oks1Utm);

        List<TieInCandidate> candidates1 = tieInService.findCandidatesForPoint(uploadId, oks1);
        assertThat(candidates1).hasSize(1);
        TieInCandidate best1 = candidates1.get(0);
        assertThat(best1.getTieInType()).isEqualTo(TieInType.EXISTING_CHAMBER);
        assertThat(best1.getExistingChamberId()).isEqualTo("ch-1");
        assertThat(best1.getCost()).isEqualTo(5_000_000.0);
        assertThat(best1.getCurrentChamberConnections()).isEqualTo(2);

        // Точка подключения ОКС №2: на отметке X = baseX + 60, Y = baseY + 5.
        // Ближайшая точка на сети net-east — (baseX + 60, baseY).
        // Расстояние до ближайшей камеры ch-1 составляет 60 м (> 10 м).
        // Должна быть предложена NEW_CHAMBER с ДУ = 500 и стоимостью 5 000 000 руб (по шкале 250..500).
        Point oks2Utm = gf.createPoint(new Coordinate(baseX + 60, baseY + 5));
        OksConnectionPointEntity oks2 = saveConnectionPoint(uploadId, "oks-2", 20.0, oks2Utm);

        List<TieInCandidate> candidates2 = tieInService.findCandidatesForPoint(uploadId, oks2);
        assertThat(candidates2).hasSize(1);
        TieInCandidate best2 = candidates2.get(0);
        assertThat(best2.getTieInType()).isEqualTo(TieInType.NEW_CHAMBER);
        assertThat(best2.getExistingChamberId()).isNull();
        assertThat(best2.getCost()).isEqualTo(5_000_000.0);
        assertThat(best2.getRequiredChamberDiameter()).isEqualTo(500);
        assertThat(best2.getDistanceToChamberM()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Переполненная камера (4 примыкания) в радиусе 10 м отсекается, создается NEW_CHAMBER")
    void fullChamberWithin10mFallsBackToNewChamber() {
        Point chUtm = gf.createPoint(new Coordinate(baseX, baseY));
        saveChamber(uploadId, "ch-full", chUtm);

        // 4 участка сходятся в камере ch-full (ДУ 200)
        saveNetwork(uploadId, "net-w", 200, gf.createLineString(new Coordinate[]{new Coordinate(baseX - 30, baseY), new Coordinate(baseX, baseY)}));
        saveNetwork(uploadId, "net-e", 200, gf.createLineString(new Coordinate[]{new Coordinate(baseX, baseY), new Coordinate(baseX + 30, baseY)}));
        saveNetwork(uploadId, "net-n", 200, gf.createLineString(new Coordinate[]{new Coordinate(baseX, baseY), new Coordinate(baseX, baseY + 30)}));
        saveNetwork(uploadId, "net-s", 200, gf.createLineString(new Coordinate[]{new Coordinate(baseX, baseY - 30), new Coordinate(baseX, baseY)}));

        // Точка подключения рядом с камерой (расстояние до сети 3 м)
        Point oksUtm = gf.createPoint(new Coordinate(baseX + 2, baseY + 3));
        OksConnectionPointEntity oks = saveConnectionPoint(uploadId, "oks-near-full", 15.0, oksUtm);

        List<TieInCandidate> candidates = tieInService.findCandidatesForPoint(uploadId, oks);
        assertThat(candidates).hasSize(1);
        TieInCandidate best = candidates.get(0);

        // Камера переполнена (4 + 1 > 4), поэтому кандидат — NEW_CHAMBER
        assertThat(best.getTieInType()).isEqualTo(TieInType.NEW_CHAMBER);
        assertThat(best.getExistingChamberId()).isNull();
        // Стоимость новой камеры по шкале для ДУ 200 (50..200 мм) = 3 000 000 руб
        assertThat(best.getCost()).isEqualTo(3_000_000.0);
        assertThat(best.getRequiredChamberDiameter()).isEqualTo(200);
    }

    @Test
    @DisplayName("Изоляция по upload_id: объекты другой сессии не учитываются при поиске кандидатов")
    void isolationByUploadId() {
        UUID otherUploadId = UUID.randomUUID();

        // В другой сессии создаем сеть и камеру
        Point chOtherUtm = gf.createPoint(new Coordinate(baseX, baseY));
        saveChamber(otherUploadId, "ch-other", chOtherUtm);

        LineString lineOther = gf.createLineString(new Coordinate[]{
                new Coordinate(baseX - 50, baseY),
                new Coordinate(baseX + 50, baseY)
        });
        saveNetwork(otherUploadId, "net-other", 400, lineOther);

        // В текущей сессии создаем только точку ОКС с теми же координатами
        Point oksUtm = gf.createPoint(new Coordinate(baseX, baseY + 5));
        OksConnectionPointEntity oksCurrent = saveConnectionPoint(uploadId, "oks-current", 12.0, oksUtm);

        // Поиск кандидатов в uploadId не должен видеть объекты otherUploadId
        List<TieInCandidate> candidates = tieInService.findCandidatesForPoint(uploadId, oksCurrent);
        assertThat(candidates).isEmpty();
        assertThat(tieInService.findBestCandidateForPoint(uploadId, oksCurrent)).isEmpty();
    }

    @Test
    @DisplayName("Пакетный поиск лучших кандидатов по всем точкам подключения загрузки")
    void batchFindBestCandidates() {
        Point chUtm = gf.createPoint(new Coordinate(baseX, baseY));
        saveChamber(uploadId, "ch-center", chUtm);

        LineString line = gf.createLineString(new Coordinate[]{
                new Coordinate(baseX - 50, baseY),
                new Coordinate(baseX + 50, baseY)
        });
        saveNetwork(uploadId, "net-main", 300, line);

        Point pt1Utm = gf.createPoint(new Coordinate(baseX, baseY + 4));
        saveConnectionPoint(uploadId, "pt-1", 10.0, pt1Utm);

        Point pt2Utm = gf.createPoint(new Coordinate(baseX + 40, baseY + 4));
        saveConnectionPoint(uploadId, "pt-2", 15.0, pt2Utm);

        Map<String, TieInCandidate> bestByPoint = tieInService.findBestCandidatesForAllPoints(uploadId);
        assertThat(bestByPoint).hasSize(2);
        assertThat(bestByPoint).containsKey("pt-1");
        assertThat(bestByPoint).containsKey("pt-2");

        // pt-1 рядом с камерой (4 м) -> EXISTING_CHAMBER
        assertThat(bestByPoint.get("pt-1").getTieInType()).isEqualTo(TieInType.EXISTING_CHAMBER);
        assertThat(bestByPoint.get("pt-1").getExistingChamberId()).isEqualTo("ch-center");

        // pt-2 на расстоянии 40 м от камеры -> NEW_CHAMBER
        assertThat(bestByPoint.get("pt-2").getTieInType()).isEqualTo(TieInType.NEW_CHAMBER);
        assertThat(bestByPoint.get("pt-2").getRequiredChamberDiameter()).isEqualTo(300);
    }
}
