-- OpenTreeMap modern-v4 database bootstrap.
-- The postgis/postgis image normally enables PostGIS already, but keeping
-- this idempotent makes the expected extensions explicit.
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS hstore;
