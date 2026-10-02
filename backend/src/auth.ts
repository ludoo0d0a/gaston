/**
 * Bearer token check. Missing or wrong key → 401.
 * Configure APP_API_KEY via wrangler secret (prod) or backend/.dev.vars (local).
 */
export function requireApiKey(request: Request, env: Env): Response | null {
  const expected = env.APP_API_KEY;
  if (!expected) {
    return jsonError(500, "server_misconfigured", "APP_API_KEY is not set");
  }
  const header = request.headers.get("Authorization") ?? "";
  const match = /^Bearer\s+(.+)$/i.exec(header);
  if (!match || match[1] !== expected) {
    return jsonError(401, "unauthorized", "Missing or invalid Bearer token");
  }
  return null;
}

export function jsonError(
  status: number,
  code: string,
  message: string,
): Response {
  return new Response(JSON.stringify({ error: { code, message } }), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
    },
  });
}
