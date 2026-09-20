package ru.moscow.heat.geojson.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.springframework.stereotype.Service;
import ru.moscow.heat.geojson.exception.GeoJsonParseException;

/**
 * Конвертер между представлениями геометрии:
 * JsonNode (GeoJSON) &lt;-&gt; org.locationtech.jts.geom.Geometry.
 *
 * <p>Используется парсером для построения PostGIS-колонок и любыми
 * сервисами для выгрузки геометрии обратно в GeoJSON.
 */
@Service
@RequiredArgsConstructor
public class GeometryConverterService {

    private final ObjectMapper objectMapper;

    private final GeoJsonReader geoJsonReader = new GeoJsonReader();
    private final GeoJsonWriter geoJsonWriter = new GeoJsonWriter();

    /**
     * GeoJSON-узел геометрии -&gt; JTS Geometry.
     * SRID вызывающий код проставляет сам (4326 для входных данных)
     *
     * @throws GeoJsonParseException если JTS не смог разобрать структуру
     */
    public Geometry fromGeoJson(JsonNode geometryNode) {
        if (geometryNode == null || geometryNode.isNull()) {
            throw new GeoJsonParseException("geometry отсутствует");
        }
        try {
            return geoJsonReader.read(geometryNode.toString());
        } catch (ParseException e) {
            throw new GeoJsonParseException(
                    "Не удалось разобрать геометрию средствами JTS: " + e.getMessage());
        }
    }

    /**
     * JTS Geometry -&gt; JsonNode (GeoJSON) для выгрузки результата
     */
    public JsonNode toGeoJson(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        try {
            return objectMapper.readTree(geoJsonWriter.write(geometry));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Не удалось сериализовать геометрию в GeoJSON: " + e.getMessage(), e);
        }
    }
}