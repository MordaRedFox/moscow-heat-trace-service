package ru.moscow.heat.spatial;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;
import org.springframework.beans.factory.annotation.Autowired;
import ru.moscow.heat.AbstractIntegrationTest;
import ru.moscow.heat.geojson.entity.*;
import ru.moscow.heat.geojson.repository.*;
import ru.moscow.heat.geojson.service.CoordinateTransformService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Интеграционное тестирование валидатора консистентности (UploadConsistencyValidator)")
class UploadConsistencyValidatorTest extends AbstractIntegrationTest {

    @Autowired
    private UploadConsistencyValidator validator;

    @Autowired
    private SourceRepository sourceRepo;

    @Autowired
    private HeatNetworkRepository heatNetworkRepo;

    @Autowired
    private HeatChamberRepository heatChamberRepo;

    @Autowired
    private OksConnectionPointRepository oksCpRepo;

    @Autowired
    private RestrictionRepository restrictionRepo;

    @Autowired
    private CoordinateTransformService transformService;

    private final GeometryFactory gf = new GeometryFactory();
    private final ObjectMapper mapper = new ObjectMapper();

    private double baseX;
    private double baseY;

    @BeforeEach
    void setUp() {
        double[] baseUtm = transformService.transformPoint(37.6175, 55.7522);
        baseX = baseUtm[0];
        baseY = baseUtm[1];
    }

    private SourceEntity saveSource(UUID uid, String fid) {
        Point ptUtm = gf.createPoint(new Coordinate(baseX, baseY));
        ptUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        SourceEntity s = SourceEntity.builder().build();
        s.setUploadId(uid);
        s.setFeatureId(fid);
        s.setGeometry(transformService.toWgs84(ptUtm));
        s.setGeometryUtm(ptUtm);
        s.setProperties(mapper.createObjectNode());
        return sourceRepo.save(s);
    }

    private HeatChamberEntity saveChamber(UUID uid, String fid) {
        Point ptUtm = gf.createPoint(new Coordinate(baseX + 10, baseY + 10));
        ptUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        HeatChamberEntity c = HeatChamberEntity.builder().build();
        c.setUploadId(uid);
        c.setFeatureId(fid);
        c.setGeometry(transformService.toWgs84(ptUtm));
        c.setGeometryUtm(ptUtm);
        c.setProperties(mapper.createObjectNode());
        return heatChamberRepo.save(c);
    }

    private HeatNetworkEntity saveHeatNetwork(UUID uid, String fid, Integer diameter) {
        Coordinate[] coords = new Coordinate[]{
                new Coordinate(baseX, baseY),
                new Coordinate(baseX + 100, baseY)
        };
        LineString lineUtm = gf.createLineString(coords);
        lineUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        HeatNetworkEntity hn = HeatNetworkEntity.builder()
                .diameter(diameter)
                .build();
        hn.setUploadId(uid);
        hn.setFeatureId(fid);
        hn.setGeometry(transformService.toWgs84(lineUtm));
        hn.setGeometryUtm(lineUtm);
        hn.setProperties(mapper.createObjectNode());
        return heatNetworkRepo.save(hn);
    }

    private RestrictionEntity saveRestriction(UUID uid, String fid, String type, double minX, double minY, double maxX, double maxY) {
        Coordinate[] coords = new Coordinate[]{
                new Coordinate(minX, minY),
                new Coordinate(maxX, minY),
                new Coordinate(maxX, maxY),
                new Coordinate(minX, maxY),
                new Coordinate(minX, minY)
        };
        LinearRing ring = gf.createLinearRing(coords);
        Polygon polyUtm = gf.createPolygon(ring);
        polyUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        RestrictionEntity r = RestrictionEntity.builder()
                .restrictionType(type)
                .build();
        r.setUploadId(uid);
        r.setFeatureId(fid);
        r.setGeometry(transformService.toWgs84(polyUtm));
        r.setGeometryUtm(polyUtm);
        r.setProperties(mapper.createObjectNode());
        return restrictionRepo.save(r);
    }

    private OksConnectionPointEntity savePoint(UUID uid, String fid, Double flow, double x, double y) {
        Point ptUtm = gf.createPoint(new Coordinate(x, y));
        ptUtm.setSRID(CoordinateTransformService.SRID_UTM_37N);
        OksConnectionPointEntity p = OksConnectionPointEntity.builder()
                .flowTph(flow)
                .build();
        p.setUploadId(uid);
        p.setFeatureId(fid);
        p.setGeometry(transformService.toWgs84(ptUtm));
        p.setGeometryUtm(ptUtm);
        p.setProperties(mapper.createObjectNode());
        return oksCpRepo.save(p);
    }

    @Test
    @DisplayName("Идеальный валидный датасет проходит валидацию без ошибок и предупреждений")
    void shouldValidateSuccessfullyOnCleanDataset() {
        UUID uid = UUID.randomUUID();
        saveSource(uid, "src_1");
        saveChamber(uid, "ch_1");
        saveHeatNetwork(uid, "net_1", 200);
        saveRestriction(uid, "oks_poly_1", "oks", baseX + 50, baseY + 50, baseX + 150, baseY + 150);
        savePoint(uid, "oks_pt_1", 10.5, baseX + 80, baseY + 80);

        ConsistencyReport report = validator.validate(uid);

        assertThat(report.isValid()).isTrue();
        assertThat(report.getErrors()).isEmpty();
        assertThat(report.getWarnings()).isEmpty();
    }

    @Test
    @DisplayName("Ошибка: отсутствие источника тепла (0 sources)")
    void shouldFailWhenNoSource() {
        UUID uid = UUID.randomUUID();
        saveChamber(uid, "ch_1");
        saveHeatNetwork(uid, "net_1", 200);
        savePoint(uid, "oks_pt_1", 10.5, baseX + 10, baseY + 10);

        ConsistencyReport report = validator.validate(uid);

        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors()).anyMatch(e -> e.contains("ровно один источник") && e.contains("обнаружено: 0"));
    }

    @Test
    @DisplayName("Ошибка: более одного источника тепла (2 sources)")
    void shouldFailWhenMultipleSources() {
        UUID uid = UUID.randomUUID();
        saveSource(uid, "src_1");
        saveSource(uid, "src_2");
        savePoint(uid, "oks_pt_1", 10.5, baseX + 10, baseY + 10);

        ConsistencyReport report = validator.validate(uid);

        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors()).anyMatch(e -> e.contains("ровно один источник") && e.contains("обнаружено: 2"));
    }

    @Test
    @DisplayName("Ошибка: отсутствие точек подключения ОКС (0 points)")
    void shouldFailWhenNoConnectionPoints() {
        UUID uid = UUID.randomUUID();
        saveSource(uid, "src_1");
        saveHeatNetwork(uid, "net_1", 200);

        ConsistencyReport report = validator.validate(uid);

        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors()).anyMatch(e -> e.contains("не содержит ни одной точки подключения"));
    }

    @Test
    @DisplayName("Ошибка: точка подключения с нулевым или отрицательным flow_tph")
    void shouldFailWhenPointHasInvalidFlow() {
        UUID uid = UUID.randomUUID();
        saveSource(uid, "src_1");
        savePoint(uid, "bad_flow_point", 0.0, baseX + 10, baseY + 10);

        ConsistencyReport report = validator.validate(uid);

        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors()).anyMatch(e -> e.contains("bad_flow_point") && e.contains("некорректный расход flow_tph"));
    }

    @Test
    @DisplayName("Ошибка: участок сети с некорректным диаметром (null или <= 0)")
    void shouldFailWhenNetworkHasInvalidDiameter() {
        UUID uid = UUID.randomUUID();
        saveSource(uid, "src_1");
        savePoint(uid, "oks_pt_1", 10.5, baseX + 10, baseY + 10);
        saveHeatNetwork(uid, "bad_net", -50);

        ConsistencyReport report = validator.validate(uid);

        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors()).anyMatch(e -> e.contains("bad_net") && e.contains("некорректный условный диаметр"));
    }

    @Test
    @DisplayName("Ошибка: неуникальные feature_id в пределах загрузки")
    void shouldFailWhenDuplicateFeatureIdsExist() {
        UUID uid = UUID.randomUUID();
        saveSource(uid, "duplicate_id");
        saveChamber(uid, "duplicate_id");
        savePoint(uid, "oks_pt_1", 10.5, baseX + 10, baseY + 10);

        ConsistencyReport report = validator.validate(uid);

        assertThat(report.isValid()).isFalse();
        assertThat(report.getErrors()).anyMatch(e -> e.contains("неуникальные feature_id") && e.contains("duplicate_id"));
    }

    @Test
    @DisplayName("Предупреждение: отсутствие тепловых камер (heat_chamber)")
    void shouldWarnWhenNoHeatChambers() {
        UUID uid = UUID.randomUUID();
        saveSource(uid, "src_1");
        saveHeatNetwork(uid, "net_1", 200);
        saveRestriction(uid, "oks_poly_1", "oks", baseX + 50, baseY + 50, baseX + 150, baseY + 150);
        savePoint(uid, "oks_pt_1", 10.5, baseX + 80, baseY + 80);

        ConsistencyReport report = validator.validate(uid);

        assertThat(report.isValid()).isTrue();
        assertThat(report.getErrors()).isEmpty();
        assertThat(report.getWarnings()).anyMatch(w -> w.contains("отсутствуют существующие тепловые камеры"));
    }

    @Test
    @DisplayName("Предупреждение: точка подключения ОКС не привязана к полигону ОКС")
    void shouldWarnWhenPointIsUnbound() {
        UUID uid = UUID.randomUUID();
        saveSource(uid, "src_1");
        saveChamber(uid, "ch_1");
        savePoint(uid, "unbound_pt", 10.5, baseX + 500, baseY + 500); // нет полигона вокруг

        ConsistencyReport report = validator.validate(uid);

        assertThat(report.isValid()).isTrue();
        assertThat(report.getErrors()).isEmpty();
        assertThat(report.getWarnings()).anyMatch(w -> w.contains("unbound_pt") && w.contains("не входят в полигоны ОКС"));
    }

    @Test
    @DisplayName("Предупреждение: ограничение с неизвестным типом (игнорируется трассировкой)")
    void shouldWarnWhenUnknownRestrictionType() {
        UUID uid = UUID.randomUUID();
        saveSource(uid, "src_1");
        saveChamber(uid, "ch_1");
        saveRestriction(uid, "oks_poly_1", "oks", baseX + 50, baseY + 50, baseX + 150, baseY + 150);
        savePoint(uid, "oks_pt_1", 10.5, baseX + 80, baseY + 80);
        saveRestriction(uid, "mystery_restr", "unsupported_custom_type", baseX + 200, baseY + 200, baseX + 300, baseY + 300);

        ConsistencyReport report = validator.validate(uid);

        assertThat(report.isValid()).isTrue();
        assertThat(report.getErrors()).isEmpty();
        assertThat(report.getWarnings()).anyMatch(w -> w.contains("mystery_restr") && w.contains("неподдерживаемый тип 'unsupported_custom_type'"));
    }
}
