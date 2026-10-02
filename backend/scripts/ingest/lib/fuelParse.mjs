/**
 * Pure parsers for fuel-history ingest (unit-tested).
 */

const FUEL_NAME_TO_ID = {
  gazole: "gazole",
  "gazole (b7)": "gazole",
  sp95: "sp95",
  "sp95-e10": "sp95",
  e10: "sp95",
  sp98: "sp98",
  e85: "e85",
  "gp lc": "gplc",
  gpl: "gplc",
  gplc: "gplc",
};

export function fuelNameToId(name) {
  const key = String(name || "")
    .trim()
    .toLowerCase()
    .normalize("NFD")
    .replace(/\p{M}/gu, "");
  if (FUEL_NAME_TO_ID[key]) return FUEL_NAME_TO_ID[key];
  if (key.includes("gazole") || key.includes("gasole")) return "gazole";
  if (key.includes("sp98") || key.includes("98")) return "sp98";
  if (key.includes("e85")) return "e85";
  if (key.includes("gpl")) return "gplc";
  if (key.includes("sp95") || key.includes("e10") || key.includes("sans plomb 95"))
    return "sp95";
  return null;
}

export function isoDay(y, m, d) {
  return `${String(y).padStart(4, "0")}-${String(m).padStart(2, "0")}-${String(d).padStart(2, "0")}`;
}

export function parseStooqCsv(text, maxRows = 40) {
  const lines = text
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean);
  if (lines.length < 2) return [];
  const data = lines.slice(1).filter((l) => !l.startsWith("Date,"));
  const tail = data.slice(-maxRows);
  const out = [];
  for (const line of tail) {
    const parts = line.split(",");
    if (parts.length < 5) continue;
    const day = parts[0].trim();
    const close = Number(parts[4].trim());
    if (day && Number.isFinite(close) && close > 0) out.push({ day, close });
  }
  return out;
}

/** Yahoo chart API fallback when Stooq bot-challenge blocks CI / datacenter IPs. */
export function parseYahooChart(jsonText, maxRows = 40) {
  const root = JSON.parse(jsonText);
  const result = root?.chart?.result?.[0];
  if (!result) return [];
  const timestamps = result.timestamp || [];
  const closes = result.indicators?.quote?.[0]?.close || [];
  const out = [];
  for (let i = 0; i < timestamps.length; i++) {
    const close = Number(closes[i]);
    if (!Number.isFinite(close) || close <= 0) continue;
    const day = new Date(timestamps[i] * 1000).toISOString().slice(0, 10);
    out.push({ day, close });
  }
  return out.slice(-maxRows);
}

/**
 * Parse ODS national aggregate JSON into D1 rows.
 * Prefers year(prix_maj)/month/day keys (ODS returns null for bare aliases).
 */
export function parseNationalFrResults(root) {
  const rows = [];
  for (const item of root?.results || []) {
    const fuelId = fuelNameToId(item["prix_nom"]);
    if (!fuelId) continue;
    const y = Number(item["year(prix_maj)"] ?? item.y);
    const m = Number(item["month(prix_maj)"] ?? item.m);
    const d = Number(item["day(prix_maj)"] ?? item.d);
    const avg = Number(item.avg_eur ?? item["avg(prix_valeur)"]);
    if (![y, m, d, avg].every((n) => Number.isFinite(n) && n > 0)) continue;
    rows.push({
      country: "FR",
      fuel_id: fuelId,
      day: isoDay(y, m, d),
      avg_eur: avg,
    });
  }
  return rows;
}
