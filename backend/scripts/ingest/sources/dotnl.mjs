/**
 * DOT-NL / NDW OCPI locations → one POI per station (Free D1 write budget).
 * Per-EVSE would be ~180k rows and exceed ~100k writes/day.
 */
import { gunzipSync } from "node:zlib";
import { existsSync, readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { saveRawDump } from "../lib/cache.mjs";
import { locationsToStationPois } from "../lib/ocpiStatus.mjs";

const LOCATIONS_URL =
  "https://opendata.ndw.nu/charging_point_locations_ocpi.json.gz";

export const id = "dotnl";

const __dirname = dirname(fileURLToPath(import.meta.url));
const DUMP_PATH = join(__dirname, "../../../.cache/dumps/dotnl/locations.json");

async function fetchGunzipText(url) {
  const res = await fetch(url, {
    headers: { "user-agent": "gaston-backend-ingest/0.1", accept: "*/*" },
    redirect: "follow",
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`dotnl HTTP ${res.status}: ${body.slice(0, 200)}`);
  }
  const buf = Buffer.from(await res.arrayBuffer());
  if (buf.length >= 2 && buf[0] === 0x1f && buf[1] === 0x8b) {
    return gunzipSync(buf).toString("utf8");
  }
  return buf.toString("utf8");
}

export async function ingest() {
  let text;
  if (existsSync(DUMP_PATH)) {
    text = readFileSync(DUMP_PATH, "utf8");
    console.log(JSON.stringify({ event: "dotnl_cache_hit", path: DUMP_PATH }));
  } else {
    text = await fetchGunzipText(LOCATIONS_URL);
    saveRawDump(id, "locations.json", text);
  }

  const locations = JSON.parse(text);
  if (!Array.isArray(locations)) {
    throw new Error("dotnl: expected JSON array of locations");
  }

  const now = new Date().toISOString();
  const pois = locationsToStationPois(locations, {
    source: id,
    country: "NL",
    now,
  });

  return { table: "pois", source: id, rows: pois };
}
