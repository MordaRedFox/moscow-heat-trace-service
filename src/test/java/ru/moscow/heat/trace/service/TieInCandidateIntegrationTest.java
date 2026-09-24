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
 * Интеграционные тесты поиска и оценки кандидатов на присоединение
 * с реальным PostgreSQL + PostGIS через Testcontainers
 * <p>Проверяются:
 * <ul>
 *   <li>подсчет примыканий native-запросом (1/2/4 примыканий,
 *       транзитная линия);</li>
 *   <li>поиск кандидатов на синтетическом датасете
 *       (существующая камера, удаленная камера);</li>
 *   <li>изоляция выборок и кандидатов по {@code upload_id};</li>
 *   <li>пакетный поиск кандидатов по всем точкам загрузки.</li>
 * </ul>
 */
@DisplayName("Интеграционные тесты TieInCandidateIntegrationTest")
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
        double[] baseUtm = transformService.transformPoint(
                37.6175, 55.7522);
        baseX = baseUtm[0];
        baseY = baseUtm[1];
    }

    private HeatNetworkEntity saveNetwork(UUID uid, String featureId,
                                          int diameter,
                                          LineString utmLine) {
        utmLine.setSRID(CoordinateTransformService.SRID_UTM_37N);
        LineString wgsLine = (LineString) transformService
                .toWgs84(utmLine);

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

    private HeatChamberEntity saveChamber(UUID uid, String featureId,
                                          Point utmPoint) {
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

    private OksConnectionPointEntity saveConnectionPoint(
            UUID uid, String featureId, double flowTph,
            Point utmPoint) {
        utmPoint.setSRID(CoordinateTransformService.SRID_UTM_37N);
        Point wgsPoint = (Point) transformService.toWgs84(utmPoint);

        OksConnectionPointEntity point = OksConnectionPointEntity
                .builder()
                .flowTph(flowTph)
                .build();
        point.setUploadId(uid);
        point.setFeatureId(featureId);
        point.setGeometry(wgsPoint);
        point.setGeometryUtm(utmPoint);
        point.setProperties(mapper.createObjectNode());
        return oksCpRepo.save(point);
    }

    /**
     * Подсчет примыканий native-запросом: 0, 1, 2, 4 примыканий,
     * а также транзитная линия, проходящая рядом, но не
     * заканчивающаяся в камере
     */
    @Test
    @DisplayName("Подсчет примыканий через countConnectionsToChamber")
    void countConnectionsNativeQuery() {
        Point chamberUtm = gf.createPoint(
                new Coordinate(baseX, baseY));
        HeatChamberEntity chamber = saveChamber(
                uploadId, "ch-center", chamberUtm);
        Point chamberWgs = (Point) chamber.getGeometry();

        int initial = heatNetworkRepo.countConnectionsToChamber(
                uploadId, chamberWgs, 1.0);
        assertThat(initial).isEqualTo(0);

        saveNetwork(uploadId, "net-1", 300,
                gf.createLineString(new Coordinate[]{
                        new Coordinate(baseX - 40, baseY),
                        new Coordinate(baseX, baseY)}));
        assertThat(heatNetworkRepo.countConnectionsToChamber(
                uploadId, chamberWgs, 1.0)).isEqualTo(1);

        saveNetwork(uploadId, "net-2", 300,
                gf.createLineString(new Coordinate[]{
                        new Coordinate(baseX, baseY),
                        new Coordinate(baseX + 40, baseY)}));
        assertThat(heatNetworkRepo.countConnectionsToChamber(
                uploadId, chamberWgs, 1.0)).isEqualTo(2);

        saveNetwork(uploadId, "net-3", 200,
                gf.createLineString(new Coordinate[]{
                        new Coordinate(baseX, baseY),
                        new Coordinate(baseX, baseY + 40)}));
        saveNetwork(uploadId, "net-4", 200,
                gf.createLineString(new Coordinate[]{
                        new Coordinate(baseX, baseY - 40),
                        new Coordinate(baseX, baseY)}));
        assertThat(heatNetworkRepo.countConnectionsToChamber(
                uploadId, chamberWgs, 1.0)).isEqualTo(4);

        saveNetwork(uploadId, "net-passing", 200,
                gf.createLineString(new Coordinate[]{
                        new Coordinate(baseX - 40, baseY + 5),
                        new Coordinate(baseX + 40, baseY + 5)}));
        assertThat(heatNetworkRepo.countConnectionsToChamber(
                uploadId, chamberWgs, 1.0)).isEqualTo(4);
    }

    /**
     * Синтетический датасет: EXISTING_CHAMBER для точки рядом
     * с камерой, NEW_CHAMBER для точки в 60 м от камеры
     */
    @Test
    @DisplayName("EXISTING_CHAMBER и NEW_CHAMBER на датасете")
    void candidatesOnSyntheticDataset() {
        Point ch1Utm = gf.createPoint(new Coordinate(baseX, baseY));
        saveChamber(uploadId, "ch-1", ch1Utm);

        saveNetwork(uploadId, "net-west", 500,
                gf.createLineString(new Coordinate[]{
                        new Coordinate(baseX - 100, baseY),
                        new Coordinate(baseX, baseY)}));
        saveNetwork(uploadId, "net-east", 500,
                gf.createLineString(new Coordinate[]{
                        new Coordinate(baseX, baseY),
                        new Coordinate(baseX + 100, baseY)}));

        Point oks1Utm = gf.createPoint(
                new Coordinate(baseX, baseY + 4));
        saveConnectionPoint(uploadId, "oks-1", 10.0, oks1Utm);

        List<TieInCandidate> c1 = tieInService.findCandidates(
                uploadId, "oks-1");
        assertThat(c1).hasSize(1);
        TieInCandidate best1 = c1.get(0);
        assertThat(best1.getType())
                .isEqualTo(TieInType.EXISTING_CHAMBER);
        assertThat(best1.getExistingChamberId()).isEqualTo("ch-1");
        assertThat(best1.getCost()).isEqualTo(5_000_000L);
        assertThat(best1.getCurrentAttachments()).isEqualTo(2);

        Point oks2Utm = gf.createPoint(
                new Coordinate(baseX + 60, baseY + 5));
        saveConnectionPoint(uploadId, "oks-2", 20.0, oks2Utm);

        List<TieInCandidate> c2 = tieInService.findCandidates(
                uploadId, "oks-2");
        assertThat(c2).hasSize(1);
        TieInCandidate best2 = c2.get(0);
        assertThat(best2.getType())
                .isEqualTo(TieInType.NEW_CHAMBER);
        assertThat(best2.getExistingChamberId()).isNull();
        assertThat(best2.getCost()).isEqualTo(5_000_000L);
        assertThat(best2.getNewChamberDiameter()).isEqualTo(500);
        assertThat(best2.getDistanceToChamberM()).isEqualTo(0.0);
    }

    /**
     * Переполненная камера (4 примыкания) в радиусе 10 м отсекается,
     * создается кандидат {@link TieInType#NEW_CHAMBER}
     */
    @Test
    @DisplayName("Переполненная камера -> NEW_CHAMBER")
    void fullChamberWithin10mFallsBackToNewChamber() {
        Point chUtm = gf.createPoint(new Coordinate(baseX, baseY));
        saveChamber(uploadId, "ch-full", chUtm);

        saveNetwork(uploadId, "net-w", 200,
                gf.createLineString(new Coordinate[]{
                        new Coordinate(baseX - 30, baseY),
                        new Coordinate(baseX, baseY)}));
        saveNetwork(uploadId, "net-e", 200,
                gf.createLineString(new Coordinate[]{
                        new Coordinate(baseX, baseY),
                        new Coordinate(baseX + 30, baseY)}));
        saveNetwork(uploadId, "net-n", 200,
                gf.createLineString(new Coordinate[]{
                        new Coordinate(baseX, baseY),
                        new Coordinate(baseX, baseY + 30)}));
        saveNetwork(uploadId, "net-s", 200,
                gf.createLineString(new Coordinate[]{
                        new Coordinate(baseX, baseY - 30),
                        new Coordinate(baseX, baseY)}));

        Point oksUtm = gf.createPoint(
                new Coordinate(baseX + 2, baseY + 3));
        saveConnectionPoint(uploadId, "oks-near-full", 15.0, oksUtm);

        List<TieInCandidate> candidates = tieInService
                .findCandidates(uploadId, "oks-near-full");
        assertThat(candidates).hasSize(1);
        TieInCandidate best = candidates.get(0);
        assertThat(best.getType()).isEqualTo(TieInType.NEW_CHAMBER);
        assertThat(best.getExistingChamberId()).isNull();
        assertThat(best.getCost()).isEqualTo(3_000_000L);
        assertThat(best.getNewChamberDiameter()).isEqualTo(200);
    }

    /**
     * Изоляция по upload_id: объекты другой сессии не учитываются
     */
    @Test
    @DisplayName("Изоляция по upload_id")
    void isolationByUploadId() {
        UUID otherUploadId = UUID.randomUUID();

        saveChamber(otherUploadId, "ch-other",
                gf.createPoint(new Coordinate(baseX, baseY)));
        saveNetwork(otherUploadId, "net-other", 400,
                gf.createLineString(new Coordinate[]{
                        new Coordinate(baseX - 50, baseY),
                        new Coordinate(baseX + 50, baseY)}));

        saveConnectionPoint(uploadId, "oks-current", 12.0,
                gf.createPoint(new Coordinate(baseX, baseY + 5)));

        List<TieInCandidate> candidates = tieInService
                .findCandidates(uploadId, "oks-current");
        assertThat(candidates).isEmpty();
    }

    /**
     * Пакетный поиск кандидатов по всем точкам загрузки.
     * Возвращается карта feature_id -> список кандидатов
     */
    @Test
    @DisplayName("Пакетный поиск кандидатов")
    void batchFindCandidates() {
        saveChamber(uploadId, "ch-center",
                gf.createPoint(new Coordinate(baseX, baseY)));
        saveNetwork(uploadId, "net-main", 300,
                gf.createLineString(new Coordinate[]{
                        new Coordinate(baseX - 50, baseY),
                        new Coordinate(baseX + 50, baseY)}));

        saveConnectionPoint(uploadId, "pt-1", 10.0,
                gf.createPoint(new Coordinate(baseX, baseY + 4)));
        saveConnectionPoint(uploadId, "pt-2", 15.0,
                gf.createPoint(new Coordinate(baseX + 40, baseY + 4)));

        Map<String, List<TieInCandidate>> all =
                tieInService.findCandidatesForAllPoints(uploadId);

        assertThat(all).hasSize(2);
        assertThat(all).containsKeys("pt-1", "pt-2");

        List<TieInCandidate> pt1 = all.get("pt-1");
        assertThat(pt1).isNotEmpty();
        assertThat(pt1.get(0).getType())
                .isEqualTo(TieInType.EXISTING_CHAMBER);
        assertThat(pt1.get(0).getExistingChamberId())
                .isEqualTo("ch-center");

        List<TieInCandidate> pt2 = all.get("pt-2");
        assertThat(pt2).isNotEmpty();
        assertThat(pt2.get(0).getType())
                .isEqualTo(TieInType.NEW_CHAMBER);
        assertThat(pt2.get(0).getNewChamberDiameter())
                .isEqualTo(300);
    }
}
