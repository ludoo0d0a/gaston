# Gaston – Fuel & EV station finder (with prices)

Gaston is a **Kotlin Multiplatform (KMP)** app for **Android** and **Android Auto** that helps you find:

- **Fuel stations** with **fuel prices** (when the data source provides it)
- **EV charging stations (IRVE)** with connector / power details (and price info when available)

## Project structure

- **`:shared`**: core models + networking + providers shared across platforms
- **`:androidApp`**: phone UI + Android Auto (Car App Library) UI

## Key features

- **Map + POI search**: nearby stations, filters (energy, brand, connectors, services, power, etc.)
- **Prices**: fuel prices and (when available) charging pricing text from providers
- **Route planning**: find stations along a route (where supported by providers/routing)
- **Android Auto**: car-friendly UI and templates for safe in-car usage

## Getting started

### Prerequisites

- Android Studio (Koala or newer recommended)
- JDK 17+

### Build

```bash
./gradlew :androidApp:assembleFullDebug
```

### Configuration

Create/update `local.properties` (not committed) for your SDK path and required keys:

```properties
sdk.dir=/path/to/your/android/sdk
GOOGLE_MAPS_KEY=your_maps_sdk_key
```

- Full list of supported build-time keys: `docs/ENV_VARS.md`
- Google Maps setup: `docs/MAPS_API_KEY_SETUP.md`
- Google Play migration/setup: `docs/GOOGLE_PLAY_MIGRATION.md`
- Android Auto DHU debugging: `docs/ANDROID_AUTO_DHU_DEBUG.md`

## Tech stack

- Kotlin 2.3.x + Kotlin Multiplatform
- Jetpack Compose
- Android Car App Library (Android Auto)
- Ktor (network)
- Koin (DI)
