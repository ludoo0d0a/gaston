import {
  computeMarketScore,
  marketHorizonPredictions,
  projectNationalTrend,
  todayParis,
  type DailyAvg,
  type DailyClose,
} from "./fuelForecast";

type NationalRow = { day: string; avg_eur: number };
type MarketRow = { day: string; close: number };

export async function fuelHistory(
  env: Env,
  country: string,
  fuelId: string,
  fromDay: string | null,
): Promise<{
  country: string;
  fuel_id: string;
  history: Array<{ day: string; avg_eur: number }>;
}> {
  const params: unknown[] = [country.toUpperCase(), fuelId];
  let sql = `
    SELECT day, avg_eur FROM fuel_national_daily
    WHERE country = ? AND fuel_id = ?
  `;
  if (fromDay) {
    sql += ` AND day >= ?`;
    params.push(fromDay);
  }
  sql += ` ORDER BY day ASC LIMIT 400`;

  const result = await env.DB.prepare(sql)
    .bind(...params)
    .all<NationalRow>();

  return {
    country: country.toUpperCase(),
    fuel_id: fuelId,
    history: (result.results ?? []).map((r) => ({
      day: r.day,
      avg_eur: r.avg_eur,
    })),
  };
}

export async function fuelForecast(
  env: Env,
  country: string,
  fuelId: string,
): Promise<Record<string, unknown>> {
  const cc = country.toUpperCase();
  const today = todayParis();
  const fromDay = shiftDay(today, -30);

  const histResult = await env.DB.prepare(
    `SELECT day, avg_eur FROM fuel_national_daily
     WHERE country = ? AND fuel_id = ? AND day >= ?
     ORDER BY day ASC LIMIT 400`,
  )
    .bind(cc, fuelId, fromDay)
    .all<NationalRow>();

  const history: DailyAvg[] = (histResult.results ?? []).map((r) => ({
    day: r.day,
    priceEurPerL: r.avg_eur,
  }));

  const forecast = projectNationalTrend(history, today);
  const baseline = history.at(-1)?.priceEurPerL ?? null;

  const brent = await loadCloses(env, "brent.uk", fromDay);
  const ho = await loadCloses(env, "ho.f", fromDay);
  const fx = await loadCloses(env, "eurusd", fromDay);
  const { score, inputs } = computeMarketScore(
    brent,
    ho.length >= 4 ? ho : brent,
    fx,
  );

  const horizon =
    baseline != null && brent.length >= 4
      ? marketHorizonPredictions(fuelId, baseline, score)
      : [];

  const directionUp =
    forecast.length > 0 && baseline != null
      ? forecast[forecast.length - 1].priceEurPerL > baseline + 0.005
      : horizon[0]?.predictedUp ?? null;

  return {
    country: cc,
    fuel_id: fuelId,
    as_of: today,
    baseline_eur: baseline,
    history: history.map((h) => ({
      day: h.day,
      avg_eur: h.priceEurPerL,
      is_forecast: false,
    })),
    forecast: forecast.map((h) => ({
      day: h.day,
      avg_eur: h.priceEurPerL,
      is_forecast: true,
    })),
    market_score: brent.length >= 4 ? score : null,
    market_inputs: brent.length >= 4 ? inputs : null,
    market_horizons: horizon,
    direction_up: directionUp,
    note:
      history.length === 0
        ? "empty — run npm run ingest:fuel-history"
        : undefined,
  };
}

async function loadCloses(
  env: Env,
  symbol: string,
  fromDay: string,
): Promise<DailyClose[]> {
  const result = await env.DB.prepare(
    `SELECT day, close FROM fuel_market_daily
     WHERE symbol = ? AND day >= ?
     ORDER BY day ASC LIMIT 100`,
  )
    .bind(symbol, fromDay)
    .all<MarketRow>();
  return (result.results ?? []).map((r) => ({ day: r.day, close: r.close }));
}

function shiftDay(iso: string, delta: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d));
  dt.setUTCDate(dt.getUTCDate() + delta);
  const yy = dt.getUTCFullYear();
  const mm = String(dt.getUTCMonth() + 1).padStart(2, "0");
  const dd = String(dt.getUTCDate()).padStart(2, "0");
  return `${yy}-${mm}-${dd}`;
}
