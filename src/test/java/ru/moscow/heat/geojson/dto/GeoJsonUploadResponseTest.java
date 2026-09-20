package ru.moscow.heat.geojson.dto;

import org.junit.jupiter.api.Test;
import ru.moscow.heat.geojson.ObjectType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты накопителя результатов парсинга {@link GeoJsonUploadResponse}.
 * Проверяют работу счетчиков по типам объектов, счетчика ошибок,
 * усечения списка ошибок и расширения bbox
 */
class GeoJsonUploadResponseTest {

    /**
     * Каждый вызов {@code incrementCount} увеличивает счетчик
     * соответствующего типа и общий счетчик объектов
     */
    @Test
    void incrementCount_growsTypeAndTotal() {
        GeoJsonUploadResponse r = new GeoJsonUploadResponse();
        r.incrementCount(ObjectType.HEAT_NETWORK);
        r.incrementCount(ObjectType.HEAT_NETWORK);
        r.incrementCount(ObjectType.SOURCE);

        assertThat(r.getTotalCount()).isEqualTo(3);
        assertThat(r.getCountsByType())
                .containsEntry(ObjectType.HEAT_NETWORK, 2)
                .containsEntry(ObjectType.SOURCE, 1);
    }

    /**
     * {@code addError} увеличивает общий счетчик ошибок и добавляет
     * запись в список. При небольшом числе ошибок усечения нет
     */
    @Test
    void addError_growsTotalAndKeepsList() {
        GeoJsonUploadResponse r = new GeoJsonUploadResponse();
        r.addError("f1", "bad");
        r.addError("f2", "worse");

        assertThat(r.getTotalErrorsCount()).isEqualTo(2);
        assertThat(r.getErrors()).hasSize(2);
        assertThat(r.isErrorsTruncated()).isFalse();
    }

    /**
     * При превышении лимита список ошибок обрезается до 1000
     * элементов, но общий счетчик продолжает расти, а признак
     * усечения становится {@code true}
     */
    @Test
    void addError_truncatesListButKeepsTotalCounter() {
        GeoJsonUploadResponse r = new GeoJsonUploadResponse();
        for (int i = 0; i < 1500; i++) {
            r.addError("f" + i, "e");
        }
        assertThat(r.getTotalErrorsCount()).isEqualTo(1500);
        assertThat(r.getErrors()).hasSize(1000);
        assertThat(r.isErrorsTruncated()).isTrue();
    }

    /**
     * Bbox расширяется по каждой новой координате и возвращается
     * в порядке {@code [minX, minY, maxX, maxY]}
     */
    @Test
    void updateBbox_expands() {
        GeoJsonUploadResponse r = new GeoJsonUploadResponse();
        r.updateBbox(10, 20);
        r.updateBbox(5, 30);
        r.updateBbox(15, 25);

        assertThat(r.getBbox())
                .containsExactly(5.0, 20.0, 15.0, 30.0);
    }

    /**
     * Если bbox не заполнен ни одной точкой,
     * {@code hasBbox} возвращает {@code false}, а {@code getBbox} -
     * {@code null}
     */
    @Test
    void getBbox_nullWhenEmpty() {
        GeoJsonUploadResponse r = new GeoJsonUploadResponse();
        assertThat(r.hasBbox()).isFalse();
        assertThat(r.getBbox()).isNull();
    }
}
