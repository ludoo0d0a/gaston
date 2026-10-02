import { fetchText } from "../lib/fetch.mjs";
import { saveRawDump } from "../lib/cache.mjs";

const GEOJSON_URL = "https://data.geoportail.lu/radar";

export const id = "luxembourg-radars";

export async function ingest() {
  const text = await fetchText(GEOJSON_URL, { label: "luxembourg-radars" });
  saveRawDump(id, "radars.geojson", text);

  const root = JSON.parse(text);
  const features = Array.isArray(root.features) ? root.features : [];
  const now = new Date().toISOString();
  const zones = [];

  features.forEach((feature, idx) => {
    const coords = feature?.geometry?.coordinates;
    if (!Array.isArray(coords) || coords.length < 2) return;
    const lon = Number(coords[0]);
    const lat = Number(coords[1]);
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) return;
    const props = feature.properties || {};
    const rid = String(props.ID ?? props.OBJECTID_1 ?? `lu_${idx}`);
    zones.push({
      id: `lu_radar_${rid}`,
      source: id,
      kind: "speed_control",
      lat,
      lon,
      radius_m: null,
      vma: null,
      updated_at: now,
      props: {
        tranche: props.TRANCON ?? null,
        dir: props.DIR ?? null,
        dir_opposite: props.DIR_ ?? null,
        year: props.YEAR ?? null,
        aac_zone: true,
      },
    });
  });

  return { table: "zones", source: id, rows: zones };
}
