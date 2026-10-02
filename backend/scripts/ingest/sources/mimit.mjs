import { col, headerIndex, parseCsvLine } from "../lib/csv.mjs";
import { fetchText } from "../lib/fetch.mjs";
import { saveRawDump } from "../lib/cache.mjs";

const STATIONS_URL =
  "https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv";
const PRICES_URL =
  "https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv";

export const id = "mimit";

function parseMimitCsv(text) {
  const lines = text.split(/\r?\n/).filter((l) => l.trim().length > 0);
  if (lines.length < 2) return { header: [], rows: [] };
  // Skip "Estrazione del ..." first line; header is second.
  const header = parseCsvLine(lines[1], "|").map((h) => h.trim());
  const rows = [];
  for (let i = 2; i < lines.length; i++) {
    rows.push(parseCsvLine(lines[i], "|"));
  }
  return { header, rows };
}

export async function ingest() {
  const stationsText = await fetchText(STATIONS_URL, {
    label: "mimit-stations",
    timeoutMs: 180_000,
  });
  const pricesText = await fetchText(PRICES_URL, {
    label: "mimit-prices",
    timeoutMs: 180_000,
  });
  saveRawDump(id, "anagrafica.csv", stationsText);
  saveRawDump(id, "prezzi.csv", pricesText);

  const stations = parseMimitCsv(stationsText);
  const prices = parseMimitCsv(pricesText);
  const sIdx = headerIndex(stations.header);
  const pIdx = headerIndex(prices.header);

  const pricesById = new Map();
  for (const row of prices.rows) {
    const sid = col(row, pIdx, "idImpianto");
    const fuel = col(row, pIdx, "descCarburante");
    const price = Number(String(col(row, pIdx, "prezzo") || "").replace(",", "."));
    if (!sid || !fuel || !Number.isFinite(price)) continue;
    const list = pricesById.get(sid) || [];
    list.push({ fuel, price, updated: col(row, pIdx, "dtComu") });
    pricesById.set(sid, list);
  }

  const now = new Date().toISOString();
  const pois = [];
  for (const row of stations.rows) {
    const sid = col(row, sIdx, "idImpianto");
    if (!sid) continue;
    const lat = Number(String(col(row, sIdx, "Latitudine") || "").replace(",", "."));
    const lon = Number(String(col(row, sIdx, "Longitudine") || "").replace(",", "."));
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue;
    pois.push({
      id: `mimit:${sid}`,
      source: id,
      category: "gas",
      lat,
      lon,
      name: col(row, sIdx, "Nome Impianto") || col(row, sIdx, "Bandiera") || "Fuel station",
      station_id: sid,
      etat: null,
      occupation: null,
      updated_at: now,
      dynamic_updated_at: null,
      props: {
        brand: col(row, sIdx, "Bandiera"),
        address: col(row, sIdx, "Indirizzo"),
        city: col(row, sIdx, "Comune"),
        province: col(row, sIdx, "Provincia"),
        prices: pricesById.get(sid) || [],
      },
    });
  }

  return { table: "pois", source: id, rows: pois };
}
