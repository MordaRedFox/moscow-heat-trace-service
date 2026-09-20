CREATE EXTENSION IF NOT EXISTS postgis;

-- Общая последовательность для всех гео-объектов
CREATE SEQUENCE IF NOT EXISTS geo_object_seq START 1 INCREMENT 50;

-- =====================  SOURCE  =====================
CREATE TABLE source (
    id            BIGINT PRIMARY KEY DEFAULT nextval('geo_object_seq'),
    feature_id    VARCHAR(255) NOT NULL UNIQUE,
    geometry      geometry(Geometry, 4326) NOT NULL,
    geometry_utm  geometry(Geometry, 32637) NOT NULL,
    properties    JSONB NOT NULL
);
CREATE INDEX idx_source_geom      ON source USING GIST (geometry);
CREATE INDEX idx_source_geom_utm  ON source USING GIST (geometry_utm);

-- =====================  HEAT_NETWORK  =====================
CREATE TABLE heat_network (
    id            BIGINT PRIMARY KEY DEFAULT nextval('geo_object_seq'),
    feature_id    VARCHAR(255) NOT NULL UNIQUE,
    geometry      geometry(LineString, 4326) NOT NULL,
    geometry_utm  geometry(LineString, 32637) NOT NULL,
    properties    JSONB NOT NULL,
    diameter      DOUBLE PRECISION,
    flow_tph      DOUBLE PRECISION,
    upstream_object_id VARCHAR(255)
);
CREATE INDEX idx_heat_network_geom      ON heat_network USING GIST (geometry);
CREATE INDEX idx_heat_network_geom_utm  ON heat_network USING GIST (geometry_utm);

-- =====================  HEAT_CHAMBER  =====================
CREATE TABLE heat_chamber (
    id            BIGINT PRIMARY KEY DEFAULT nextval('geo_object_seq'),
    feature_id    VARCHAR(255) NOT NULL UNIQUE,
    geometry      geometry(Point, 4326) NOT NULL,
    geometry_utm  geometry(Point, 32637) NOT NULL,
    properties    JSONB NOT NULL,
    diameter      DOUBLE PRECISION,
    upstream_object_id VARCHAR(255)
);
CREATE INDEX idx_heat_chamber_geom      ON heat_chamber USING GIST (geometry);
CREATE INDEX idx_heat_chamber_geom_utm  ON heat_chamber USING GIST (geometry_utm);

-- =====================  OKS_FUTURE  =====================
CREATE TABLE oks_future (
    id            BIGINT PRIMARY KEY DEFAULT nextval('geo_object_seq'),
    feature_id    VARCHAR(255) NOT NULL UNIQUE,
    geometry      geometry(Geometry, 4326) NOT NULL,
    geometry_utm  geometry(Geometry, 32637) NOT NULL,
    properties    JSONB NOT NULL,
    flow_tph      DOUBLE PRECISION,
    heat_load     DOUBLE PRECISION
);
CREATE INDEX idx_oks_future_geom      ON oks_future USING GIST (geometry);
CREATE INDEX idx_oks_future_geom_utm  ON oks_future USING GIST (geometry_utm);

-- =====================  OKS_CONNECTION_POINT  =====================
CREATE TABLE oks_connection_point (
    id            BIGINT PRIMARY KEY DEFAULT nextval('geo_object_seq'),
    feature_id    VARCHAR(255) NOT NULL UNIQUE,
    geometry      geometry(Point, 4326) NOT NULL,
    geometry_utm  geometry(Point, 32637) NOT NULL,
    properties    JSONB NOT NULL,
    oks_id        VARCHAR(255)
);
CREATE INDEX idx_oks_cp_geom      ON oks_connection_point USING GIST (geometry);
CREATE INDEX idx_oks_cp_geom_utm  ON oks_connection_point USING GIST (geometry_utm);

-- =====================  OKS_EXISTING  =====================
CREATE TABLE oks_existing (
    id            BIGINT PRIMARY KEY DEFAULT nextval('geo_object_seq'),
    feature_id    VARCHAR(255) NOT NULL UNIQUE,
    geometry      geometry(Geometry, 4326) NOT NULL,
    geometry_utm  geometry(Geometry, 32637) NOT NULL,
    properties    JSONB NOT NULL
);
CREATE INDEX idx_oks_existing_geom      ON oks_existing USING GIST (geometry);
CREATE INDEX idx_oks_existing_geom_utm  ON oks_existing USING GIST (geometry_utm);

-- =====================  RESTRICTION  =====================
CREATE TABLE restriction (
    id            BIGINT PRIMARY KEY DEFAULT nextval('geo_object_seq'),
    feature_id    VARCHAR(255) NOT NULL UNIQUE,
    geometry      geometry(Geometry, 4326) NOT NULL,
    geometry_utm  geometry(Geometry, 32637) NOT NULL,
    properties    JSONB NOT NULL,
    restriction_type VARCHAR(255)
);
CREATE INDEX idx_restriction_geom      ON restriction USING GIST (geometry);
CREATE INDEX idx_restriction_geom_utm  ON restriction USING GIST (geometry_utm);