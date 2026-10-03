/**
 * Download OpenTollData JSON(s) from GitHub, merge, save locally + R2 key toll/open_toll_data.json.
 * No D1 writes (matrices stay on R2 only).
 */
import { saveRawDump } from "../lib/cache.mjs";
import { spawnSync } from "node:child_process";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { writeFileSync } from "node:fs";

const __dirname = dirname(fileURLToPath(import.meta.url));
const BACKEND_ROOT = join(__dirname, "../../..");
const SOURCE = "open-toll-data";
const GITHUB_API =
  "https://api.github.com/repos/louis2038/OpenTollData/contents/TollPrice_DataBase";
const RAW_BASE =
  "https://raw.githubusercontent.com/louis2038/OpenTollData/main/TollPrice_DataBase";
/** Canonical object served by Worker GET. */
export const TOLL_R2_KEY = "toll/open_toll_data.json";

function mergeOpenTollData(parts) {
  const networks = [];
  const toll_description = {};
  const open_toll_price = {};
  let date;
  let version;
  for (const part of parts) {
    if (Array.isArray(part.networks)) networks.push(...part.networks);
    if (part.toll_description) Object.assign(toll_description, part.toll_description);
    if (part.open_toll_price) Object.assign(open_toll_price, part.open_toll_price);
    if (part.date) date = part.date;
    if (part.version) version = part.version;
  }
  return { networks, toll_description, open_toll_price, date, version };
}

async function listTollJsonFiles() {
  const res = await fetch(GITHUB_API, {
    headers: { Accept: "application/vnd.github+json", "User-Agent": "gaston-backend" },
  });
  if (!res.ok) {
    // Fallback: known AREA file only
    return ["toll_price_AREA.json"];
  }
  const items = await res.json();
  if (!Array.isArray(items)) return ["toll_price_AREA.json"];
  return items
    .filter((i) => i.type === "file" && /^toll_price_.*\.json$/i.test(i.name))
    .map((i) => i.name);
}

/**
 * @returns {{ table: "r2_only", source: string, files: string[], booths: number }}
 */
export async function ingest() {
  const names = await listTollJsonFiles();
  if (names.length === 0) {
    throw new Error("No toll_price_*.json found in OpenTollData");
  }
  const parts = [];
  for (const name of names) {
    const url = `${RAW_BASE}/${name}`;
    const res = await fetch(url, { headers: { "User-Agent": "gaston-backend" } });
    if (!res.ok) {
      console.warn(
        JSON.stringify({ event: "toll_skip_file", file: name, status: res.status }),
      );
      continue;
    }
    const text = await res.text();
    saveRawDump(SOURCE, name, text);
    parts.push(JSON.parse(text));
  }
  if (parts.length === 0) {
    throw new Error("Failed to download any OpenTollData JSON");
  }
  const merged = mergeOpenTollData(parts);
  merged.ingested_at = new Date().toISOString();
  merged.source_files = names;
  const mergedJson = JSON.stringify(merged);
  const mergedPath = saveRawDump(SOURCE, "open_toll_data.json", mergedJson);
  // Also write under backend/.cache/toll for local wrangler if needed
  writeFileSync(join(BACKEND_ROOT, ".cache", "open_toll_data.json"), mergedJson);

  return {
    table: "r2_only",
    source: SOURCE,
    files: names,
    booths: Object.keys(merged.toll_description || {}).length,
    mergedPath,
    r2Key: TOLL_R2_KEY,
  };
}

/** Upload canonical merged file to R2 (remote ingest). */
export function uploadCanonicalToll(mergedPath, { local = true } = {}) {
  if (local) return { skipped: true, reason: "local" };
  const result = spawnSync(
    "npx",
    [
      "wrangler",
      "r2",
      "object",
      "put",
      `gaston-dumps/${TOLL_R2_KEY}`,
      "--file",
      mergedPath,
      "--remote",
    ],
    { cwd: BACKEND_ROOT, encoding: "utf8", maxBuffer: 10 * 1024 * 1024 },
  );
  if (result.status !== 0) {
    const err = (result.stderr || result.stdout || "").slice(0, 1000);
    throw new Error(`R2 put failed ${TOLL_R2_KEY}: ${err}`);
  }
  return { key: TOLL_R2_KEY };
}
