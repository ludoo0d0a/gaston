# Gaston backend — Cloudflare Workers Free API

Isolated from the Android/KMP app: **no Gradle module**, no dependency on `:androidApp` / `:shared`.

Plan: [`docs/plans/cloudflare-backend-geo-api.md`](../docs/plans/cloudflare-backend-geo-api.md)

## Stack (cost ~$0)

| Piece | Role |
|-------|------|
| Workers Free | `GET /health`, `GET /v1/pois`, `GET /v1/zones` |
| D1 Free | Indexed `pois` + `zones` |
| R2 | Raw dump archives (optional upload on `--remote`) |
| GitHub Actions | CSV/JSON ingest (not Cron Triggers) |

## Prerequisites

1. Node 20+ (on Apple Silicon prefer `mise exec node@22`)
2. Cloudflare account (Free)
3. `cd backend && npm install`
4. `npx wrangler login`

## One-time Cloudflare resources

```bash
cd backend
npx wrangler d1 create gaston-pois
# Paste database_id into wrangler.jsonc

npx wrangler r2 bucket create gaston-dumps
cp .dev.vars.example .dev.vars
npx wrangler secret put APP_API_KEY   # production
npm run db:local    # or db:remote
```

## Local API

```bash
mise exec node@22 -- npm run dev
curl -s http://127.0.0.1:8787/health
curl -s -H "Authorization: Bearer dev-local-secret-change-me" \
  "http://127.0.0.1:8787/v1/pois?lat=48.85&lon=2.35&radius_km=15&source=gireve"
```

## Phase 1 — ingest dumps

Runs **outside** the Worker (parse CSV in Node / GHA).

```bash
# Small smoke test
npm run ingest:lu

# France radars CSV
npm run ingest:fr-radars

# Large IRVE (watch D1 Free write budget ~100k/day)
npm run ingest:gireve

# Others
npm run ingest -- --source=qualicharge --local
npm run ingest -- --source=minetur --local
npm run ingest -- --source=mimit --local
```

Raw files land in `backend/.cache/dumps/` (gitignored). Prefer **one source per day** on Free.

| Source | Table | Notes |
|--------|-------|--------|
| `gireve` | pois | FR IRVE static+dynamic CSV |
| `qualicharge` | pois | FR IRVE static+dynamic CSV |
| `france-radars` | zones | data.gouv CSV (latest resource) |
| `luxembourg-radars` | zones | GeoJSON (~39) |
| `minetur` | pois | ES fuel national JSON |
| `mimit` | pois | IT stations+prices pipe CSV |

GHA: workflow **Backend ingest** (`workflow_dispatch` + daily LU). Secrets: `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`.

## Free-plan guards

- No CSV parse on the Worker request path
- D1 ≤ ~100k writes/day
- Mobile app unchanged until a later opt-in client
