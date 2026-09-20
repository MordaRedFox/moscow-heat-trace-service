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
 */
class GeoJsonParserServiceTest extends AbstractIntegrationTest {

    @Autowired
    private GeoJsonParserService parser;

    @Autowired
    private GeoFeatureRepository featureRepo;

    private UUID uploadId;

    /**
     * Генерирует новый идентификатор загрузки для каждого теста,
     * чтобы изолировать данные между прогонами
     */
    @BeforeEach
    void setUp() {
        uploadId = UUID.randomUUID();
    }

    /**
     * Файл со всеми поддерживаемыми типами объектов: source,
     * heat_network, heat_chamber, oks_future, oks_connection_point,
     * restriction. Все фичи валидны, счетчики совпадают, bbox
     * охватывает все координаты
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("Валидный файл со всеми типами сохраняется полностью")
    void validFileWithAllTypes() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();

        ObjectNode src = TestGeoJsonFactory.feature(
                "src1", "source", "Point", 37.6, 55.75);
        TestGeoJsonFactory.addFeature(c, src);

        ObjectNode hn = TestGeoJsonFactory.feature(
                "net1", "heat_network", "LineString",
                37.6, 55.75, 37.61, 55.751);
        ((ObjectNode) hn.get("properties")).put("diameter", 200);
        ((ObjectNode) hn.get("properties")).put("flow_tph", 100.0);
        ((ObjectNode) hn.get("properties"))
                .put("upstream_object_id", "src1");
        TestGeoJsonFactory.addFeature(c, hn);

        ObjectNode ch = TestGeoJsonFactory.feature(
                "ch1", "heat_chamber", "Point", 37.605, 55.75);
        ((ObjectNode) ch.get("properties")).put("diameter", 200);
        TestGeoJsonFactory.addFeature(c, ch);

        ObjectNode oks = TestGeoJsonFactory.feature(
                "oks1", "oks_future", "Polygon",
                37.62, 55.76, 37.63, 55.76,
                37.63, 55.77, 37.62, 55.76);
        ((ObjectNode) oks.get("properties")).put("flow_tph", 50.0);
        ((ObjectNode) oks.get("properties")).put("heat_load", 2.5);
        TestGeoJsonFactory.addFeature(c, oks);

        ObjectNode cp = TestGeoJsonFactory.feature(
                "cp1", "oks_connection_point", "Point", 37.62, 55.76);
        ((ObjectNode) cp.get("properties")).put("oks_id", "oks1");
        TestGeoJsonFactory.addFeature(c, cp);

        ObjectNode restr = TestGeoJsonFactory.feature(
                "r1", "restriction", "Polygon",
                37.5, 55.7, 37.51, 55.7,
                37.51, 55.71, 37.5, 55.7);
        ((ObjectNode) restr.get("properties"))
                .put("restriction_type", "road");
        TestGeoJsonFactory.addFeature(c, restr);

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);

        assertThat(r.getTotalCount()).isEqualTo(6);
        assertThat(r.getTotalErrorsCount()).isZero();
        assertThat(r.getCountsByType())
                .containsEntry(ObjectType.SOURCE, 1)
                .containsEntry(ObjectType.HEAT_NETWORK, 1)
                .containsEntry(ObjectType.HEAT_CHAMBER, 1)
                .containsEntry(ObjectType.OKS_FUTURE, 1)
                .containsEntry(ObjectType.OKS_CONNECTION_POINT, 1)
                .containsEntry(ObjectType.RESTRICTION, 1);
        assertThat(featureRepo.countByUploadId(uploadId))
                .isEqualTo(6);

        List<Double> bbox = r.getBbox();
        assertThat(bbox).isNotNull();
        assertThat(bbox.get(0)).isLessThanOrEqualTo(37.5);
        assertThat(bbox.get(2)).isGreaterThanOrEqualTo(37.63);
    }

    /**
     * CRS WGS 84 в канонической форме CRS84 принимается без ошибок
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("CRS WGS84 проходит")
    void crsWgs84Accepted() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addCrs(
                c, "urn:ogc:def:crs:OGC:1.3:CRS84");
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "s1", "source", "Point", 37.6, 55.75));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);

        assertThat(r.getTotalErrorsCount()).isZero();
    }

    /**
     * Отсутствие блока crs в файле допустимо: подразумевается WGS 84
     * по умолчанию
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("Отсутствие CRS допустимо")
    void crsMissingAccepted() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "s1", "source", "Point", 37.6, 55.75));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);

        assertThat(r.getTotalErrorsCount()).isZero();
    }

    /**
     * Корневой элемент файла — не JSON-объект (массив)
     * Ожидается {@link GeoJsonParseException}
     */
    @Test
    void rejectNonJsonObject() {
        assertThatThrownBy(() -> parser.processStream(
                new ByteArrayInputStream("[1,2,3]".getBytes()),
                uploadId))
                .isInstanceOf(GeoJsonParseException.class);
    }

    /**
     * Отсутствует корневое поле {@code type}. Ожидается ошибка
     * с упоминанием поля
     */
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

    /**
     * Корневое поле {@code type} имеет недопустимое значение
     * (не {@code FeatureCollection}). Ожидается ошибка
     */
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

    /**
     * Отсутствует корневое поле {@code features}. Ожидается ошибка
     * с упоминанием поля
     */
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

    /**
     * Пустой массив {@code features} допустим: 0 объектов, 0 ошибок
     * @throws Exception при ошибке парсинга
     */
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

    /**
     * CRS, отличная от WGS 84 (например, EPSG:3857), отклоняется
     * с ошибкой парсинга
     */
    @Test
    void rejectCrsNonWgs84() {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addCrs(c, "EPSG:3857");
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "s1", "source", "Point", 37.6, 55.75));
        assertThatThrownBy(() -> parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("CRS");
    }

    /**
     * Регрессия: crs, расположенная после массива {@code features},
     * всё равно должна валидироваться. Ручная сборка JSON нужна,
     * чтобы гарантировать порядок полей
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("БАГ: crs после features не проверяется")
    void crsAfterFeatures_isNotValidated() throws Exception {
        String json = "{\"type\":\"FeatureCollection\","
                + "\"features\":["
                + "{\"type\":\"Feature\","
                + "\"properties\":{\"id\":\"s1\","
                + "\"object_type\":\"source\"},"
                + "\"geometry\":{\"type\":\"Point\","
                + "\"coordinates\":[37.6,55.75]}}"
                + "],"
                + "\"crs\":{\"type\":\"name\","
                + "\"properties\":{\"name\":\"EPSG:3857\"}}}";

        assertThatThrownBy(() -> parser.processStream(
                new ByteArrayInputStream(json.getBytes()),
                uploadId))
                .isInstanceOf(GeoJsonParseException.class);
    }

    /**
     * У фичи отсутствует блок {@code properties}.
     * Ожидается 1 ошибка валидации, объект не сохраняется
     * @throws Exception при ошибке парсинга
     */
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

    /**
     * У фичи отсутствует обязательный атрибут {@code id}.
     * Ожидается 1 ошибка с упоминанием поля
     * @throws Exception при ошибке парсинга
     */
    @Test
    void rejectFeatureWithoutId() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                null, "source", "Point", 37.6, 55.75));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
        assertThat(r.getErrors().get(0).getMessage())
                .contains("id");
    }

    /**
     * Регрессия: {@code id: null} должен приводить к ошибке
     * валидации, а не к сохранению фичи со строкой {@code "null"}
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("БАГ: id=null должен отклоняться")
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
     * Неизвестное значение {@code object_type} отклоняется
     * @throws Exception при ошибке парсинга
     */
    @Test
    void rejectUnknownObjectType() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "x1", "unknown_type", "Point", 37.6, 55.75));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
    }

    /**
     * Отсутствие {@code object_type} отклоняется
     * @throws Exception при ошибке парсинга
     */
    @Test
    void rejectMissingObjectType() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "x1", null, "Point", 37.6, 55.75));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
    }

    /**
     * Тип геометрии не соответствует типу объекта: source ожидает
     * Point, передается LineString
     * @throws Exception при ошибке парсинга
     */
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

    /**
     * LineString с одной точкой невалиден: минимум две точки
     * @throws Exception при ошибке парсинга
     */
    @Test
    void rejectLineStringWithOnePoint() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode f = TestGeoJsonFactory.feature(
                "n1", "heat_network", "LineString",
                37.6, 55.75);
        ((ObjectNode) f.get("properties")).put("diameter", 200);
        ((ObjectNode) f.get("properties")).put("flow_tph", 10.0);
        ((ObjectNode) f.get("properties"))
                .put("upstream_object_id", "s1");
        TestGeoJsonFactory.addFeature(c, f);

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
    }

    /**
     * Координаты точки вне допустимых диапазонов широты и долготы
     * отклоняются
     * @throws Exception при ошибке парсинга
     */
    @Test
    void rejectPointOutOfRange() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "s1", "source", "Point", 200.0, 55.75));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
    }

    /**
     * Кольцо полигона должно быть замкнуто: первая и последняя
     * точки совпадают. Иначе - ошибка
     * @throws Exception при ошибке парсинга
     */
    @Test
    void rejectUnclosedPolygonRing() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode f = TestGeoJsonFactory.feature(
                "o1", "oks_future", "Polygon",
                37.6, 55.75, 37.61, 55.75,
                37.61, 55.76, 37.62, 55.76);
        ((ObjectNode) f.get("properties")).put("flow_tph", 1.0);
        ((ObjectNode) f.get("properties")).put("heat_load", 1.0);
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
     * heat_network без обязательного {@code diameter} отклоняется
     * @throws Exception при ошибке парсинга
     */
    @Test
    void rejectHeatNetworkWithoutDiameter() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode f = TestGeoJsonFactory.feature(
                "n1", "heat_network", "LineString",
                37.6, 55.75, 37.61, 55.76);
        ((ObjectNode) f.get("properties")).put("flow_tph", 10.0);
        ((ObjectNode) f.get("properties"))
                .put("upstream_object_id", "s1");
        TestGeoJsonFactory.addFeature(c, f);

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
        assertThat(r.getErrors().get(0).getMessage())
                .contains("diameter");
    }

    /**
     * heat_network без обязательного {@code flow_tph} отклоняется
     * @throws Exception при ошибке парсинга
     */
    @Test
    void rejectHeatNetworkWithoutFlow() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode f = TestGeoJsonFactory.feature(
                "n1", "heat_network", "LineString",
                37.6, 55.75, 37.61, 55.76);
        ((ObjectNode) f.get("properties")).put("diameter", 200);
        ((ObjectNode) f.get("properties"))
                .put("upstream_object_id", "s1");
        TestGeoJsonFactory.addFeature(c, f);

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
    }

    /**
     * Регрессия: heat_chamber без {@code upstream_object_id} должна
     * проходить валидацию - атрибут не входит в required для камеры
     * @throws Exception при ошибке парсинга
     */
    @Test
    @DisplayName("HEAT_CHAMBER без upstream_object_id проходит")
    void heatChamberWithoutUpstreamShouldPass() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode ch = TestGeoJsonFactory.feature(
                "ch1", "heat_chamber", "Point", 37.6, 55.75);
        ((ObjectNode) ch.get("properties")).put("diameter", 200);
        TestGeoJsonFactory.addFeature(c, ch);

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount())
                .as("камера без upstream_object_id должна проходить")
                .isZero();
    }

    /**
     * oks_future без обязательного {@code flow_tph} отклоняется
     * @throws Exception при ошибке парсинга
     */
    @Test
    void rejectOksFutureWithoutFlow() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode f = TestGeoJsonFactory.feature(
                "o1", "oks_future", "Polygon",
                37.6, 55.75, 37.61, 55.75,
                37.61, 55.76, 37.6, 55.75);
        ((ObjectNode) f.get("properties")).put("heat_load", 1.0);
        TestGeoJsonFactory.addFeature(c, f);

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
    }

    /**
     * oks_connection_point без обязательного {@code oks_id} отклоняется
     * @throws Exception при ошибке парсинга
     */
    @Test
    void rejectOksConnectionPointWithoutOksId() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "cp1", "oks_connection_point", "Point",
                37.6, 55.75));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
    }

    /**
     * restriction без обязательного {@code restriction_type} отклоняется
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

    /**
     * {@code diameter} передан строкой вместо целого числа.
     * Ожидается ошибка типа
     * @throws Exception при ошибке парсинга
     */
    @Test
    void rejectDiameterAsString() throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        ObjectNode f = TestGeoJsonFactory.feature(
                "n1", "heat_network", "LineString",
                37.6, 55.75, 37.61, 55.76);
        ((ObjectNode) f.get("properties")).put("diameter", "200");
        ((ObjectNode) f.get("properties")).put("flow_tph", 10.0);
        ((ObjectNode) f.get("properties"))
                .put("upstream_object_id", "s1");
        TestGeoJsonFactory.addFeature(c, f);

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
    }

    /**
     * Вторая фича с тем же {@code id} в рамках одной загрузки
     * отклоняется как дубликат; первый объект сохранён, в БД
     * ровно одна запись
     * @throws Exception при ошибке парсинга
     */
    @Test
    void duplicateFeatureId_reportsErrorForSecondOccurrence()
            throws Exception {
        ObjectNode c = TestGeoJsonFactory.featureCollection();
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "same", "source", "Point", 37.6, 55.75));
        TestGeoJsonFactory.addFeature(c, TestGeoJsonFactory.feature(
                "same", "source", "Point", 37.7, 55.8));

        GeoJsonUploadResponse r = parser.processStream(
                new ByteArrayInputStream(
                        TestGeoJsonFactory.toBytes(c)),
                uploadId);

        assertThat(r.getTotalCount()).isEqualTo(1);
        assertThat(r.getTotalErrorsCount()).isEqualTo(1);
        assertThat(featureRepo.countByUploadId(uploadId))
                .isEqualTo(1);
    }
}
