# Finland Digitraffic AFIR EV availability

Real-time EVSE availability for **Finland**, using Fintraffic Digitraffic’s open AFIR charging-network API (no API key).

## Source used (no API key)

| | |
|---|---|
| **Portal** | [Digitraffic — Alternative Fuels (AFIR)](https://www.digitraffic.fi/en/road-traffic/afir/) |
| **Swagger** | [afir.digitraffic.fi/swagger](https://afir.digitraffic.fi/swagger/) |
| **CPO onboarding** | [Fintraffic AFIR](https://www.fintraffic.fi/en/digitalservices/afir) |
| **Auth** | None (open data). Licence: Creative Commons 4.0 BY |
| **Coverage** | CPOs publishing to Digitraffic AFIR (growing under AFIR Art. 20) |

### Endpoints (used)

| Role | URL |
|------|-----|
| **Locations snapshot** (GeoJSON FeatureCollection) | `https://afir.digitraffic.fi/api/charging-network/v1/locations/all` |
| **Statuses snapshot** (JSON `statuses[]`) | `https://afir.digitraffic.fi/api/charging-network/v1/locations/statuses/all` |

Prefer `/all` snapshot URLs directly (Digitraffic docs). Paginated alternatives: `…/locations` and `…/locations/statuses` with cursor (`?cursor=`) and fixed page size 500; `?limit=ALL` redirects to `/all`.

**Required header:** `Accept-Encoding: gzip` — without it the API returns **HTTP 406**.

```bash
curl -sS -H "Accept-Encoding: gzip" --compressed \
  "https://afir.digitraffic.fi/api/charging-network/v1/locations/statuses/all" | head
```

### Not used (yet)

| Feed | Notes |
|------|--------|
| DATEX II v3.6 locations / statuses | Heavier XML; GeoJSON+JSON is enough for Gaston |
| Tariffs API | Pricing not wired for availability |
| MQTT `wss://afir.digitraffic.fi:443/mqtt` (`status-v1/…`) | Real-time push; REST snapshots + ~60s cache are sufficient for map viewport |

Status enum (OCPI-compatible): `AVAILABLE`, `BLOCKED`, `CHARGING`, `INOPERATIVE`, `OUTOFORDER`, `PLANNED`, `REMOVED`, `RESERVED`, `UNKNOWN`.

## In the codebase

- **Client:** `shared/.../api/finland/DigitrafficAfirAvailabilityClient.kt` — fetch gzip snapshots, parse, join by EVSE id, cache (~15 min locations, ~60s statuses), radius filter
- **Provider:** `DigitrafficAfirAvailabilityProvider` implements `BorneAvailabilityProvider`
- **Tests:** `shared/.../api/finland/DigitrafficAfirAvailabilityTest.kt` (fixtures; no live network)
- Status mapping: shared [`OcpiEvseAvailability`](../shared/src/commonMain/kotlin/fr/geoking/gaston/api/common/OcpiEvseAvailability.kt)

### ID matching

| Field | Digitraffic | Gaston |
|-------|-------------|--------|
| EVSE / PDC id | `properties.evses[].id` ↔ `statuses[].evseId` (e.g. `FI*HLN*E218692*01`) | `PdcAvailability.id` |
| Station | `properties.id` | `PdcAvailability.stationId` |
| Coordinates | GeoJSON `geometry.coordinates` **`[lon, lat]`** | lat/lon on each PDC |

## Wiring (do not skip — parallel country agents leave this to the integrator)

### 1. `BorneAvailabilityProviderFactory`

Add an optional `digitrafficAfirProvider` and route Finland before the Eco-Movement fallback:

```kotlin
class BorneAvailabilityProviderFactory(
    private val belibProvider: BorneAvailabilityProvider,
    private val qualiChargeProvider: BorneAvailabilityProvider? = null,
    private val belgiumNapProvider: BorneAvailabilityProvider? = null,
    private val digitrafficAfirProvider: BorneAvailabilityProvider? = null,
    private val ecoMovementProvider: BorneAvailabilityProvider? = null,
) {
    fun getProvider(latitude: Double, longitude: Double): BorneAvailabilityProvider? {
        return when (ParkingRegion.containing(latitude, longitude)) {
            ParkingRegion.Belgium -> belgiumNapProvider ?: ecoMovementProvider
            ParkingRegion.Finland -> digitrafficAfirProvider ?: ecoMovementProvider
            ParkingRegion.France -> { /* existing FR / Paris logic */ }
            else -> ecoMovementProvider
        }
    }
}
```

`ParkingRegion.Finland` already exists (`countryCode` `FI`).

### 2. Koin DI (`androidApp/.../di/MapModule.kt`)

```kotlin
import fr.geoking.gaston.api.finland.DigitrafficAfirAvailabilityClient
import fr.geoking.gaston.api.finland.DigitrafficAfirAvailabilityProvider

single { DigitrafficAfirAvailabilityClient(get()) }
single<BorneAvailabilityProvider>(named("digitraffic_afir")) {
    DigitrafficAfirAvailabilityProvider(get(), radiusKm = 15, limit = 200)
}
single<BorneAvailabilityProviderFactory> {
    BorneAvailabilityProviderFactory(
        belibProvider = get(named("belib")),
        qualiChargeProvider = get(named("qualicharge")),
        belgiumNapProvider = get(named("belgium_nap")),
        digitrafficAfirProvider = get(named("digitraffic_afir")),
        ecoMovementProvider = if (BuildConfig.ECO_MOVEMENT_KEY.isBlank()) {
            null
        } else {
            EcoMovementAvailabilityProvider(get(), radiusKm = 15, limit = 200)
        },
    )
}
```

### 3. Docs / Settings

List Digitraffic AFIR in Settings → About / sources and [`sources.md`](sources.md) when integrating.
