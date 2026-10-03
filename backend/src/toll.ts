/**
 * French highway toll estimate from OpenTollData (mirrors :shared TollCalculator).
 * Closed networks: one entry→exit matrix lookup per consecutive run (not hop sum).
 */

export const TOLL_DATA_R2_KEY = "toll/open_toll_data.json";

export type OpenTollData = {
  networks?: OpenTollNetwork[];
  toll_description?: Record<string, TollBoothDescription>;
  open_toll_price?: Record<string, OpenTollPriceEntry>;
  date?: string;
  version?: string;
};

export type OpenTollNetwork = {
  network_name?: string;
  tolls?: string[];
  connection?: Record<string, Record<string, ConnectionPrice>>;
};

export type ConnectionPrice = {
  distance?: string;
  price?: Record<string, string>;
};

export type TollBoothDescription = {
  lat?: string;
  lon?: string;
  type?: string;
};

export type OpenTollPriceEntry = {
  distance?: string;
  price?: Record<string, string>;
};

export type TollSegment =
  | {
      type: "close";
      network: string;
      entry: string;
      exit: string;
      amount_eur: number;
    }
  | {
      type: "open";
      booth: string;
      amount_eur: number;
    };

export type TollEstimateResult = {
  amount_eur: number;
  currency: "EUR";
  segments: TollSegment[];
  data_version: string | null;
};

const BOOTH_PROXIMITY_M = 1500;
const PI_180 = Math.PI / 180;
const METERS_PER_DEGREE_LAT = 111_320;
const METERS_PER_DEGREE_LON = 85_000;

let cachedData: OpenTollData | null = null;
let cachedEtag: string | null = null;

export function clearTollCacheForTests(): void {
  cachedData = null;
  cachedEtag = null;
}

export function setTollDataForTests(data: OpenTollData | null): void {
  cachedData = data;
  cachedEtag = "test";
}

export async function loadTollData(env: Env): Promise<OpenTollData | null> {
  if (cachedData) return cachedData;
  const obj = await env.DUMPS.get(TOLL_DATA_R2_KEY);
  if (!obj) return null;
  const text = await obj.text();
  try {
    cachedData = JSON.parse(text) as OpenTollData;
    cachedEtag = obj.etag ?? null;
    return cachedData;
  } catch {
    return null;
  }
}

export function mergeOpenTollData(parts: OpenTollData[]): OpenTollData {
  const networks: OpenTollNetwork[] = [];
  const toll_description: Record<string, TollBoothDescription> = {};
  const open_toll_price: Record<string, OpenTollPriceEntry> = {};
  let date: string | undefined;
  let version: string | undefined;
  for (const part of parts) {
    if (part.networks) networks.push(...part.networks);
    if (part.toll_description) Object.assign(toll_description, part.toll_description);
    if (part.open_toll_price) Object.assign(open_toll_price, part.open_toll_price);
    if (part.date) date = part.date;
    if (part.version) version = part.version;
  }
  return { networks, toll_description, open_toll_price, date, version };
}

export function estimateToll(
  data: OpenTollData,
  points: Array<[number, number]>,
  vehicleClass: number,
): TollEstimateResult | null {
  if (points.length < 2) return null;
  if (![1, 2, 3, 4, 5].includes(vehicleClass)) return null;

  const desc = data.toll_description ?? {};
  const segmentLens = segmentLengths(points);
  const cum = cumulativeLengths(segmentLens);

  const boothsWithPos: Array<{ name: string; pos: number }> = [];
  for (const [name, booth] of Object.entries(desc)) {
    const lat = Number(booth.lat);
    const lon = Number(booth.lon);
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue;
    let minDist = Infinity;
    let positionAlongRoute = 0;
    for (let i = 0; i < points.length - 1; i++) {
      const [alat, alon] = points[i];
      const [blat, blon] = points[i + 1];
      const { dist, t } = distanceAndT(lat, lon, alat, alon, blat, blon);
      if (dist < minDist) {
        minDist = dist;
        positionAlongRoute = cum[i] + t * segmentLens[i];
      }
    }
    if (minDist <= BOOTH_PROXIMITY_M) {
      boothsWithPos.push({ name, pos: positionAlongRoute });
    }
  }
  boothsWithPos.sort((a, b) => a.pos - b.pos);
  if (boothsWithPos.length === 0) return null;

  const boothOrder = boothsWithPos.map((b) => b.name);
  const tollToNetwork = new Map<string, string>();
  for (const net of data.networks ?? []) {
    const netName = net.network_name ?? "";
    for (const t of net.tolls ?? []) {
      tollToNetwork.set(t, netName);
    }
  }

  const classKey = `class_${vehicleClass}`;
  const segments: TollSegment[] = [];
  let total = 0;
  let i = 0;
  while (i < boothOrder.length) {
    const name = boothOrder[i];
    const descEntry = desc[name];
    if (!descEntry) {
      i++;
      continue;
    }
    if (descEntry.type === "open") {
      const price = Number(data.open_toll_price?.[name]?.price?.[classKey]);
      if (Number.isFinite(price)) {
        total += price;
        segments.push({ type: "open", booth: name, amount_eur: price });
      }
      i++;
      continue;
    }

    const net = tollToNetwork.get(name);
    if (!net) {
      i++;
      continue;
    }
    const runStart = i;
    let runEnd = i;
    while (runEnd + 1 < boothOrder.length) {
      const nextName = boothOrder[runEnd + 1];
      const nextDesc = desc[nextName];
      if (!nextDesc || nextDesc.type === "open") break;
      if (tollToNetwork.get(nextName) !== net) break;
      runEnd++;
    }
    const entry = boothOrder[runStart];
    const exit = boothOrder[runEnd];
    if (entry !== exit) {
      const network = (data.networks ?? []).find((n) => n.network_name === net);
      const price = Number(network?.connection?.[entry]?.[exit]?.price?.[classKey]);
      if (Number.isFinite(price)) {
        total += price;
        segments.push({
          type: "close",
          network: net,
          entry,
          exit,
          amount_eur: price,
        });
      }
    }
    i = runEnd + 1;
  }

  if (total <= 0) return null;
  return {
    amount_eur: total,
    currency: "EUR",
    segments,
    data_version: data.version ?? data.date ?? null,
  };
}

export async function estimateTollFromEnv(
  env: Env,
  points: Array<[number, number]>,
  vehicleClass: number,
): Promise<
  | { ok: true; result: TollEstimateResult }
  | { ok: false; status: number; code: string; message: string }
> {
  const data = await loadTollData(env);
  if (!data) {
    return {
      ok: false,
      status: 503,
      code: "toll_data_missing",
      message: "Toll data not ingested — run open-toll-data ingest",
    };
  }
  const result = estimateToll(data, points, vehicleClass);
  if (!result) {
    return {
      ok: false,
      status: 404,
      code: "no_toll",
      message: "No toll booths matched the route",
    };
  }
  return { ok: true, result };
}

function segmentLengths(points: Array<[number, number]>): number[] {
  const len: number[] = [];
  for (let i = 0; i < points.length - 1; i++) {
    const [lat1, lon1] = points[i];
    const [lat2, lon2] = points[i + 1];
    len.push(haversineMeters(lat1, lon1, lat2, lon2));
  }
  return len;
}

function cumulativeLengths(segmentLengths: number[]): number[] {
  const cum = [0];
  for (const s of segmentLengths) cum.push(cum[cum.length - 1] + s);
  return cum;
}

function distanceAndT(
  plat: number,
  plon: number,
  latA: number,
  lonA: number,
  latB: number,
  lonB: number,
): { dist: number; t: number } {
  const ax = (lonA - plon) * Math.cos(plat * PI_180) * METERS_PER_DEGREE_LON;
  const ay = (latA - plat) * METERS_PER_DEGREE_LAT;
  const bx = (lonB - plon) * Math.cos(plat * PI_180) * METERS_PER_DEGREE_LON;
  const by = (latB - plat) * METERS_PER_DEGREE_LAT;
  const dx = bx - ax;
  const dy = by - ay;
  const lenSq = dx * dx + dy * dy;
  const tRaw = lenSq <= 1e-20 ? 0 : (-ax * dx + -ay * dy) / lenSq;
  const t = Math.min(1, Math.max(0, tRaw));
  const projX = ax + t * dx;
  const projY = ay + t * dy;
  return { dist: Math.hypot(projX, projY), t };
}

function haversineMeters(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number,
): number {
  const r = 6_371_000;
  const dLat = (lat2 - lat1) * PI_180;
  const dLon = (lon2 - lon1) * PI_180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * PI_180) *
      Math.cos(lat2 * PI_180) *
      Math.sin(dLon / 2) ** 2;
  return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0, 1 - a)));
}
