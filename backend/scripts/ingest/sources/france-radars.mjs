import { fetchJson, fetchText } from "../lib/fetch.mjs";
import { saveRawDump } from "../lib/cache.mjs";

const DATASET_API =
  "https://www.data.gouv.fr/api/1/datasets/liste-des-radars-fixes-en-france/";
const FALLBACK_CSV =
  "https://static.data.gouv.fr/resources/liste-des-radars-fixes-en-france/20251230-134204/jeu-de-donnees-liste-des-radars-fixes-en-france-12-2025.csv";

export const id = "france-radars";

async function resolveCsvUrl() {
  try {
    const root = await fetchJson(DATASET_API, { label: "france-radars-dataset" });
    const resources = Array.isArray(root.resources) ? root.resources : [];
    const csvs = resources.filter((r) => {
      const format = String(r.format || "").toLowerCase();
      const mime = String(r.mime || "").toLowerCase();
      return format === "csv" || mime.includes("csv");
    });
    csvs.sort((a, b) =>
      String(b.last_modified || b.created_at || "").localeCompare(
        String(a.last_modified || a.created_at || ""),
      ),
    );
    if (csvs[0]?.url) return csvs[0].url;
  } catch {
    /* fallback */
  }
  return FALLBACK_CSV;
}

export async function ingest() {
  const url = await resolveCsvUrl();
  const csvText = await fetchText(url, { label: "france-radars-csv" });
  saveRawDump(id, "radars.csv", csvText);

  const lines = csvText
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean);
  if (lines.length === 0) return { table: "zones", source: id, rows: [] };

  let idIdx = 0;
  let typeIdx = 1;
  let vmaIdx = 3;
  let latIdx = 4;
  let lonIdx = 5;
  const header = lines[0].split(";").map((h) => h.trim().toLowerCase());
  header.forEach((colName, idx) => {
    if (colName.includes("numéro") || colName.includes("numero") || colName === "id")
      idIdx = idx;
    else if (colName.includes("type")) typeIdx = idx;
    else if (colName.includes("vma") || colName.includes("vitesse")) vmaIdx = idx;
    else if (colName.includes("lat")) latIdx = idx;
    else if (colName.includes("long") || colName.includes("lon")) lonIdx = idx;
  });

  const now = new Date().toISOString();
  const zones = [];
  for (let i = 1; i < lines.length; i++) {
    const parts = lines[i].split(";").map((p) => p.trim());
    if (parts.length <= Math.max(latIdx, lonIdx)) continue;
    const rid = parts[idIdx];
    if (!rid) continue;
    const lat = Number(String(parts[latIdx] || "").replace("+", "").replace(",", "."));
    const lon = Number(String(parts[lonIdx] || "").replace("+", "").replace(",", "."));
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue;
    const vmaRaw = parts[vmaIdx] || "NA";
    const vma = Number.parseInt(vmaRaw, 10);
    zones.push({
      id: `fr_radar_${rid}`,
      source: id,
      kind: "speed_control",
      lat,
      lon,
      radius_m: null,
      vma: Number.isFinite(vma) ? vma : null,
      updated_at: now,
      props: {
        type: parts[typeIdx] || "FIXE",
        aac_zone: true,
      },
    });
  }

  return { table: "zones", source: id, rows: zones };
}
