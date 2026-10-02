-- Phase 0 schema: POIs (IRVE/fuel dumps) + AAC zones + ingest metadata.
-- Writes are applied by GitHub Actions ingest scripts, not by the Worker hot path.

CREATE TABLE IF NOT EXISTS pois (
  id TEXT PRIMARY KEY,
  source TEXT NOT NULL,
  category TEXT NOT NULL,
  lat REAL NOT NULL,
  lon REAL NOT NULL,
  name TEXT,
  station_id TEXT,
  props_json TEXT NOT NULL DEFAULT '{}',
  etat TEXT,
  occupation TEXT,
  updated_at TEXT NOT NULL,
  dynamic_updated_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_pois_lat_lon ON pois(lat, lon);
CREATE INDEX IF NOT EXISTS idx_pois_source ON pois(source);
CREATE INDEX IF NOT EXISTS idx_pois_station ON pois(station_id);
CREATE INDEX IF NOT EXISTS idx_pois_category ON pois(category);

CREATE TABLE IF NOT EXISTS zones (
  id TEXT PRIMARY KEY,
  source TEXT NOT NULL,
  kind TEXT NOT NULL,
  lat REAL NOT NULL,
  lon REAL NOT NULL,
  radius_m REAL,
  vma INTEGER,
  props_json TEXT NOT NULL DEFAULT '{}',
  updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_zones_lat_lon ON zones(lat, lon);
CREATE INDEX IF NOT EXISTS idx_zones_source ON zones(source);

CREATE TABLE IF NOT EXISTS ingest_meta (
  source TEXT PRIMARY KEY,
  last_success_at TEXT,
  etag_or_version TEXT,
  row_count INTEGER,
  write_count INTEGER
);
