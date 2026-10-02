const DEFAULT_UA = "gaston-backend-ingest/0.1 (+https://github.com/geoking)";

export async function fetchText(url, { label = url, timeoutMs = 120_000 } = {}) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), timeoutMs);
  try {
    const res = await fetch(url, {
      signal: ctrl.signal,
      headers: { "user-agent": DEFAULT_UA, accept: "*/*" },
      redirect: "follow",
    });
    const body = await res.text();
    if (!res.ok) {
      throw new Error(`${label} HTTP ${res.status}: ${body.slice(0, 200)}`);
    }
    return body;
  } finally {
    clearTimeout(t);
  }
}

export async function fetchJson(url, opts) {
  const text = await fetchText(url, opts);
  return JSON.parse(text);
}
