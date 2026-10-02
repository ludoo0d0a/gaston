# Gaston backend — Cloudflare Workers Free API (Phase 0 scaffold)

Isolated from the Android/KMP app: **no Gradle module**, no dependency on `:androidApp` / `:shared`.

Plan: [`docs/plans/cloudflare-backend-geo-api.md`](../docs/plans/cloudflare-backend-geo-api.md)

## Stack (cost ~$0)

| Piece | Role |
|-------|------|
| Workers Free | `GET /health`, `GET /v1/pois`, `GET /v1/zones` |
| D1 Free | Indexed `pois` + `zones` |
| R2 | Raw dump archives (bindings ready; unused until ingest) |
| GitHub Actions | Future CSV ingest (not Cron Triggers — Free CPU too low) |

## Prerequisites

1. Node 20+
2. Cloudflare account (Free)
3. `cd backend && npm install`
4. `npx wrangler login`

## One-time Cloudflare resources

```bash
cd backend
npx wrangler d1 create gaston-pois
# Paste the returned database_id into wrangler.jsonc → d1_databases[0].database_id

npx wrangler r2 bucket create gaston-dumps

cp .dev.vars.example .dev.vars   # local only
# Production:
npx wrangler secret put APP_API_KEY
```

Apply schema:

```bash
npm run db:local    # for wrangler dev
npm run db:remote   # after database_id is set
```

## Node for backend/

Prefer an arm64 Node (e.g. `mise exec node@22 -- …`) on Apple Silicon. The repo may have an x86_64 nvm Node that breaks `workerd`.

```bash
cd backend
mise exec node@22 -- npm install
mise exec node@22 -- npm run dev
```

```bash
curl -s http://127.0.0.1:8787/health

curl -s -H "Authorization: Bearer dev-local-secret-change-me" \
  "http://127.0.0.1:8787/v1/pois?lat=48.85&lon=2.35&radius_km=15"

curl -s -H "Authorization: Bearer dev-local-secret-change-me" \
  "http://127.0.0.1:8787/v1/zones?lat=48.85&lon=2.35&radius_km=25"
```

Empty `FeatureCollection` until Phase 1 ingest populates D1.

## Deploy (still Free)

```bash
npm run deploy
```

Do **not** add Cron Triggers on the Free plan for ingest.

## Layout

```
backend/
  wrangler.jsonc
  src/                 # Worker (auth + GeoJSON query)
  migrations/          # D1 SQL
  scripts/ingest/      # Node ingest (GHA) — stub only in Phase 0
  README.md
```

## Free-plan guards

- No CSV parse on the Worker request path
- D1 ≤ ~100k writes/day → full national reloads at most 1–2×/day for large dumps
- Mobile app unchanged until a later opt-in client
