# Sweden EV availability via NOBIL (AFIR NAP)

Real-time / dump-backed EVSE availability for **Sweden**, using the Nordic [NOBIL](https://info.nobil.no/english) database — Sweden’s AFIR National Access Point (Energimyndigheten; also listed via [trafficdata.se](https://trafficdata.se)).

Same database and API as Norway; filter with `countrycode=SWE`.

## Source

| | |
|---|---|
| **NAP / operator** | Energimyndigheten (SE data in NOBIL); Enova owns the platform |
| **API docs** | [API_NOBIL_Documentation_v3](https://info.nobil.no/files/API_NOBIL_Documentation_v3_20250827.pdf) |
| **Datadump** | `https://nobil.no/api/server/datadump.php?apikey=…&countrycode=SWE&format=json&file=false` |
| **API key** | **Required** — register at [nobil.no](https://info.nobil.no) (free, CC-BY). Suggested env / `local.properties`: `NOBIL_API_KEY` |
| **Country code** | `SWE` ([`NobilClient.COUNTRY_SWE`](../shared/src/commonMain/kotlin/fr/geoking/gaston/api/nobil/NobilClient.kt) / [`SwedenNobilAvailabilityProvider.COUNTRY_CODE`](../shared/src/commonMain/kotlin/fr/geoking/gaston/api/nobil/SwedenNobilAvailabilityProvider.kt)) |
| **Coverage** | Public chargers reported into NOBIL for Sweden (OCPI + manual) |

Without `NOBIL_API_KEY`, the shared client returns an empty list (no crash).

### Real-time stream (follow-up)

Per-EVSE live status also exists via Enova WebSocket ([sanntid](https://info.nobil.no/sanntid)): signup at [data.enova.no](https://data.enova.no), GET `https://data.enova.no/real-time/v1/Realtime` with `x-api-key` → `wss://…`. Statuses are OCPI-like (`AVAILABLE`, `CHARGING`, …). Not wired in Gaston yet — the datadump embeds connector status when present (attr type 8).

## Difference from Eco-Movement fallback

| | **NOBIL (Sweden)** | **Eco-Movement OCPI** |
|---|---|---|
| Role | Country NAP / AFIR feed for SE | Commercial EU-wide OCPI fallback |
| Key | `NOBIL_API_KEY` (nobil.no) | `ECO_MOVEMENT_KEY` |
| Scope | `countrycode=SWE` only | Global locations dump |
| Prefer when | Map center is in [`ParkingRegion.Sweden`](../shared/src/commonMain/kotlin/fr/geoking/gaston/parking/ParkingRegion.kt) | Outside SE (and other NAP countries) |

When Sweden NOBIL is wired in the factory, it should win over Eco-Movement inside the Sweden bbox.

**Bbox caveat:** `ParkingRegion.Norway` is checked before `Sweden` and its coarse box overlaps much of Sweden (e.g. Stockholm). Prefer Sweden-specific sub-boxes or ISO country from reverse-geocode when routing NOR vs SWE providers; southern/eastern SE points outside Norway/Denmark boxes (e.g. Kalmar) already resolve to `ParkingRegion.Sweden`.

## In the codebase

- **Shared client:** `NobilClient` — owned with Norway; construct with `countryCode = SWE`
- **Shared provider:** `NobilAvailabilityProvider` — thin mapper over the client
- **Sweden wrapper:** `SwedenNobilAvailabilityProvider` — delegates to `NobilAvailabilityProvider`; documents / hard-codes SWE for DI
- **Status mapping:** `OcpiEvseAvailability` (+ NOBIL legacy Vacant/Busy)

## Wiring (do not apply in parallel country PRs — snippets only)

### `BorneAvailabilityProviderFactory`

Inject an optional Sweden provider and route Sweden before the Eco-Movement fallback:

```kotlin
class BorneAvailabilityProviderFactory(
    private val belibProvider: BorneAvailabilityProvider,
    private val qualiChargeProvider: BorneAvailabilityProvider? = null,
    private val belgiumNapProvider: BorneAvailabilityProvider? = null,
    private val swedenNobilProvider: BorneAvailabilityProvider? = null,
    private val ecoMovementProvider: BorneAvailabilityProvider? = null,
) {
    fun getProvider(latitude: Double, longitude: Double): BorneAvailabilityProvider? {
        return when (ParkingRegion.containing(latitude, longitude)) {
            ParkingRegion.Belgium -> belgiumNapProvider ?: ecoMovementProvider
            ParkingRegion.Sweden -> swedenNobilProvider ?: ecoMovementProvider
            ParkingRegion.France -> { /* existing QualiCharge / Belib */ … }
            else -> ecoMovementProvider
        }
    }
}
```

### Koin (`MapModule`)

```kotlin
single {
    NobilClient(
        client = get(),
        apiKey = getProperty("NOBIL_API_KEY", ""),
        countryCode = SwedenNobilAvailabilityProvider.COUNTRY_CODE, // or separate NOR + SWE singles
    )
}
// Prefer two clients if Norway + Sweden are both enabled:
single(named("nobilSweden")) {
    NobilClient(get(), apiKey = getProperty("NOBIL_API_KEY", ""), countryCode = "SWE")
}
single<BorneAvailabilityProvider>(named("swedenNobil")) {
    SwedenNobilAvailabilityProvider(get(named("nobilSweden")))
}
```

Factory ctor: `swedenNobilProvider = getOrNull(named("swedenNobil"))`.

Shared overview: [`NOBIL_AVAILABILITY.md`](NOBIL_AVAILABILITY.md) (Norway agent) when present.
