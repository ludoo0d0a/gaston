#!/usr/bin/env bash
# Phase A Ops Free smoke checks (no secrets printed).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BASE="${GASTON_API_BASE:-https://gaston-api.ludovic-valente.workers.dev}"
DEV_VARS="$ROOT/.dev.vars"
NODE=(node)
if command -v mise >/dev/null 2>&1; then
  NODE=(mise exec node@22 -- node)
fi

echo "== health =="
curl -fsS "$BASE/health"
echo

if [[ -f "$DEV_VARS" ]]; then
  # shellcheck disable=SC1090
  set -a; source "$DEV_VARS"; set +a
fi
if [[ -z "${APP_API_KEY:-}" ]]; then
  echo "WARN: APP_API_KEY not in env/.dev.vars — skipping authed checks" >&2
  exit 0
fi

AUTH="Authorization: Bearer ${APP_API_KEY}"
echo "== fuel forecast =="
curl -fsS -H "$AUTH" "$BASE/v1/fuel/forecast?country=FR&fuel=gazole" \
  | "${NODE[@]}" -e "let d='';process.stdin.on('data',c=>d+=c);process.stdin.on('end',()=>{const j=JSON.parse(d);console.log({hist:j.history?.length,fc:j.forecast?.length,score:j.market_score});});"

echo "== pois BE =="
curl -fsS -H "$AUTH" "$BASE/v1/pois?lat=50.85&lon=4.35&radius_km=2&source=belgium-nap&limit=1" \
  | "${NODE[@]}" -e "let d='';process.stdin.on('data',c=>d+=c);process.stdin.on('end',()=>{const j=JSON.parse(d);console.log({features:j.features?.length});});"

echo "== GHA secrets (names only) =="
gh secret list -R "${GITHUB_REPO:-ludoo0d0a/gaston}" | grep CLOUDFLARE || true
