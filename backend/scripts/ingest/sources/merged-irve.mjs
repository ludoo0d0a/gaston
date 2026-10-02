/**
 * Merge Gireve + QualiCharge into source=`merged-irve` (PoiMerger-like rules).
 * Prefers `.cache/dumps/{gireve,qualicharge}` when present to avoid re-download.
 */
import { mergePoisGrid } from "../lib/merge.mjs";
import { readFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { col, headerIndex, parseCsv, parseCoordonneesXy } from "../lib/csv.mjs";
import * as gireve from "./gireve.mjs";
import * as qualicharge from "./qualicharge.mjs";

export const id = "merged-irve";

const __dirname = dirname(fileURLToPath(import.meta.url));
const DUMP_ROOT = join(__dirname, "../../../.cache/dumps");

function parseIrveFromCache(sourceId, idPrefix) {
  const staticPath = join(DUMP_ROOT, sourceId, "static.csv");
  const dynamicPath = join(DUMP_ROOT, sourceId, "dynamic.csv");
  if (!existsSync(staticPath) || !existsSync(dynamicPath)) return null;
  const staticText = readFileSync(staticPath, "utf8");
  const dynamicText = readFileSync(dynamicPath, "utf8");
  const staticParsed = parseCsv(staticText);
  const dynParsed = parseCsv(dynamicText);
  const sIdx = headerIndex(staticParsed.header);
  const dIdx = headerIndex(dynParsed.header);
  const dynamicByPdc = new Map();
  for (const row of dynParsed.rows) {
    const pdc = col(row, dIdx, "id_pdc_itinerance");
    if (!pdc) continue;
    dynamicByPdc.set(pdc, {
      etat: col(row, dIdx, "etat_pdc"),
      occupation: col(row, dIdx, "occupation_pdc"),
    });
  }
  const now = new Date().toISOString();
  const pois = [];
  for (const row of staticParsed.rows) {
    const pdc = col(row, sIdx, "id_pdc_itinerance");
    if (!pdc) continue;
    const xy = parseCoordonneesXy(col(row, sIdx, "coordonneesXY"));
    if (!xy) continue;
    const dyn = dynamicByPdc.get(pdc);
    pois.push({
      id: `${idPrefix}:${pdc}`,
      source: sourceId,
      category: "irve",
      lat: xy.lat,
      lon: xy.lon,
      name: col(row, sIdx, "nom_station"),
      station_id: col(row, sIdx, "id_station_itinerance"),
      etat: dyn?.etat ?? null,
      occupation: dyn?.occupation ?? null,
      updated_at: now,
      dynamic_updated_at: dyn ? now : null,
      props: {
        operator: col(row, sIdx, "nom_operateur"),
        enseigne: col(row, sIdx, "nom_enseigne"),
        address: col(row, sIdx, "adresse_station"),
        puissance:
          col(row, sIdx, "puissance_nominale") ??
          col(row, sIdx, "puissance_maximale"),
      },
    });
  }
  return pois;
}

export async function ingest() {
  let gRows = parseIrveFromCache("gireve", "gireve");
  let qRows = parseIrveFromCache("qualicharge", "qualicharge");
  if (!gRows) {
    const g = await gireve.ingest();
    gRows = g.rows;
  }
  if (!qRows) {
    const q = await qualicharge.ingest();
    qRows = q.rows;
  }

  const combined = [...gRows, ...qRows];
  console.log(
    JSON.stringify({
      event: "merge_input",
      gireve: gRows.length,
      qualicharge: qRows.length,
      combined: combined.length,
    }),
  );
  const merged = mergePoisGrid(combined, 0.02);
  const now = new Date().toISOString();
  const rows = merged.map((p) => ({
    ...p,
    source: id,
    id: p.station_id
      ? `merged-irve:${p.station_id}`
      : `merged-irve:${String(p.id).replace(/^(gireve|qualicharge|merged:)+/, "")}`,
    updated_at: now,
    props: {
      operator: p.props?.operator ?? null,
      enseigne: p.props?.enseigne ?? null,
      address: p.props?.address ?? null,
      puissance: p.props?.puissance ?? null,
      merged_from: p.props?.merged_from || [p.source],
    },
  }));
  console.log(
    JSON.stringify({
      event: "merge_output",
      rows: rows.length,
      saved: combined.length - rows.length,
    }),
  );
  return { table: "pois", source: id, rows };
}
