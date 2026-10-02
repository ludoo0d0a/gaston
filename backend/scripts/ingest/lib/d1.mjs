import { mkdirSync, writeFileSync, rmSync } from "node:fs";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname } from "node:path";

const __dirname = dirname(fileURLToPath(import.meta.url));
const BACKEND_ROOT = join(__dirname, "../../..");
const CACHE_SQL = join(BACKEND_ROOT, ".cache/sql");

export function sqlString(value) {
  if (value == null) return "NULL";
  return `'${String(value).replaceAll("'", "''")}'`;
}

export function sqlNum(value) {
  if (value == null || !Number.isFinite(Number(value))) return "NULL";
  return String(Number(value));
}

/**
 * Replace all rows for a source in `pois` or `zones`, then insert in batches.
 * Uses wrangler d1 execute (--local or --remote).
 */
export function replaceSourceRows({
  table,
  source,
  columns,
  rows,
  local = true,
  batchSize = 80,
}) {
  if (table !== "pois" && table !== "zones") {
    throw new Error(`unsupported table ${table}`);
  }
  mkdirSync(CACHE_SQL, { recursive: true });
  const stamp = Date.now();
  const files = [];

  const deleteSql = `DELETE FROM ${table} WHERE source = ${sqlString(source)};`;
  const deleteFile = join(CACHE_SQL, `${source}_delete_${stamp}.sql`);
  writeFileSync(deleteFile, deleteSql, "utf8");
  files.push(deleteFile);
  runWranglerSqlFile(deleteFile, local);

  let written = 0;
  for (let i = 0; i < rows.length; i += batchSize) {
    const chunk = rows.slice(i, i + batchSize);
    const values = chunk
      .map((row) => `(${columns.map((c) => row[c]).join(", ")})`)
      .join(",\n");
    const sql = `INSERT INTO ${table} (${columns.join(", ")}) VALUES\n${values};`;
    const file = join(CACHE_SQL, `${source}_ins_${stamp}_${i}.sql`);
    writeFileSync(file, sql, "utf8");
    files.push(file);
    runWranglerSqlFile(file, local);
    written += chunk.length;
  }

  const metaSql = `
INSERT INTO ingest_meta (source, last_success_at, etag_or_version, row_count, write_count)
VALUES (${sqlString(source)}, ${sqlString(new Date().toISOString())}, NULL, ${written}, ${written + 1})
ON CONFLICT(source) DO UPDATE SET
  last_success_at = excluded.last_success_at,
  row_count = excluded.row_count,
  write_count = excluded.write_count;
`.trim();
  const metaFile = join(CACHE_SQL, `${source}_meta_${stamp}.sql`);
  writeFileSync(metaFile, metaSql, "utf8");
  files.push(metaFile);
  runWranglerSqlFile(metaFile, local);

  for (const f of files) {
    try {
      rmSync(f);
    } catch {
      /* ignore */
    }
  }

  return { written, deleteWrites: 1 };
}

function runWranglerSqlFile(file, local) {
  const args = [
    "wrangler",
    "d1",
    "execute",
    "gaston-pois",
    local ? "--local" : "--remote",
    "--file",
    file,
    "--yes",
  ];
  const result = spawnSync("npx", args, {
    cwd: BACKEND_ROOT,
    encoding: "utf8",
    maxBuffer: 20 * 1024 * 1024,
  });
  if (result.status !== 0) {
    const err = (result.stderr || result.stdout || "").slice(0, 2000);
    throw new Error(`wrangler d1 execute failed (${file}): ${err}`);
  }
}

/** Build SQL-ready cell map for a pois row. */
export function poiSqlCells(poi) {
  return {
    id: sqlString(poi.id),
    source: sqlString(poi.source),
    category: sqlString(poi.category),
    lat: sqlNum(poi.lat),
    lon: sqlNum(poi.lon),
    name: sqlString(poi.name),
    station_id: sqlString(poi.station_id),
    props_json: sqlString(JSON.stringify(poi.props ?? {})),
    etat: sqlString(poi.etat),
    occupation: sqlString(poi.occupation),
    updated_at: sqlString(poi.updated_at),
    dynamic_updated_at: sqlString(poi.dynamic_updated_at),
  };
}

export const POI_COLUMNS = [
  "id",
  "source",
  "category",
  "lat",
  "lon",
  "name",
  "station_id",
  "props_json",
  "etat",
  "occupation",
  "updated_at",
  "dynamic_updated_at",
];

export function zoneSqlCells(zone) {
  return {
    id: sqlString(zone.id),
    source: sqlString(zone.source),
    kind: sqlString(zone.kind),
    lat: sqlNum(zone.lat),
    lon: sqlNum(zone.lon),
    radius_m: sqlNum(zone.radius_m),
    vma: sqlNum(zone.vma),
    props_json: sqlString(JSON.stringify(zone.props ?? {})),
    updated_at: sqlString(zone.updated_at),
  };
}

export const ZONE_COLUMNS = [
  "id",
  "source",
  "kind",
  "lat",
  "lon",
  "radius_m",
  "vma",
  "props_json",
  "updated_at",
];
