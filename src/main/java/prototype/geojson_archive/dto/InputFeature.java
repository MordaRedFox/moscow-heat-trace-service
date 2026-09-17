package prototype.geojson_archive.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;
import prototype.geojson_archive.ObjectType;

@Getter
@Setter
public class InputFeature {
    private int index;              // позиция в массиве features — удобно для сообщений об ошибках
    private String id;
    private ObjectType objectType;
    private String geometryType;    // "Point", "LineString" и т.д.
    private JsonNode rawProperties; // остальные атрибуты читаем по месту, без отдельного DTO на каждый тип
}