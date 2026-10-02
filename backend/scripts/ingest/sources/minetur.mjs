import { fetchText } from "../lib/fetch.mjs";
import { saveRawDump } from "../lib/cache.mjs";

const URL =
  "https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/";

export const id = "minetur";

export async function ingest() {
  const text = await fetchText(URL, { label: "minetur", timeoutMs: 180_000 });
  saveRawDump(id, "estaciones.json", text);

  const root = JSON.parse(text);
  const list = root.ListaEESSPrecio || [];
  const now = new Date().toISOString();
  const pois = [];

  for (const s of list) {
    const ideess = s.IDEESS;
    if (!ideess) continue;
    const lat = Number(String(s.Latitud || "").replace(",", "."));
    const lon = Number(String(s["Longitud (WGS84)"] || "").replace(",", "."));
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue;

    const prices = {};
    const parsePrice = (raw) => {
      const s = String(raw ?? "").trim();
      if (!s) return null;
      const n = Number(s.replace(",", "."));
      return Number.isFinite(n) ? n : null;
    };
    const p95 = parsePrice(s["Precio Gasolina 95 E5"]);
    const diesel = parsePrice(s["Precio Gasóleo A"]);
    const p98 = parsePrice(s["Precio Gasolina 98 E5"]);
    if (p95 != null) prices.sp95 = p95;
    if (diesel != null) prices.diesel = diesel;
    if (p98 != null) prices.sp98 = p98;

    pois.push({
      id: `minetur:${ideess}`,
      source: id,
      category: "gas",
      lat,
      lon,
      name: s.Rotulo || "Gas Station",
      station_id: String(ideess),
      etat: null,
      occupation: null,
      updated_at: now,
      dynamic_updated_at: null,
      props: {
        address: s.Direccion || null,
        prices,
      },
    });
  }

  return { table: "pois", source: id, rows: pois };
}
