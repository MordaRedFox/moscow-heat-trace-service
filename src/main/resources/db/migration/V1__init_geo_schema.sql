-- Расширение PostGIS должно существовать до создания geometry-колонок Hibernate-ом.
-- Скрипт идемпотентен и безопасен при каждом старте.
CREATE EXTENSION IF NOT EXISTS postgis;