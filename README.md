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

### 🆘 Emergency helper & highway costs
Quick access to road-trip essentials when something goes wrong, plus highway toll estimation to plan the real cost of a journey.

---

## Project structure

| Module | Contents |
|--------|----------|
| `:shared` | Core models, networking, provider clients — shared across platforms |
| `:androidApp` | Phone UI (Jetpack Compose) + Android Auto (Car App Library) |

---

## Key features

### Map & POIs

- Interactive map with two engines: **Google Maps** and **MapLibre** (with light/dark/auto themes)
- Nearby POI search (fuel stations, EV chargers, rest stops, services)
- **Search along your route** — pick a destination and get POIs within a configurable radius from the path
- POI detail view with full price list, address, services, opening hours and quick actions
- **Favorites** — save and re-open your favorite stations / chargers
- **Local ratings** (1–5 stars, on-device, no backend required)
- Brand-aware POI markers (Shell, TotalEnergies, Leclerc, BP, Esso, Auchan, Intermarché, …)
- Real-time **Google traffic** overlay (when Google Maps engine is selected)

### Fuel prices

- Live prices for **SP95, SP95-E10, SP98, Diesel, Diesel+, GPL** and country-specific fuels
- "Cheapest stations" card on the dashboard
- **Fuel price forecast / history** — per-station price history and national price charts
- Country-aware reference prices (OpenVan.camp, weekly baselines)
- **Fuel card** preference (Routex, UTA Edenred, TotalEnergies Fleet, Shell Fleet ID, GO, DKV, EuroShell, Aral, Repsol)
- Filter to **only show highway stations**

### EV charging

- Public charging points across multiple sources (data.gouv IRVE, OpenChargeMap, Fastned, DKV, Eco‑Movement, Chargy …)
- Connector type filter — **CCS2, CHAdeMO, Type 2, Type 3, Tesla, E/F**
- Minimum power filter — **22 kW AC → 350 kW DC** and beyond
- Per-kWh pricing where available
- **Real-time availability** (Belib for Paris, OCPI feeds where exposed)
- IRVE operator and power-tier selection

### Vehicle profile

- Energy type: **Gas / Electric / Hybrid**
- Brand & model
- Tank capacity (L) and consumption (L/100 km) for thermal engines
- Battery capacity (kWh), range (km) and consumption (kWh/100 km) for EVs
- Preferred fuel types and preferred power tiers — used to auto-tune map filters

### Route planning & navigation

- Route planning **A → B** with **OSRM**
- Suggested fuel & charging **stops along the route** (vehicle-aware)
- Route preview (distance, duration, polyline)
- One-tap **navigation hand-off** to the system maps app
- **Highway cost planner** — estimate motorway tolls for French routes with OpenTollData
- Multi-region **traffic events** (CITA Luxembourg, TomTom, Google traffic layer)
- **Weather** along the route (Open-Meteo + WMO codes)

### Emergency helper

- Quick emergency helper for road incidents and unexpected stops
- Surface nearby useful POIs such as parking, rest areas, toilets, water and services
- One-tap navigation to the closest safe stop or assistance location
- Android Auto-friendly access so drivers can stay focused on the road

### Rest stops & services (POIs)

- Toilets, drinking water, rest areas, picnic spots
- **Camper-van services** (water, waste, electricity)
- **Parking** (ParkApi, OpenStreetMap)
- Camping sites (Hérault Data, OSM)
- Configurable Overpass amenity selection

### Multi-country data sources

- **30+ POI providers** spanning Western, Central, Northern, Southern Europe and beyond
- Selection mode: **Manual** (pick exact sources) or **Auto** (auto-select by current country)
- Per-country toggles — enable/disable a whole country in one tap
- Optional API keys for some feeds (OpenChargeMap, Eco-Movement, Tankerkönig, Fuelprices.dk, NSW FuelCheck, …)
- Full source list: [`docs/sources.md`](docs/sources.md) · how to get keys: [`docs/API_KEYS.md`](docs/API_KEYS.md)

### Android Auto

- First-class Android Auto integration (Car App Library)
- Car-friendly templates: **list, grid, pane, place-list-map, navigation, message, search, sign-in**
- POI map screen with surface-rendered LibreMap (lab)
- **In-car filters** — energy, brand, connector, min. power, IRVE operator, services, Overpass amenities
- **Route planning & route preview** in the car
- **Fuel forecast** in the car
- Vehicle settings (tank, consumption, battery, EV range, energy type)
- Settings, sources and POI provider selection from the car
- Network/location info screen + error / guidance screens

### Account, sync & monetization

- **Google sign-in** (Firebase Auth)
- **Cloud settings sync** via Firestore (favorites, vehicle profile, preferred sources)
- **Premium tier** with paywall popup (Google Play Billing)
- **AdMob banner** for the free tier
- **In-app updates** (Google Play Core)
- Local **error log** with copy-to-clipboard

### Architecture & platform

- **Kotlin Multiplatform** — `:shared` module for models, networking and provider clients
- Phone UI in **Jetpack Compose** (Material 3); Auto UI via **Car App Library**
- Networking with **Ktor**, DI with **Koin**
- On-device persistence with **Room** (price history, national fuel prices, favorites)
- Cache management with one-tap "Clear cache"
- **Android 15+ 16 KB page size** support (lint-checked in CI)

---

## Getting started

### Prerequisites

- Android Studio Koala or newer
- JDK 17+

### Build

```bash
./gradlew :androidApp:assembleFullDebug
```

### E2E Testing (Maestro)

Gaston uses **Maestro** for end-to-end UI testing. Tests are located in the `.maestro/` directory.

To run tests:
1. [Install Maestro](https://maestro.mobile.dev/getting-started/installing-maestro)
2. Start an Android emulator or connect a device
3. Run the flows:
   ```bash
   maestro test .maestro/dashboard.yaml
   maestro test .maestro/map_navigation.yaml
   maestro test .maestro/settings_navigation.yaml
   maestro test .maestro/emergency.yaml
   maestro test .maestro/fuel_forecast.yaml
   maestro test .maestro/route_planning.yaml
   ```

### Configuration

Create / update `local.properties` (not committed):

```properties
sdk.dir=/path/to/your/android/sdk
GOOGLE_MAPS_KEY=your_maps_sdk_key
```

Full list of build-time keys → [`docs/ENV_VARS.md`](docs/ENV_VARS.md)

Additional setup guides:
- API keys for data sources → [`docs/API_KEYS.md`](docs/API_KEYS.md)
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
