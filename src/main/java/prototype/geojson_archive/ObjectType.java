package prototype.geojson_archive;

public enum ObjectType {
    SOURCE("source"),
    HEAT_NETWORK("heat_network"),
    HEAT_CHAMBER("heat_chamber"),
    OKS_FUTURE("oks_future"),
    OKS_CONNECTION_POINT("oks_connection_point"),
    OKS_EXISTING("oks_existing"),
    RESTRICTION("restriction");

    private final String code;

    ObjectType(String code) { this.code = code; }

    public String getCode() { return code; }

    public static ObjectType fromCode(String code) {
        for (ObjectType t : values()) {
            if (t.code.equals(code)) return t;
        }
        return null; // неизвестный тип — обработаем как ошибку валидации
    }
}