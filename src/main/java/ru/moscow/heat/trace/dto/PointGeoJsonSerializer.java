package ru.moscow.heat.trace.dto;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.locationtech.jts.geom.Point;

import java.io.IOException;

/**
 * Сериализатор JTS Point в формат GeoJSON Point для REST API
 */
public class PointGeoJsonSerializer extends JsonSerializer<Point> {

    @Override
    public void serialize(Point point, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (point == null) {
            gen.writeNull();
            return;
        }
        gen.writeStartObject();
        gen.writeStringField("type", "Point");
        gen.writeArrayFieldStart("coordinates");
        gen.writeNumber(point.getX());
        gen.writeNumber(point.getY());
        gen.writeEndArray();
        gen.writeEndObject();
    }
}
