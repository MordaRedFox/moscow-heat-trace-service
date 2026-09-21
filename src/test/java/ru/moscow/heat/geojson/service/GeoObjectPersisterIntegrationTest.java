package ru.moscow.heat.geojson.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.moscow.heat.AbstractIntegrationTest;
import ru.moscow.heat.geojson.TestGeoJsonFactory;
import ru.moscow.heat.geojson.entity.HeatChamberEntity;
import ru.moscow.heat.geojson.entity.HeatNetworkEntity;
import ru.moscow.heat.geojson.entity.OksConnectionPointEntity;
import ru.moscow.heat.geojson.entity.RestrictionEntity;
import ru.moscow.heat.geojson.entity.SourceEntity;
import ru.moscow.heat.geojson.repository.GeoFeatureRepository;
import ru.moscow.heat.geojson.repository.HeatChamberRepository;
import ru.moscow.heat.geojson.repository.HeatNetworkRepository;
import ru.moscow.heat.geojson.repository.OksConnectionPointRepository;
import ru.moscow.heat.geojson.repository.RestrictionRepository;
import ru.moscow.heat.geojson.repository.SourceRepository;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты раскладки {@code GeoFeature} по
 * типизированным таблицам через {@code GeoObjectPersister}
 */
class GeoObjectPersisterIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private GeoJsonParserService parser;

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
    private GeoFeatureRepository geoFeatureRepo;

    private UUID uploadId;

    @BeforeEach
    void setUp() {
        uploadId = UUID.randomUUID();
    }

    /**
     * {@code source} попадает в таблицу {@code source};
     * остальные типизированные таблицы пусты
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("source сохраняется в таблицу source")
    void sourceGoesToSourceTable() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "src1", "source", "Point",
                37.6, 55.75));

        parse(c);

        List<SourceEntity> sources = sourceRepo.findByUploadId(uploadId);
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getFeatureId()).isEqualTo("src1");
        assertThat(sources.get(0).getUploadId()).isEqualTo(uploadId);
        assertThat(heatNetworkRepo.findByUploadId(uploadId)).isEmpty();
        assertThat(heatChamberRepo.findByUploadId(uploadId)).isEmpty();
    }

    /**
     * {@code heat_network}: извлекается атрибут diameter
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("heat_network сохраняет диаметр")
    void heatNetworkExtractsDiameter() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode f = TestGeoJsonFactory.feature(
                "n1", "heat_network", "LineString",
                37.6, 55.75, 37.61, 55.76);
        ((ObjectNode) f.get("properties")).put("diameter", 200);
        TestGeoJsonFactory.addFeature(c, f);

        parse(c);

        List<HeatNetworkEntity> nets =
                heatNetworkRepo.findByUploadId(uploadId);
        assertThat(nets).hasSize(1);
        HeatNetworkEntity n = nets.get(0);
        assertThat(n.getFeatureId()).isEqualTo("n1");
        assertThat(n.getDiameter()).isEqualTo(200);
    }

    /**
     * {@code heat_chamber}: сохраняется без атрибутов, только
     * идентификация и геометрия
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("heat_chamber сохраняется без атрибутов")
    void heatChamberHasNoAttributes() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "ch1", "heat_chamber", "Point",
                37.6, 55.75));

        parse(c);

        List<HeatChamberEntity> chambers =
                heatChamberRepo.findByUploadId(uploadId);
        assertThat(chambers).hasSize(1);
        assertThat(chambers.get(0).getFeatureId()).isEqualTo("ch1");
    }

    /**
     * {@code oks_connection_point}: извлекается атрибут flow_tph
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("oks_connection_point сохраняет flow_tph")
    void oksConnectionPointExtractsFlow() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode cp = TestGeoJsonFactory.feature(
                "cp1", "oks_connection_point", "Point",
                37.6, 55.75);
        ((ObjectNode) cp.get("properties")).put("flow_tph", 24.87);
        TestGeoJsonFactory.addFeature(c, cp);

        parse(c);

        List<OksConnectionPointEntity> list =
                oksCpRepo.findByUploadId(uploadId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getFlowTph()).isEqualTo(24.87);
    }

    /**
     * {@code restriction}: извлекается restriction_type
     * @throws Exception при ошибке парсинга
     */
    @Test
    void restrictionExtractsType() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode r = TestGeoJsonFactory.feature(
                "r1", "restriction", "Polygon",
                37.5, 55.7, 37.51, 55.7,
                37.51, 55.71, 37.5, 55.7);
        ((ObjectNode) r.get("properties"))
                .put("restriction_type", "gas_pipeline");
        TestGeoJsonFactory.addFeature(c, r);

        parse(c);

        List<RestrictionEntity> list =
                restrictionRepo.findByUploadId(uploadId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getRestrictionType())
                .isEqualTo("gas_pipeline");
    }

    /**
     * {@code restriction_type = oks} - типичный случай для полигонов
     * ОКС - сохраняется корректно
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("restriction_type = oks сохраняется")
    void restrictionOksTypeStored() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode r = TestGeoJsonFactory.feature(
                "oks_poly_1", "restriction", "Polygon",
                37.6, 55.75, 37.61, 55.75,
                37.61, 55.76, 37.6, 55.75);
        ((ObjectNode) r.get("properties"))
                .put("restriction_type", "oks");
        TestGeoJsonFactory.addFeature(c, r);

        parse(c);

        List<RestrictionEntity> list =
                restrictionRepo.findByUploadId(uploadId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getRestrictionType()).isEqualTo("oks");
    }

    /**
     * Типизированная сущность хранит обе проекции геометрии и properties.
     * Для проверки {@code geom} и {@code geomUtm} используется
     * приведение к {@code Object}: JTS объявляет {@code Geometry}
     * с сырым {@code Comparable}, из-за чего вывод типа в
     * {@code assertThat(T)} дает unchecked-предупреждение
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("Типизированная сущность хранит geom, geom_utm "
            + "и properties")
    void typedEntityHasBothGeometryProjections() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "src1", "source", "Point",
                37.6, 55.75));

        parse(c);

        SourceEntity s = sourceRepo.findByUploadId(uploadId).get(0);
        assertThat((Object) s.getGeometry()).isNotNull();
        assertThat((Object) s.getGeometryUtm()).isNotNull();
        assertThat(s.getGeometry().getSRID()).isEqualTo(4326);
        assertThat(s.getGeometryUtm().getSRID()).isEqualTo(32637);
        assertThat(s.getProperties()).isNotNull();
        assertThat(s.getProperties().get("id").asText())
                .isEqualTo("src1");
    }

    /**
     * Изоляция по {@code upload_id}: одинаковые {@code feature_id}
     * в разных загрузках не конфликтуют
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("Persister изолирует данные по upload_id")
    void persisterIsolatesByUploadId() throws Exception {
        UUID other = UUID.randomUUID();

        ObjectNode c1 = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c1, TestGeoJsonFactory.feature(
                "same", "source", "Point",
                37.6, 55.75));
        parse(c1, uploadId);

        ObjectNode c2 = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c2, TestGeoJsonFactory.feature(
                "same", "source", "Point",
                37.7, 55.8));
        parse(c2, other);

        assertThat(sourceRepo.findByUploadId(uploadId)).hasSize(1);
        assertThat(sourceRepo.findByUploadId(other)).hasSize(1);
    }

    /**
     * Смешанный файл: каждая фича попадает в свою типизированную
     * таблицу, счетчик в {@code geo_feature} совпадает
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("Смешанный файл раскладывается по всем таблицам")
    void mixedFileGoesToAllTables() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();

        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "s1", "source", "Point",
                37.6, 55.75));

        ObjectNode hn = TestGeoJsonFactory.feature(
                "n1", "heat_network", "LineString",
                37.6, 55.75, 37.61, 55.751);
        ((ObjectNode) hn.get("properties")).put("diameter", 500);
        TestGeoJsonFactory.addFeature(c, hn);

        ObjectNode ch = TestGeoJsonFactory.feature(
                "c1", "heat_chamber", "Point",
                37.605, 55.75);
        TestGeoJsonFactory.addFeature(c, ch);

        ObjectNode cp = TestGeoJsonFactory.feature(
                "cp1", "oks_connection_point", "Point",
                37.62, 55.76);
        ((ObjectNode) cp.get("properties")).put("flow_tph", 12.5);
        TestGeoJsonFactory.addFeature(c, cp);

        parse(c);

        assertThat(sourceRepo.findByUploadId(uploadId)).hasSize(1);
        assertThat(heatNetworkRepo.findByUploadId(uploadId)).hasSize(1);
        assertThat(heatChamberRepo.findByUploadId(uploadId)).hasSize(1);
        assertThat(oksCpRepo.findByUploadId(uploadId)).hasSize(1);
        assertThat(geoFeatureRepo.countByUploadId(uploadId))
                .isEqualTo(4);
    }

    /**
     * Прогон парсера на текущем {@link #uploadId}
     * @param collection готовая FeatureCollection
     * @throws Exception при ошибке парсинга
     */
    private void parse(ObjectNode collection) throws Exception {
        parse(collection, uploadId);
    }

    /**
     * Прогон парсера на заданном {@code uploadId}
     * @param collection готовая FeatureCollection
     * @param id         идентификатор загрузки
     * @throws Exception при ошибке парсинга
     */
    private void parse(ObjectNode collection, UUID id) throws Exception {
        parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(collection)),
                id);
    }
}
