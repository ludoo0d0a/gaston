import { col, headerIndex, parseCsv, parseCoordonneesXy } from "../lib/csv.mjs";
import { fetchText } from "../lib/fetch.mjs";
import { saveRawDump } from "../lib/cache.mjs";

const STATIC_URL =
  "https://proxy.transport.data.gouv.fr/resource/gireve-irve-statique?format=csv";
const DYNAMIC_URL =
  "https://proxy.transport.data.gouv.fr/resource/gireve-irve-dynamique";

export const id = "gireve";

export async function ingest() {
  const staticText = await fetchText(STATIC_URL, { label: "gireve-static" });
  const dynamicText = await fetchText(DYNAMIC_URL, { label: "gireve-dynamic" });
  saveRawDump(id, "static.csv", staticText);
  saveRawDump(id, "dynamic.csv", dynamicText);

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
      id: `gireve:${pdc}`,
      source: id,
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
        amenageur: col(row, sIdx, "nom_amenageur"),
        enseigne: col(row, sIdx, "nom_enseigne"),
        address: col(row, sIdx, "adresse_station"),
        puissance:
          col(row, sIdx, "puissance_nominale") ??
          col(row, sIdx, "puissance_maximale"),
      },
    });
  }

  return { table: "pois", source: id, rows: pois };
}
