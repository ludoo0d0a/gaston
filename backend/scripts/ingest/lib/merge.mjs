/**
 * Server-side POI merge — mirrors shared PoiMerger thresholds:
 * - ≤50 m unconditional
 * - ≤300 m + same brand (skipped for IRVE↔IRVE)
 * - ≤300 m + complementary gas (prices vs brand/name)
 * - ≤300 m + name token Jaccard ≥ 0.8
 */

const MERGE_DISTANCE_METERS = 50;
const MERGE_DISTANCE_WITH_NAME_METERS = 300;
const MERGE_DISTANCE_WITH_BRAND_METERS = 300;
const NAME_SIMILARITY_MIN = 0.8;

export function haversineMeters(lat1, lon1, lat2, lon2) {
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * 6_371_000 * Math.asin(Math.min(1, Math.sqrt(a)));
}

function foldName(s) {
  return String(s || "")
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

function nameTokens(name) {
  return foldName(name)
    .split(" ")
    .filter((t) => t.length >= 2);
}

function namesSimilarEnough(a, b) {
  const ta = new Set(nameTokens(a.name));
  const tb = new Set(nameTokens(b.name));
  if (ta.size === 0 || tb.size === 0) return false;
  let inter = 0;
  for (const t of ta) if (tb.has(t)) inter++;
  const union = ta.size + tb.size - inter;
  return union > 0 && inter / union >= NAME_SIMILARITY_MIN;
}

function brandKey(poi) {
  const raw = foldName(poi.props?.brand || poi.props?.enseigne || poi.name || "");
  if (!raw) return null;
  // Light brand hints (subset of BrandRegistry — enough for cross-source gas).
  const hints = [
    "total",
    "esso",
    "shell",
    "bp",
    "eni",
    "agip",
    "q8",
    "repsol",
    "cepsa",
    "galp",
    "omv",
    "aral",
    "avia",
    "leclerc",
    "carrefour",
    "intermarche",
    "auchan",
  ];
  for (const h of hints) {
    if (raw.includes(h)) return h;
  }
  return null;
}

function isIrve(poi) {
  return poi.category === "irve";
}

function isGas(poi) {
  return poi.category === "gas";
}

function hasPrices(poi) {
  const p = poi.props?.prices;
  if (!p) return false;
  if (Array.isArray(p)) return p.length > 0;
  return Object.keys(p).length > 0;
}

function isComplementaryGasPair(a, b) {
  if (!isGas(a) || !isGas(b)) return false;
  const aPrices = hasPrices(a);
  const bPrices = hasPrices(b);
  if (aPrices === bPrices) return false;
  const named = (p) => !!(brandKey(p) || (p.name && p.name.length > 3));
  return (aPrices && named(b)) || (bPrices && named(a));
}

export function isSamePoi(a, b) {
  if (a.id === b.id) return true;
  if (a.station_id && b.station_id && a.station_id === b.station_id) {
    // Same itinerance / plant id across feeds
    if (a.category === b.category) return true;
  }

  const latDelta = Math.abs(a.lat - b.lat) * 111_000;
  if (latDelta > MERGE_DISTANCE_WITH_NAME_METERS * 1.2) return false;
  const lonDelta =
    Math.abs(a.lon - b.lon) *
    111_000 *
    Math.cos(((a.lat + b.lat) / 2) * (Math.PI / 180));
  if (lonDelta > MERGE_DISTANCE_WITH_NAME_METERS * 1.2) return false;

  const dist = haversineMeters(a.lat, a.lon, b.lat, b.lon);
  if (dist <= MERGE_DISTANCE_METERS) return true;
  if (
    dist > MERGE_DISTANCE_WITH_NAME_METERS &&
    dist > MERGE_DISTANCE_WITH_BRAND_METERS
  ) {
    return false;
  }

  if (dist <= MERGE_DISTANCE_WITH_BRAND_METERS && !(isIrve(a) && isIrve(b))) {
    const ba = brandKey(a);
    const bb = brandKey(b);
    if (ba && ba === bb) return true;
  }

  if (dist <= MERGE_DISTANCE_WITH_NAME_METERS && isComplementaryGasPair(a, b)) {
    return true;
  }

  if (dist <= MERGE_DISTANCE_WITH_NAME_METERS) {
    return namesSimilarEnough(a, b);
  }
  return false;
}

function mergeProps(a, b) {
  const pa = a.props || {};
  const pb = b.props || {};
  const pricesA = pa.prices;
  const pricesB = pb.prices;
  let prices = pricesA || pricesB;
  if (pricesA && pricesB) {
    if (Array.isArray(pricesA) || Array.isArray(pricesB)) {
      prices = [...(Array.isArray(pricesA) ? pricesA : []), ...(Array.isArray(pricesB) ? pricesB : [])];
    } else {
      prices = { ...pricesA, ...pricesB };
    }
  }
  return {
    ...pb,
    ...pa,
    prices,
    merged_from: [
      ...(pa.merged_from || [a.source]),
      ...(pb.merged_from || [b.source]),
    ].filter((v, i, arr) => arr.indexOf(v) === i),
  };
}

export function mergeTwo(a, b) {
  // Prefer row with richer dynamic / name
  const preferA =
    (a.etat ? 1 : 0) + (a.name ? 1 : 0) + (hasPrices(a) ? 2 : 0) >=
    (b.etat ? 1 : 0) + (b.name ? 1 : 0) + (hasPrices(b) ? 2 : 0);
  const primary = preferA ? a : b;
  const secondary = preferA ? b : a;
  return {
    ...primary,
    id: `merged:${primary.id.replace(/^(gireve|qualicharge|minetur|mimit):/, "")}:${secondary.source}`,
    source: "merged",
    name: primary.name || secondary.name,
    station_id: primary.station_id || secondary.station_id,
    etat: primary.etat || secondary.etat,
    occupation: primary.occupation || secondary.occupation,
    dynamic_updated_at:
      primary.dynamic_updated_at || secondary.dynamic_updated_at,
    props: mergeProps(primary, secondary),
  };
}

/**
 * O(n²) merge — OK for country slices when filtered; for full FR IRVE prefer
 * spatial bucketing (geohash) below.
 */
export function mergePois(pois) {
  if (pois.length <= 1) return pois.slice();
  const ordered = [...pois].sort((a, b) => String(a.id).localeCompare(String(b.id)));
  const merged = ordered.slice();
  let i = 0;
  while (i < merged.length) {
    let j = i + 1;
    while (j < merged.length) {
      if (isSamePoi(merged[i], merged[j])) {
        merged[i] = mergeTwo(merged[i], merged[j]);
        merged.splice(j, 1);
        continue;
      }
      j++;
    }
    i++;
  }
  return merged;
}

/** Geohash-ish grid bucket to keep merge near-linear on large IRVE sets. */
export function mergePoisGrid(pois, cellDeg = 0.02) {
  const buckets = new Map();
  for (const p of pois) {
    const key = `${Math.floor(p.lat / cellDeg)}_${Math.floor(p.lon / cellDeg)}`;
    if (!buckets.has(key)) buckets.set(key, []);
    buckets.get(key).push(p);
  }
  // Merge within each cell and with 8-neighbors via second pass on cell results
  const cellMerged = [];
  for (const list of buckets.values()) {
    cellMerged.push(...mergePois(list));
  }
  return mergePois(cellMerged);
}

/** Radar: primary sources win within 40 m (RadarPoiMerger). */
const RADAR_DEDUP_M = 40;
const RADAR_PRIMARY = new Set(["france-radars", "luxembourg-radars", "lufop"]);

export function dedupeRadarZones(zones) {
  const primary = zones.filter((z) => RADAR_PRIMARY.has(z.source));
  const secondary = zones.filter((z) => !RADAR_PRIMARY.has(z.source));
  if (primary.length === 0 || secondary.length === 0) return zones;
  const drop = new Set();
  for (const s of secondary) {
    const near = primary.some(
      (p) => haversineMeters(s.lat, s.lon, p.lat, p.lon) <= RADAR_DEDUP_M,
    );
    if (near) drop.add(s.id);
  }
  return zones.filter((z) => !drop.has(z.id));
}
