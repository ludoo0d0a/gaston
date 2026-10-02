/**
 * Port of NationalFuelTrendPredictor + RuleBasedFuelPricePredictor (Kotlin).
 */

export type DailyAvg = { day: string; priceEurPerL: number };
export type DailyClose = { day: string; close: number };

export function projectNationalTrend(
  history: DailyAvg[],
  anchorDay: string,
  minHistoryDays = 3,
  projectionDays = 5,
): DailyAvg[] {
  const sorted = [...history].sort((a, b) => a.day.localeCompare(b.day));
  if (sorted.length < minHistoryDays) return [];

  const n = sorted.length;
  const xs = Array.from({ length: n }, (_, i) => i);
  const ys = sorted.map((p) => p.priceEurPerL);
  const sumX = xs.reduce((a, b) => a + b, 0);
  const sumY = ys.reduce((a, b) => a + b, 0);
  const sumXy = xs.reduce((a, i) => a + i * ys[i], 0);
  const sumX2 = xs.reduce((a, x) => a + x * x, 0);
  const denom = n * sumX2 - sumX * sumX;
  const slope = denom === 0 ? 0 : (n * sumXy - sumX * sumY) / denom;
  const intercept = (sumY - slope * sumX) / n;

  const anchor = parseIsoDay(anchorDay);
  if (!anchor) return [];

  const out: DailyAvg[] = [];
  for (let horizon = 1; horizon <= projectionDays; horizon++) {
    const target = addDays(anchor, horizon);
    const x = n - 1 + horizon;
    const price = Math.max(0.05, intercept + slope * x);
    out.push({ day: formatIsoDay(target), priceEurPerL: price });
  }
  return out;
}

function logReturn(series: DailyClose[], lagDays: number): number | null {
  if (series.length <= lagDays) return null;
  const a = series[series.length - 1].close;
  const b = series[series.length - 1 - lagDays].close;
  if (a <= 0 || b <= 0) return null;
  return Math.log(a / b);
}

export function computeMarketScore(
  brent: DailyClose[],
  heatingOil: DailyClose[],
  eurusd: DailyClose[],
): { score: number; inputs: Record<string, number | null> } {
  const returnBrent3d = logReturn(brent, 3);
  const returnHeatingOil3d = logReturn(heatingOil, 3) ?? returnBrent3d;
  const returnEurusd1d = logReturn(eurusd, 1);
  const rB = returnBrent3d ?? 0;
  const rHo = returnHeatingOil3d ?? 0;
  const rFx = returnEurusd1d ?? 0;
  const score = 0.5 * rB + 0.4 * rHo - 0.2 * rFx;
  return {
    score,
    inputs: {
      returnBrent3d,
      returnHeatingOil3d,
      returnEurusd1d,
    },
  };
}

function passThrough(fuelId: string): number {
  const id = fuelId.toLowerCase();
  if (id.includes("gazole") || id.includes("diesel")) return 0.45;
  return 0.38;
}

export function marketHorizonPredictions(
  fuelId: string,
  baselinePriceEurPerL: number,
  score: number,
  upThreshold = 0.002,
  horizonDecay = 0.85,
): Array<{
  horizonDays: number;
  predictedUp: boolean;
  predictedPriceEurPerL: number;
}> {
  const k = passThrough(fuelId);
  return [1, 2, 3].map((h) => {
    const decay = Math.pow(horizonDecay, h);
    const predictedPrice = baselinePriceEurPerL + k * score * decay;
    return {
      horizonDays: h,
      predictedUp: score > upThreshold,
      predictedPriceEurPerL: Math.max(0.05, predictedPrice),
    };
  });
}

function parseIsoDay(iso: string): Date | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  if (!m) return null;
  return new Date(Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3])));
}

function formatIsoDay(d: Date): string {
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, "0");
  const day = String(d.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function addDays(d: Date, n: number): Date {
  const x = new Date(d.getTime());
  x.setUTCDate(x.getUTCDate() + n);
  return x;
}

export function todayParis(): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/Paris",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
}
