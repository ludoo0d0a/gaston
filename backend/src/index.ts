import { jsonError, requireApiKey } from "./auth";
import { parseSearchParams } from "./geo";
import { searchPois } from "./pois";
import { searchZones } from "./zones";

export default {
  async fetch(request: Request, env: Env, _ctx: ExecutionContext): Promise<Response> {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }

    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "gaston-api", phase: 0 });
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

    return withCors(jsonError(404, "not_found", `No route for ${url.pathname}`));
  },
} satisfies ExportedHandler<Env>;

function json(data: unknown, status = 200): Response {
  return withCors(
    new Response(JSON.stringify(data), {
      status,
      headers: {
        "content-type": "application/json; charset=utf-8",
        "cache-control": "no-store",
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
        // Short cache OK once data exists; empty results still cheap.
        "cache-control": "public, max-age=60",
      },
    }),
  );
}

function corsHeaders(): HeadersInit {
  return {
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET, OPTIONS",
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
