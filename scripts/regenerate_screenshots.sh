#!/usr/bin/env bash
# Regenerate Play Store / website phone screenshots from real Compose UI (Paparazzi).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "Exporting marketing screenshots (Compose + Robolectric)…"
./gradlew :androidApp:testPlaystoreDebugUnitTest \
  --tests "fr.geoking.gaston.ui.map.MarketingScreenshotTest"

echo "Done — PNGs written to playstore-assets/ and website/assets/screenshots/"
echo "Icon & feature graphic: python3 scripts/gen_assets.py"
