# Gaston backend — Cloudflare Workers Free API

Isolated from the Android/KMP app: **no Gradle module**, no dependency on `:androidApp` / `:shared`.

Plan: [`docs/plans/cloudflare-backend-geo-api.md`](../docs/plans/cloudflare-backend-geo-api.md)

## Stack (cost ~$0)

| Piece | Role |
|-------|------|
| Workers Free | GeoJSON POI/zones + fuel history/forecast |
| D1 Free | `pois`, `zones`, `fuel_national_daily`, `fuel_market_daily` |
| R2 | Raw dump archives (`gaston-dumps`) |
| GitHub Actions | Ingest (not Cron Triggers) |

**Account ID:** `04efa4000e37d6c1df300af5da041485`  
**D1:** `gaston-pois` (`c7cab17b-418e-4f3a-af93-20940801a192`)  
**R2:** `gaston-dumps`

## Prerequisites

```bash
cd backend
mise exec node@22 -- npm install   # Apple Silicon: use arm64 Node
npx wrangler login
cp .dev.vars.example .dev.vars
```

## Deploy (Free)

```bash
# APP_API_KEY already set remotely after first deploy; rotate with:
# echo -n 'your-secret' | npx wrangler secret put APP_API_KEY

npm run db:remote
npm run deploy
```

```bash
npm test    # unit tests (node:test)
npm run check
```

GitHub Actions secrets (repo Settings → Secrets):

- `CLOUDFLARE_API_TOKEN` — Account token: **Edit Cloudflare Workers** + **D1 Edit** + R2 read/write
- `CLOUDFLARE_ACCOUNT_ID` — `04efa4000e37d6c1df300af5da041485` ✅ set

```bash
# One-time: create token in dashboard, copy it, then:
pbpaste | ./scripts/ops/set-gha-cf-token.sh
./scripts/ops/smoke-remote.sh
gh workflow run "Backend ingest" -f source=fuel-history -f target=remote
```

Dashboard tokens: https://dash.cloudflare.com/?to=/:account/api-tokens

## API

```bash
# Local
mise exec node@22 -- npm run dev
curl -s http://127.0.0.1:8787/health

AUTH="Authorization: Bearer $(grep APP_API_KEY .dev.vars | cut -d= -f2)"

curl -s -H "$AUTH" \
  "http://127.0.0.1:8787/v1/pois?lat=50.85&lon=4.35&radius_km=5&source=belgium-nap&limit=5"

curl -s -H "$AUTH" \
  "http://127.0.0.1:8787/v1/fuel/history?country=FR&fuel=gazole&from=2026-09-01"

curl -s -H "$AUTH" \
  "http://127.0.0.1:8787/v1/fuel/forecast?country=FR&fuel=gazole"
```

| Route | Auth | Notes |
|-------|------|--------|
| `GET /health` | no | |
| `GET /v1/pois?lat&lon&radius_km&source&limit` | Bearer | GeoJSON |
| `GET /v1/zones?...` | Bearer | GeoJSON radars |
| `GET /v1/fuel/history?country&fuel&from` | Bearer | National daily averages |
| `GET /v1/fuel/forecast?country&fuel` | Bearer | History + trend + market score |

## Ingest

```bash
npm run ingest:fuel-history      # FR national + Stooq (Yahoo fallback if blocked)
npm run ingest:belgium-nap
npm run ingest:dotnl             # ~75k stations — 1/day on Free
npm run ingest:lu
# Remote:
npm run ingest -- --source=fuel-history --remote
```

| Source | Table | Notes |
|--------|-------|--------|
| `fuel-history` | fuel_* | FR avg + Brent/HO/EURUSD (Stooq, Yahoo fallback) |
| `belgium-nap` | pois | BE IRVE OCPI JSON (per EVSE) |
| `dotnl` | pois | NL IRVE — **1 row/station** (Free write budget) |
| `gireve` / `qualicharge` / `merged-irve` | pois | FR IRVE |
| `minetur` / `mimit` | pois | ES / IT fuel |
| `france-radars` / `luxembourg-radars` | zones | AAC |

Prefer **one heavy dump per day** on Free (~100k D1 writes/day, resets **00:00 UTC**).
Default ingest is **upsert** (no full DELETE). `--replace` costs ≈2× writes and can
block **all** D1 queries (including reads) until the next UTC day when the cap is hit.

```bash
npm run ingest -- --source=dotnl --remote          # upsert (~75k writes)
npm run ingest -- --source=dotnl --remote --replace # avoid on Free
```

GHA schedule `0 3 * * *`: `luxembourg-radars` + `fuel-history` remote.
