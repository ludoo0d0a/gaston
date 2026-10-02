#!/usr/bin/env node
/**
 * Phase 1 ingest CLI — runs outside the Worker (Free CPU limit).
 *
 * Usage:
 *   node scripts/ingest/run.mjs --source=luxembourg-radars --local
 *   node scripts/ingest/run.mjs --source=france-radars --local
 *   node scripts/ingest/run.mjs --source=dotnl --remote
 *   node scripts/ingest/run.mjs --source=dotnl --remote --replace   # DELETE+INSERT (≈2× writes)
 *
 * Free D1 write budget ~100k/day — prefer one large source per run.
 * Limit resets 00:00 UTC; over-limit blocks ALL D1 queries (reads too).
 */
import {
  POI_COLUMNS,
  ZONE_COLUMNS,
  poiSqlCells,
  replaceSourceRows,
  zoneSqlCells,
} from "./lib/d1.mjs";
import { uploadRawToR2 } from "./lib/cache.mjs";
import { dedupeRadarZones } from "./lib/merge.mjs";
import { readdirSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));

/** Soft refuse remote writes above this unless --force (Free = 100k/day). */
const REMOTE_WRITE_SOFT_LIMIT = 90_000;

const SOURCES = {
  gireve: () => import("./sources/gireve.mjs"),
  qualicharge: () => import("./sources/qualicharge.mjs"),
  "france-radars": () => import("./sources/france-radars.mjs"),
  "luxembourg-radars": () => import("./sources/luxembourg-radars.mjs"),
  minetur: () => import("./sources/minetur.mjs"),
  mimit: () => import("./sources/mimit.mjs"),
  "merged-irve": () => import("./sources/merged-irve.mjs"),
  "belgium-nap": () => import("./sources/belgium-nap.mjs"),
  dotnl: () => import("./sources/dotnl.mjs"),
  "fuel-history": () => import("./sources/fuel-history.mjs"),
};

function parseArgs(argv) {
  const out = {
    source: null,
    local: true,
    remote: false,
    help: false,
    replace: false,
    force: false,
  };
  for (const a of argv) {
    if (a === "--help" || a === "-h") out.help = true;
    else if (a === "--remote") {
      out.remote = true;
      out.local = false;
    } else if (a === "--local") out.local = true;
    else if (a === "--replace") out.replace = true;
    else if (a === "--force") out.force = true;
    else if (a.startsWith("--source=")) out.source = a.slice("--source=".length);
  }
  return out;
}

function printHelp() {
  console.log(`Available sources: ${Object.keys(SOURCES).join(", ")}, all
Flags: --source=<id|all> [--local|--remote] [--replace] [--force]
  --replace  DELETE source rows then INSERT (≈2× Free write cost)
  --force    allow remote writes estimated over ${REMOTE_WRITE_SOFT_LIMIT}`);
}

function assertRemoteBudget({ sourceId, rowCount, mode, force }) {
  const estimated = mode === "replace" ? rowCount * 2 : rowCount;
  console.log(
    JSON.stringify({
      event: "write_budget_estimate",
      source: sourceId,
      rows: rowCount,
      mode,
      estimated_row_writes: estimated,
      free_daily_cap: 100_000,
    }),
  );
  if (estimated > REMOTE_WRITE_SOFT_LIMIT && !force) {
    throw new Error(
      `Refusing remote ingest for ${sourceId}: ~${estimated} row writes ` +
        `(Free cap 100k/day). Wait until 00:00 UTC, split sources across days, ` +
        `prefer upsert (default), or pass --force.`,
    );
  }
}

async function runOne(sourceId, { local, replace, force }) {
  const loader = SOURCES[sourceId];
  if (!loader) throw new Error(`Unknown source: ${sourceId}`);
  const mod = await loader();
  console.log(JSON.stringify({ event: "ingest_start", source: sourceId, local }));
  const result = await mod.ingest();
  const rowCount =
    result.table === "fuel"
      ? (result.national?.length ?? 0) + (result.market?.length ?? 0)
      : result.rows?.length ?? 0;
  console.log(
    JSON.stringify({
      event: "ingest_parsed",
      source: sourceId,
      table: result.table,
      rows: rowCount,
    }),
  );

  const mode = replace ? "replace" : "upsert";
  if (!local && result.table !== "fuel") {
    assertRemoteBudget({ sourceId, rowCount, mode, force });
  }

  const dumpDir = join(__dirname, "../../.cache/dumps", sourceId);
  try {
    for (const name of readdirSync(dumpDir)) {
      uploadRawToR2(sourceId, name, join(dumpDir, name), { local });
    }
  } catch (e) {
    if (e && e.code !== "ENOENT") {
      console.warn(JSON.stringify({ event: "r2_skip", error: String(e.message || e) }));
    }
  }

  if (result.table === "fuel") {
    if (!local && !force && rowCount > REMOTE_WRITE_SOFT_LIMIT) {
      assertRemoteBudget({ sourceId, rowCount, mode: "upsert", force });
    }
    const { writeFuelTables } = await import("./sources/fuel-history.mjs");
    const written = writeFuelTables({
      national: result.national,
      market: result.market,
      local,
    });
    console.log(JSON.stringify({ event: "ingest_done", source: sourceId, ...written }));
  } else if (result.table === "pois") {
    const sqlRows = result.rows.map((r) => poiSqlCells(r));
    // Merged JSON is fat; DOT-NL station rows are lean but numerous.
    const batchSize = sourceId.startsWith("merged")
      ? 15
      : sourceId === "dotnl"
        ? 120
        : 40;
    const written = replaceSourceRows({
      table: "pois",
      source: result.source,
      columns: POI_COLUMNS,
      rows: sqlRows,
      local,
      batchSize,
      mode,
    });
    console.log(JSON.stringify({ event: "ingest_done", source: sourceId, ...written }));
  } else if (result.table === "zones") {
    const deduped = dedupeRadarZones(result.rows);
    if (deduped.length !== result.rows.length) {
      console.log(
        JSON.stringify({
          event: "radar_dedupe",
          before: result.rows.length,
          after: deduped.length,
        }),
      );
    }
    const sqlRows = deduped.map((r) => zoneSqlCells(r));
    const written = replaceSourceRows({
      table: "zones",
      source: result.source,
      columns: ZONE_COLUMNS,
      rows: sqlRows,
      local,
      mode,
    });
    console.log(JSON.stringify({ event: "ingest_done", source: sourceId, ...written }));
  } else {
    throw new Error(`Unknown table ${result.table}`);
  }
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help || !args.source) {
    printHelp();
    process.exit(args.help ? 0 : 1);
  }

  const list =
    args.source === "all" ? Object.keys(SOURCES) : [args.source];

  if (args.source === "all") {
    console.warn(
      JSON.stringify({
        event: "write_budget_warning",
        message:
          "Running all sources may exceed D1 Free 100k writes/day. Prefer one source per day.",
      }),
    );
  }

  for (const sid of list) {
    await runOne(sid, {
      local: args.local,
      replace: args.replace,
      force: args.force,
    });
  }
}

main().catch((err) => {
  console.error(JSON.stringify({ event: "ingest_error", error: String(err.stack || err) }));
  process.exit(1);
});
