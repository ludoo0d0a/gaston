import { fetchText } from "../lib/fetch.mjs";
import { saveRawDump } from "../lib/cache.mjs";
import { locationsToEvsePois } from "../lib/ocpiStatus.mjs";

const LOCATIONS_URL =
  "https://roaming.road.io/files/9ef09c78-2666-418a-aa45-4f2261e2e305/locations.json?force=true";

export const id = "belgium-nap";

export async function ingest() {
  const text = await fetchText(LOCATIONS_URL, {
    label: "belgium-nap",
    timeoutMs: 180_000,
  });
  saveRawDump(id, "locations.json", text);

  const locations = JSON.parse(text);
  if (!Array.isArray(locations)) {
    throw new Error("belgium-nap: expected JSON array of locations");
  }

  const now = new Date().toISOString();
  const pois = locationsToEvsePois(locations, {
    source: id,
    country: "BE",
    now,
  });

  return { table: "pois", source: id, rows: pois };
}
