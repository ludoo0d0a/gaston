#!/usr/bin/env bash
# Set GitHub Actions Cloudflare secrets for backend ingest (Free).
# Usage:
#   1. Create an Account API token (Edit Cloudflare Workers + D1 Edit + R2):
#      https://dash.cloudflare.com/?to=/:account/api-tokens
#   2. Copy the token once, then:
#        pbpaste | ./scripts/ops/set-gha-cf-token.sh
#      or:
#        ./scripts/ops/set-gha-cf-token.sh   # then paste token, Enter, Ctrl-D
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REPO="${GITHUB_REPO:-ludoo0d0a/gaston}"
ACCOUNT_ID="${CLOUDFLARE_ACCOUNT_ID:-04efa4000e37d6c1df300af5da041485}"

if ! command -v gh >/dev/null; then
  echo "gh CLI required" >&2
  exit 1
fi

echo "Setting CLOUDFLARE_ACCOUNT_ID=$ACCOUNT_ID on $REPO"
printf '%s' "$ACCOUNT_ID" | gh secret set CLOUDFLARE_ACCOUNT_ID -R "$REPO"

echo "Paste CLOUDFLARE_API_TOKEN (input hidden), then Ctrl-D / EOF:"
TOKEN="$(cat)"
TOKEN="$(printf '%s' "$TOKEN" | tr -d '\r\n[:space:]')"
if [[ ${#TOKEN} -lt 20 ]]; then
  echo "Token looks empty/too short — aborted." >&2
  exit 1
fi
printf '%s' "$TOKEN" | gh secret set CLOUDFLARE_API_TOKEN -R "$REPO"
echo "OK: CLOUDFLARE_API_TOKEN set on $REPO"
echo "Verify: gh secret list -R $REPO | grep CLOUDFLARE"
echo "Test: gh workflow run 'Backend ingest' -R $REPO -f source=fuel-history -f target=remote"
