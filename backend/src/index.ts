import { jsonError, requireApiKey } from "./auth";
import { fuelForecast, fuelHistory } from "./fuel";
import { parseSearchParams } from "./geo";
import { searchPois } from "./pois";
import { estimateTollFromEnv } from "./toll";
import { searchZones } from "./zones";

export default {
  async fetch(
    request: Request,
    env: Env,
    _ctx: ExecutionContext,
  ): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }

    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "gaston-api", phase: "we-fuel-toll" });
    }

    const authError = requireApiKey(request, env);
    if (authError) {
      return withCors(authError);
    }

    if (request.method === "GET" && url.pathname === "/v1/pois") {
      const parsed = parseSearchParams(url);
      if ("error" in parsed) {
        return withCors(jsonError(400, "bad_request", parsed.error));
      }
      const body = await searchPois(
        env,
        parsed.lat,
        parsed.lon,
        parsed.radiusKm,
        parsed.source,
        parsed.limit,
      );
      return geoJson(body);
    }

    if (request.method === "GET" && url.pathname === "/v1/zones") {
      const parsed = parseSearchParams(url);
      if ("error" in parsed) {
        return withCors(jsonError(400, "bad_request", parsed.error));
      }
      const body = await searchZones(
        env,
        parsed.lat,
        parsed.lon,
        parsed.radiusKm,
        parsed.source,
        parsed.limit,
      );
      return geoJson(body);
    }

    if (request.method === "GET" && url.pathname === "/v1/fuel/history") {
      const country = (url.searchParams.get("country") || "FR").toUpperCase();
      const fuel = url.searchParams.get("fuel") || "gazole";
      const from = url.searchParams.get("from");
      if (!/^[A-Z]{2}$/.test(country)) {
        return withCors(jsonError(400, "bad_request", "country must be ISO-2"));
      }
      const body = await fuelHistory(env, country, fuel, from);
      return json(body);
    }

    if (request.method === "GET" && url.pathname === "/v1/fuel/forecast") {
      const country = (url.searchParams.get("country") || "FR").toUpperCase();
      const fuel = url.searchParams.get("fuel") || "gazole";
      if (!/^[A-Z]{2}$/.test(country)) {
        return withCors(jsonError(400, "bad_request", "country must be ISO-2"));
      }
      const body = await fuelForecast(env, country, fuel);
      return json(body);
    }

    if (request.method === "POST" && url.pathname === "/v1/toll/estimate") {
      let body: unknown;
      try {
        body = await request.json();
      } catch {
        return withCors(jsonError(400, "bad_request", "invalid JSON body"));
      }
      const obj = body as {
        points?: unknown;
        vehicle_class?: unknown;
      };
      const pointsRaw = obj.points;
      const vehicleClass = Number(obj.vehicle_class ?? 1);
      if (!Array.isArray(pointsRaw) || pointsRaw.length < 2) {
        return withCors(
          jsonError(400, "bad_request", "points must be [[lat,lon], ...] with ≥2"),
        );
      }
      const points: Array<[number, number]> = [];
      for (const p of pointsRaw) {
        if (!Array.isArray(p) || p.length < 2) {
          return withCors(jsonError(400, "bad_request", "each point must be [lat,lon]"));
        }
        const lat = Number(p[0]);
        const lon = Number(p[1]);
        if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
          return withCors(jsonError(400, "bad_request", "lat/lon must be numbers"));
        }
        points.push([lat, lon]);
      }
      if (![1, 2, 3, 4, 5].includes(vehicleClass)) {
        return withCors(
          jsonError(400, "bad_request", "vehicle_class must be 1–5"),
        );
      }
      const estimated = await estimateTollFromEnv(env, points, vehicleClass);
      if (!estimated.ok) {
        return withCors(
          jsonError(estimated.status, estimated.code, estimated.message),
        );
      }
      return json(estimated.result);
    }

    return withCors(
      jsonError(404, "not_found", `No route for ${url.pathname}`),
    );
  },
} satisfies ExportedHandler<Env>;

function json(data: unknown, status = 200): Response {
  return withCors(
    new Response(JSON.stringify(data), {
      status,
      headers: {
        "content-type": "application/json; charset=utf-8",
        "cache-control": status === 200 ? "public, max-age=60" : "no-store",
      },
    }),
  );
}

function geoJson(data: unknown): Response {
  return withCors(
    new Response(JSON.stringify(data), {
      status: 200,
      headers: {
        "content-type": "application/geo+json; charset=utf-8",
        "cache-control": "public, max-age=60",
      },
    }),
  );
}

function corsHeaders(): HeadersInit {
  return {
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET, POST, OPTIONS",
    "access-control-allow-headers": "Authorization, Content-Type",
  };
}

function withCors(response: Response): Response {
  const headers = new Headers(response.headers);
  for (const [k, v] of Object.entries(corsHeaders())) {
    headers.set(k, v);
  }
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers,
  });
}
