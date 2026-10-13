#!/usr/bin/env bash
# Regenerate Play Store / website phone screenshots from real Compose UI (Robolectric).
# Map shot uses Maps Static API when GOOGLE_MAPS_KEY is set (network); otherwise the
# bundled asset androidApp/src/main/assets/marketing/map-paris-static.png.
# Force offline: GASTON_STATIC_MAP_OFFLINE=1 ./scripts/regenerate_screenshots.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "Exporting marketing screenshots (Compose + Robolectric)…"
./gradlew :androidApp:testPlaystoreDebugUnitTest \
  --tests "fr.geoking.gaston.marketing.MarketingScreenshotTest"

echo "Done — PNGs written to playstore-assets/ and website/assets/screenshots/"
echo "Icon & feature graphic: python3 scripts/gen_assets.py"
