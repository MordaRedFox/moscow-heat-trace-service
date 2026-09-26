package ru.moscow.heat.geojson.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.moscow.heat.AbstractIntegrationTest;
import ru.moscow.heat.geojson.ObjectType;
import ru.moscow.heat.geojson.TestGeoJsonFactory;
import ru.moscow.heat.geojson.dto.GeoJsonUploadResponse;
import ru.moscow.heat.geojson.exception.GeoJsonParseException;
import ru.moscow.heat.geojson.repository.GeoFeatureRepository;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Интеграционные тесты потокового парсера GeoJSON.
 * Поднимается реальный Spring-контекст и PostgreSQL через
 * {@link AbstractIntegrationTest}. Проверяются: позитивные сценарии
 * с полным набором типов объектов, валидация структуры файла,
 * обязательных атрибутов, геометрии, типов значений и обработка
 * дубликатов идентификаторов
 * <p>Отдельно проверяется поддержка числового {@code id} -
 * требование ТП раздел 1.1: «Идентификаторы входных объектов могут
 * быть строковыми или числовыми»
 */
class GeoJsonParserServiceTest extends AbstractIntegrationTest {

    @Autowired
    private GeoJsonParserService parser;

    @Autowired
    private GeoFeatureRepository featureRepo;

    private UUID uploadId;

    @BeforeEach
    void setUp() {
        uploadId = UUID.randomUUID();
    }

    /**
     * Файл со всеми поддерживаемыми типами: source, heat_network,
     * heat_chamber, oks_connection_point, restriction
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("Валидный файл со всеми типами сохраняется")
    void validFileWithAllTypes() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();

        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "src1", "source", "Point",
                37.6, 55.75));

        ObjectNode hn = TestGeoJsonFactory.feature(
                "net1", "heat_network", "LineString",
                37.6, 55.75, 37.61, 55.751);
        ((ObjectNode) hn.get("properties")).put("diameter", 500);
        TestGeoJsonFactory.addFeature(c, hn);

        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "ch1", "heat_chamber", "Point",
                37.605, 55.75));

        ObjectNode cp = TestGeoJsonFactory.feature(
                "cp1", "oks_connection_point", "Point",
                37.62, 55.76);
        ((ObjectNode) cp.get("properties")).put("flow_tph", 24.87);
        TestGeoJsonFactory.addFeature(c, cp);

        ObjectNode restr = TestGeoJsonFactory.feature(
                "r1", "restriction", "Polygon",
                37.5, 55.7, 37.51, 55.7,
                37.51, 55.71, 37.5, 55.7);
        ((ObjectNode) restr.get("properties"))
                .put("restriction_type", "oks");
        TestGeoJsonFactory.addFeature(c, restr);

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);

        assertThat(r.getTotalCount()).isEqualTo(5);
        assertThat(r.getTotalErrorsCount()).isZero();
        assertThat(r.getCountsByType())
                .containsEntry(ObjectType.SOURCE, 1)
                .containsEntry(ObjectType.HEAT_NETWORK, 1)
                .containsEntry(ObjectType.HEAT_CHAMBER, 1)
                .containsEntry(ObjectType.OKS_CONNECTION_POINT, 1)
                .containsEntry(ObjectType.RESTRICTION, 1);
        assertThat(featureRepo.countByUploadId(uploadId))
                .isEqualTo(5);

        List<Double> bbox = r.getBbox();
        assertThat(bbox).isNotNull();
        assertThat(bbox.get(0)).isLessThanOrEqualTo(37.5);
        assertThat(bbox.get(2)).isGreaterThanOrEqualTo(37.62);
    }

    /**
     * CRS WGS 84 в канонической форме CRS84 принимается
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("CRS WGS84 проходит")
    void crsWgs84Accepted() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addCrs(
                c, "urn:ogc:def:crs:OGC:1.3:CRS84");
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "s1", "source", "Point",
                37.6, 55.75));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);

        assertThat(r.getTotalErrorsCount()).isZero();
    }

    /**
     * Отсутствие блока {@code crs} допустимо
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("Отсутствие CRS допустимо")
    void crsMissingAccepted() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "s1", "source", "Point",
                37.6, 55.75));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);

        assertThat(r.getTotalErrorsCount()).isZero();
    }

    // Негативные сценарии: структура

    @Test
    void rejectNonJsonObject() {
        assertThatThrownBy(() -> parser.processStream(
                new ByteArrayInputStream("[1,2,3]".getBytes()),
                uploadId))
                .isInstanceOf(GeoJsonParseException.class);
    }

    @Test
    void rejectMissingType() {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.removeType(c);
        assertThatThrownBy(() -> parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("type");
    }

    @Test
    void rejectWrongTypeValue() {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        c.put("type", "Feature");
        assertThatThrownBy(() -> parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("FeatureCollection");
    }

    @Test
    void rejectMissingFeatures() {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.removeFeatures(c);
        assertThatThrownBy(() -> parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("features");
    }

    @Test
    void emptyFeaturesArrayAccepted() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalCount()).isZero();
        assertThat(r.getTotalErrorsCount()).isZero();
    }

    @Test
    void rejectCrsNonWgs84() {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addCrs(c, "EPSG:3857");
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "s1", "source", "Point",
                37.6, 55.75));
        assertThatThrownBy(() -> parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("CRS");
    }

    // Негативные сценарии: фичи

    @Test
    void rejectFeatureWithoutProperties() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode f = c.putArray("features").addObject();
        f.put("type", "Feature");
        f.putObject("geometry").put("type", "Point")
                .putArray("coordinates").add(1).add(2);

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
    }

    @Test
    void rejectFeatureWithoutId() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                null, "source", "Point",
                37.6, 55.75));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
        assertThat(r.getErrors().get(0).getMessage())
                .contains("id");
    }

    @Test
    @DisplayName("id=null отклоняется")
    void rejectIdNull() throws Exception {
        String json = "{\"type\":\"FeatureCollection\","
                + "\"features\":["
                + "{\"type\":\"Feature\","
                + "\"properties\":{\"id\":null,"
                + "\"object_type\":\"source\"},"
                + "\"geometry\":{\"type\":\"Point\","
                + "\"coordinates\":[37.6,55.75]}}]}";

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(json.getBytes()),
                uploadId);
        assertThat(r.getTotalErrorsCount())
                .as("id=null должен дать ошибку валидации")
                .isEqualTo(1);
    }

    /**
     * Числовой id (например, {@code "id": 1}) принимается без ошибок -
     * ТП раздел 1.1 разрешает как строковые, так и числовые
     * идентификаторы. Такие id встречаются в конкурсном наборе
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("Числовой id принимается")
    void acceptNumericId() throws Exception {
        String json = "{\"type\":\"FeatureCollection\","
                + "\"features\":["
                + "{\"type\":\"Feature\","
                + "\"properties\":{\"id\":1,"
                + "\"object_type\":\"source\"},"
                + "\"geometry\":{\"type\":\"Point\","
                + "\"coordinates\":[37.6,55.75]}}]}";

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(json.getBytes()),
                uploadId);

        assertThat(r.getTotalErrorsCount()).isZero();
        assertThat(r.getTotalCount()).isEqualTo(1);
        assertThat(r.getCountsByType())
                .containsEntry(ObjectType.SOURCE, 1);
    }

    /**
     * Булев id отклоняется - допустимы только строка или число
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("Булев id отклоняется")
    void rejectBooleanId() throws Exception {
        String json = "{\"type\":\"FeatureCollection\","
                + "\"features\":["
                + "{\"type\":\"Feature\","
                + "\"properties\":{\"id\":true,"
                + "\"object_type\":\"source\"},"
                + "\"geometry\":{\"type\":\"Point\","
                + "\"coordinates\":[37.6,55.75]}}]}";

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(json.getBytes()),
                uploadId);

        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
        assertThat(r.getErrors().get(0).getMessage())
                .contains("id");
    }

    @Test
    void rejectUnknownObjectType() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "x1", "unknown_type", "Point",
                37.6, 55.75));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
    }

    /**
     * Удаленные типы (oks_future, oks_existing) из актуальной
     * модели отклоняются как неизвестные
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("oks_future и oks_existing отклоняются")
    void removedTypesAreRejected() throws Exception {
        ObjectNode c1 = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c1, TestGeoJsonFactory.feature(
                "o1", "oks_future", "Polygon",
                37.6, 55.75, 37.61, 55.75,
                37.61, 55.76, 37.6, 55.75));
        GeoJsonUploadResponse r1 = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c1)),
                uploadId);
        assertThat(r1.getTotalErrorsCount()).isEqualTo(1);

        ObjectNode c2 = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c2, TestGeoJsonFactory.feature(
                "e1", "oks_existing", "Polygon",
                37.6, 55.75, 37.61, 55.75,
                37.61, 55.76, 37.6, 55.75));
        GeoJsonUploadResponse r2 = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c2)),
                uploadId);
        assertThat(r2.getTotalErrorsCount()).isEqualTo(1);
    }

    @Test
    void rejectMissingObjectType() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "x1", null, "Point",
                37.6, 55.75));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
    }

    @Test
    void rejectGeometryTypeMismatch() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "s1", "source", "LineString",
                37.6, 55.75, 37.61, 55.76));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
    }

    @Test
    void rejectLineStringWithOnePoint() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode f = TestGeoJsonFactory.feature(
                "n1", "heat_network", "LineString",
                37.6, 55.75);
        ((ObjectNode) f.get("properties")).put("diameter", 200);
        TestGeoJsonFactory.addFeature(c, f);

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
    }

    @Test
    void rejectPointOutOfRange() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "s1", "source", "Point",
                200.0, 55.75));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
    }

    @Test
    void rejectUnclosedPolygonRing() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode f = TestGeoJsonFactory.feature(
                "r1", "restriction", "Polygon",
                37.6, 55.75, 37.61, 55.75,
                37.61, 55.76, 37.62, 55.76);
        ((ObjectNode) f.get("properties"))
                .put("restriction_type", "oks");
        TestGeoJsonFactory.addFeature(c, f);

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
        assertThat(r.getErrors().get(0).getMessage())
                .contains("замкнут");
    }

    /**
     * heat_network без diameter отклоняется
     * @throws Exception при ошибке парсинга
     */
    @Test
    void rejectHeatNetworkWithoutDiameter() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "n1", "heat_network", "LineString",
                37.6, 55.75, 37.61, 55.76));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
        assertThat(r.getErrors().get(0).getMessage())
                .contains("diameter");
    }

    /**
     * heat_network без flow_tph и upstream_object_id ПРОХОДИТ -
     * эти атрибуты удалены из обязательных в новой модели
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("heat_network без flow_tph проходит")
    void heatNetworkWithoutFlowPasses() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode f = TestGeoJsonFactory.feature(
                "n1", "heat_network", "LineString",
                37.6, 55.75, 37.61, 55.76);
        ((ObjectNode) f.get("properties")).put("diameter", 200);
        TestGeoJsonFactory.addFeature(c, f);

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isZero();
    }

    /**
     * heat_chamber без каких-либо атрибутов проходит
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("heat_chamber без атрибутов проходит")
    void heatChamberWithoutAttributesPasses() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "ch1", "heat_chamber", "Point",
                37.6, 55.75));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isZero();
    }

    /**
     * oks_connection_point без flow_tph отклоняется
     * @throws Exception при ошибке парсинга
     */
    @Test
    void rejectOksConnectionPointWithoutFlow() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "cp1", "oks_connection_point", "Point",
                37.6, 55.75));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
        assertThat(r.getErrors().get(0).getMessage())
                .contains("flow_tph");
    }

    /**
     * oks_connection_point с oks_id в properties - не ошибка,
     * лишние атрибуты игнорируются
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("Лишние атрибуты не ломают валидацию")
    void extraAttributesIgnored() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode cp = TestGeoJsonFactory.feature(
                "cp1", "oks_connection_point", "Point",
                37.6, 55.75);
        ObjectNode props = (ObjectNode) cp.get("properties");
        props.put("flow_tph", 10.0);
        props.put("oks_id", "some_polygon");
        props.put("heat_load", 99.9);
        TestGeoJsonFactory.addFeature(c, cp);

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isZero();
    }

    /**
     * restriction без restriction_type отклоняется
     * @throws Exception при ошибке парсинга
     */
    @Test
    void rejectRestrictionWithoutRestrictionType() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "r1", "restriction", "Polygon",
                37.6, 55.75, 37.61, 55.75,
                37.61, 55.76, 37.6, 55.75));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
    }

    // Типы атрибутов

    @Test
    void rejectDiameterAsString() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode f = TestGeoJsonFactory.feature(
                "n1", "heat_network", "LineString",
                37.6, 55.75, 37.61, 55.76);
        ((ObjectNode) f.get("properties")).put("diameter", "200");
        TestGeoJsonFactory.addFeature(c, f);

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
    }

    // Дубликаты

    @Test
    void duplicateFeatureId_reportsErrorForSecondOccurrence()
            throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "same", "source", "Point",
                37.6, 55.75));
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "same", "source", "Point",
                37.7, 55.8));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);

        assertThat(r.getTotalCount()).isEqualTo(1);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
        assertThat(featureRepo.countByUploadId(uploadId))
                .isEqualTo(1);
    }

    /**
     * restriction с линейной геометрией LineString принимается
     * @throws Exception при ошибке парсинга
     */
    @Test
    void restrictionWithLineStringAccepted() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode r = TestGeoJsonFactory.feature(
                "road1", "restriction", "LineString",
                37.6, 55.75, 37.65, 55.78);
        ((ObjectNode) r.get("properties")).put("restriction_type", "road");
        TestGeoJsonFactory.addFeature(c, r);

        GeoJsonUploadResponse resp = parser.processStream(
                new ByteArrayInputStream(TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(resp.getTotalErrorsCount()).isZero();
    }
}
