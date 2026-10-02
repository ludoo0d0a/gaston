/**
 * Daily FR national pump averages (data.economie) + Stooq market closes → D1.
 * Low write count — safe for Free daily schedule.
 */
import { fetchText } from "../lib/fetch.mjs";
import { saveRawDump } from "../lib/cache.mjs";
import { sqlNum, sqlString } from "../lib/d1.mjs";
import {
  parseNationalFrResults,
  parseStooqCsv,
  parseYahooChart,
} from "../lib/fuelParse.mjs";
import { mkdirSync, writeFileSync, rmSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

export const id = "fuel-history";

const __dirname = dirname(fileURLToPath(import.meta.url));
const BACKEND_ROOT = join(__dirname, "../../..");
const CACHE_SQL = join(BACKEND_ROOT, ".cache/sql");

const QUOTIDIEN_BASE =
  "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-carburants-quotidien";

/** Canonical D1 symbols (same as app StooqSymbols). */
const MARKET_SYMBOLS = {
  "brent.uk": {
    stooq: "https://stooq.com/q/d/l/?s=brent.uk&i=d",
    yahoo: "BZ=F",
  },
  "ho.f": {
    stooq: "https://stooq.com/q/d/l/?s=ho.f&i=d",
    yahoo: "HO=F",
  },
  eurusd: {
    stooq: "https://stooq.com/q/d/l/?s=eurusd&i=d",
    yahoo: "EURUSD=X",
  },
};

function isoDay(y, m, d) {
  return `${String(y).padStart(4, "0")}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
}

function dayAfter(iso) {
  const [ys, ms, ds] = iso.split("-").map(Number);
  const dt = new Date(Date.UTC(ys, ms - 1, ds));
  dt.setUTCDate(dt.getUTCDate() + 1);
  return isoDay(dt.getUTCFullYear(), dt.getUTCMonth() + 1, dt.getUTCDate());
}

function todayParis() {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/Paris",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}

function daysAgo(iso, n) {
  const [ys, ms, ds] = iso.split("-").map(Number);
  const dt = new Date(Date.UTC(ys, ms - 1, ds));
  dt.setUTCDate(dt.getUTCDate() - n);
  return isoDay(dt.getUTCFullYear(), dt.getUTCMonth() + 1, dt.getUTCDate());
}

function runSqlFile(file, local) {
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
    throw new Error(
      `wrangler d1 execute failed: ${(result.stderr || result.stdout || "").slice(0, 2000)}`,
    );
  }
}

async function fetchMarketCloses(symbol, urls) {
  try {
    const csv = await fetchText(urls.stooq, {
      label: `stooq-${symbol}`,
      headers: {
        accept: "text/csv,text/plain,*/*",
        referer: "https://stooq.com/",
      },
    });
    saveRawDump(id, `stooq-${symbol.replace(".", "_")}.csv`, csv);
    const rows = parseStooqCsv(csv, 40);
    if (rows.length > 0) return rows;
  } catch (e) {
    console.warn(
      JSON.stringify({
        event: "stooq_skip",
        symbol,
        error: String(e.message || e).slice(0, 180),
      }),
    );
  }

  const yahooUrl =
    `https://query2.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(urls.yahoo)}` +
    `?interval=1d&range=2mo`;
  const body = await fetchText(yahooUrl, {
    label: `yahoo-${symbol}`,
    headers: {
      accept: "application/json",
      "user-agent":
        "Mozilla/5.0 (compatible; gaston-backend-ingest/0.1; +https://github.com/geoking)",
    },
  });
  saveRawDump(id, `yahoo-${symbol.replace(".", "_")}.json`, body);
  return parseYahooChart(body, 40);
}

async function fetchNationalFr(fromDay, toDay) {
  const fromIso = `${fromDay}T00:00:00`;
  const toExclusive = dayAfter(toDay);
  const where = `prix_maj >= '${fromIso}' AND prix_maj < '${toExclusive}T00:00:00'`;
  // Aliased select is required: bare year()/month()/day() fields come back null from ODS.
  const select =
    "prix_nom,year(prix_maj) as y,month(prix_maj) as m,day(prix_maj) as d,avg(prix_valeur) as avg_eur";
  const groupBy = "prix_nom,year(prix_maj),month(prix_maj),day(prix_maj)";
  const orderBy = "year(prix_maj) DESC,month(prix_maj) DESC,day(prix_maj) DESC";
  const url =
    `${QUOTIDIEN_BASE}/records?` +
    `select=${encodeURIComponent(select)}&` +
    `where=${encodeURIComponent(where)}&` +
    `group_by=${encodeURIComponent(groupBy)}&` +
    `order_by=${encodeURIComponent(orderBy)}&` +
    `limit=400`;
  const body = await fetchText(url, { label: "fuel-national-fr", timeoutMs: 120_000 });
  saveRawDump(id, "national-fr.json", body);
  return parseNationalFrResults(JSON.parse(body));
}

/**
 * Special ingest: writes fuel_* tables (not pois/zones).
 * Returned shape for run.mjs branch `fuel`.
 */
export async function ingest() {
  const toDay = todayParis();
  const fromDay = daysAgo(toDay, 30);
  const national = await fetchNationalFr(fromDay, toDay);

  const market = [];
  for (const [symbol, urls] of Object.entries(MARKET_SYMBOLS)) {
    try {
      for (const row of await fetchMarketCloses(symbol, urls)) {
        market.push({ symbol, day: row.day, close: row.close });
      }
    } catch (e) {
      console.warn(
        JSON.stringify({
          event: "market_skip",
          symbol,
          error: String(e.message || e).slice(0, 180),
        }),
      );
    }
  }

  if (national.length === 0) {
    throw new Error("fuel-history: national FR fetch returned 0 rows");
  }

  return {
    table: "fuel",
    source: id,
    national,
    market,
  };
}

export function writeFuelTables({ national, market, local = true }) {
  mkdirSync(CACHE_SQL, { recursive: true });
  const stamp = Date.now();
  const now = new Date().toISOString();
  const files = [];
  let writes = 0;

  const flush = (name, sql) => {
    const file = join(CACHE_SQL, `${name}_${stamp}.sql`);
    writeFileSync(file, sql, "utf8");
    files.push(file);
    runSqlFile(file, local);
  };

  flush(
    "fuel_cleanup",
    `DELETE FROM fuel_national_daily WHERE day < '2000-01-01';`,
  );

  const batchSize = 40;
  for (let i = 0; i < national.length; i += batchSize) {
    const chunk = national.slice(i, i + batchSize);
    const values = chunk
      .map(
        (r) =>
          `(${sqlString(r.country)}, ${sqlString(r.fuel_id)}, ${sqlString(r.day)}, ${sqlNum(r.avg_eur)}, ${sqlString(now)})`,
      )
      .join(",\n");
    flush(
      `fuel_nat_${i}`,
      `INSERT INTO fuel_national_daily (country, fuel_id, day, avg_eur, updated_at) VALUES\n${values}\nON CONFLICT(country, fuel_id, day) DO UPDATE SET avg_eur = excluded.avg_eur, updated_at = excluded.updated_at;`,
    );
    writes += chunk.length;
  }

  for (let i = 0; i < market.length; i += batchSize) {
    const chunk = market.slice(i, i + batchSize);
    const values = chunk
      .map(
        (r) =>
          `(${sqlString(r.symbol)}, ${sqlString(r.day)}, ${sqlNum(r.close)}, ${sqlString(now)})`,
      )
      .join(",\n");
    flush(
      `fuel_mkt_${i}`,
      `INSERT INTO fuel_market_daily (symbol, day, close, updated_at) VALUES\n${values}\nON CONFLICT(symbol, day) DO UPDATE SET close = excluded.close, updated_at = excluded.updated_at;`,
    );
    writes += chunk.length;
  }

  flush(
    "fuel_meta",
    `INSERT INTO ingest_meta (source, last_success_at, etag_or_version, row_count, write_count)
VALUES (${sqlString(id)}, ${sqlString(now)}, NULL, ${national.length + market.length}, ${writes})
ON CONFLICT(source) DO UPDATE SET
  last_success_at = excluded.last_success_at,
  row_count = excluded.row_count,
  write_count = excluded.write_count;`,
  );

  for (const f of files) {
    try {
      rmSync(f);
    } catch {
      /* ignore */
    }
  }
  return { written: writes, national: national.length, market: market.length };
}
