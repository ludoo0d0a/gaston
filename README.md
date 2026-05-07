# Gaston — Fuel & EV station finder with prices

**Gaston** is a **Kotlin Multiplatform (KMP)** app for **Android** and **Android Auto** that helps every driver find the right stop at the right price.

Whether you need the cheapest SP95 on the motorway, the nearest fast charger for your EV, or just a clean rest area with toilets — Gaston has you covered, on your phone and in the car.

---

## What Gaston does

### ⛽ Fuel stations & prices
Real-time fuel prices across dozens of brands (Shell, TotalEnergies, Leclerc, Auchan, Intermarché, BP, Esso and more). Compare SP95, SP95-E10, SP98, Diesel, Diesel+, GPL at a glance and pick the cheapest pump near you or along your route.

### ⚡ EV charging
Locate IRVE public charging points with connector details (CCS2, CHAdeMO, Type 2, Tesla), power rating (22 kW AC → 350 kW DC) and per-kWh pricing where available. Filter by minimum power and check real-time availability.

### 🗺 Route-based search
Don't just search around you — find stations *along your route*. Combine fuel and charging stops for long-distance trips without detours.

### 🛑 Rest stops & services
On long journeys, Gaston also surfaces toilets, rest areas, picnic spots, camper-van services and parking so every passenger is happy.

### 🚗 Android Auto — eyes on the road
First-class Android Auto support using the Car App Library. Big readable cards, safe templates and one-tap navigation — no fumbling with your phone while driving.

---

## Project structure

| Module | Contents |
|--------|----------|
| `:shared` | Core models, networking, provider clients — shared across platforms |
| `:androidApp` | Phone UI (Jetpack Compose) + Android Auto (Car App Library) |

---

## Key features

- **Map + POI search** — nearby stations, search along route, interactive map (MapLibre)
- **Prices** — fuel prices and EV charging costs from open/public data sources
- **Rich filters** — energy type, brand, connector, min. power, services, open status
- **Android Auto** — car-friendly templates (list, place details, route preview)
- **KMP architecture** — shared business logic, platform-specific UI

---

## Getting started

### Prerequisites

- Android Studio Koala or newer
- JDK 17+

### Build

```bash
./gradlew :androidApp:assembleFullDebug
```

### Configuration

Create / update `local.properties` (not committed):

```properties
sdk.dir=/path/to/your/android/sdk
GOOGLE_MAPS_KEY=your_maps_sdk_key
```

Full list of build-time keys → [`docs/ENV_VARS.md`](docs/ENV_VARS.md)

Additional setup guides:
- Google Maps API key → [`docs/MAPS_API_KEY_SETUP.md`](docs/MAPS_API_KEY_SETUP.md)
- Google Play setup → [`docs/GOOGLE_PLAY_MIGRATION.md`](docs/GOOGLE_PLAY_MIGRATION.md)
- Android Auto DHU debugging → [`docs/ANDROID_AUTO_DHU_DEBUG.md`](docs/ANDROID_AUTO_DHU_DEBUG.md)

---

## Play Store assets

All icons, screenshots and the feature graphic live in [`playstore-assets/`](playstore-assets/).
Regenerate after any brand change:

```bash
python3 scripts/gen_assets.py
```

See [`playstore-assets/README.md`](playstore-assets/README.md) for upload instructions and store listing copy.

---

## Free France EV charging “day prices” (tariff baselines)

For coarse, **free/public** France pricing baselines (not per-station for all networks), you can run:

```bash
python3 scripts/ev_prices_fr.py
```

Outputs:
- `tmp/ev-prices-fr-YYYY-MM-DD.json`
- `tmp/ev-prices-fr-YYYY-MM-DD.csv`

---

## Tech stack

| Layer | Library |
|-------|---------|
| Language | Kotlin 2.3.x + Kotlin Multiplatform |
| Phone UI | Jetpack Compose |
| Car UI | Android Car App Library (Android Auto) |
| Map | MapLibre Android |
| Network | Ktor |
| DI | Koin |

---

## 16 KB page size support (Android 15+)

Gaston doesn’t ship any native code from this repo, but some dependencies may include native (`.so`) libraries. The project uses a recent Android Gradle Plugin (AGP) and runs Android Lint in CI so **misaligned native libraries (16 KB requirement)** are detected early.

Reference: `Support 16 KB page sizes` → `https://developer.android.com/guide/practices/page-sizes`
