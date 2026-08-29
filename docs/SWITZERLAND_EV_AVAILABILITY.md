# Switzerland EV availability (ich-tanke-strom / BFE)

Real-time EVSE availability for **Switzerland**, using the open **ich-tanke-strom** national electromobility dumps (BFE / EnergieSchweiz).

## Source (no API key)

| | |
|---|---|
| **Programme** | [ich-tanke-strom](https://www.ich-tanke-strom.ch) / [recharge-my-car.ch](https://recharge-my-car.ch) |
| **Dataset** | [opendata.swiss — Ladestationen](https://opendata.swiss/en/dataset/ladestationen) |
| **Static JSON** | `https://data.geo.admin.ch/ch.bfe.ladestellen-elektromobilitaet/data/ch.bfe.ladestellen-elektromobilitaet.json` |
| **Status JSON** | `https://data.geo.admin.ch/ch.bfe.ladestellen-elektromobilitaet/status/ch.bfe.ladestellen-elektromobilitaet.json` |
| **Format** | OICP-inspired custom JSON (`EVSEData` / `EVSEStatuses`) — **not** OCPI 2.2.1 |
| **Auth** | None (open download) |
| **Docs** | [SFOE/ichtankestrom_Documentation](https://github.com/SFOE/ichtankestrom_Documentation) |

### Licence

Open use with **attribution** (O-By-Ask). **Commercial use may require prior BFE permission** — confirm before shipping in a paid store build. See [EnergieSchweiz](https://www.energieschweiz.ch/ladeinfrastruktur/werkzeuge/ich-tanke-strom/) / opendata.swiss terms.

## Status mapping (OICP → app)

| OICP `EVSEStatus` | `AvailabilityStatus` | Notes |
|-------------------|----------------------|-------|
| `Available` | Available | |
| `Occupied` | Occupied | |
| `Reserved` | Reserved | |
| `OutOfService` | Maintenance | |
| `Unknown` / missing | Unknown | |
| `EvseNotFound` | (skipped) | Treated like OCPI `REMOVED` |

Match key: `EvseID` (join static ↔ status). Coordinates from static `GeoCoordinates.Google` (`"lat lon"`). Station id: `ChargingStationId` when present.

## In the codebase

- **Client:** `shared/.../api/switzerland/IchTankeStromAvailabilityClient.kt` — fetch/parse/cache (status ~60s, static ~1h), radius filter
- **Provider:** `IchTankeStromAvailabilityProvider` implements `BorneAvailabilityProvider`
- **Tests:** `shared/.../api/switzerland/IchTankeStromAvailabilityTest.kt` (fixture JSON, no network)

## Factory / DI wiring (do not apply in this PR — parallel agents)

`ParkingRegion.Switzerland` already exists. Wire **before** Eco-Movement fallback:

### `BorneAvailabilityProviderFactory`

```kotlin
class BorneAvailabilityProviderFactory(
    private val belibProvider: BorneAvailabilityProvider,
    private val qualiChargeProvider: BorneAvailabilityProvider? = null,
    private val belgiumNapProvider: BorneAvailabilityProvider? = null,
    private val ichTankeStromProvider: BorneAvailabilityProvider? = null, // NEW
    private val ecoMovementProvider: BorneAvailabilityProvider? = null,
) {
    fun getProvider(latitude: Double, longitude: Double): BorneAvailabilityProvider? {
        return when (ParkingRegion.containing(latitude, longitude)) {
            ParkingRegion.Belgium -> belgiumNapProvider ?: ecoMovementProvider
            ParkingRegion.Switzerland -> ichTankeStromProvider ?: ecoMovementProvider // NEW
            ParkingRegion.France -> { /* existing Paris / QualiCharge */ }
            else -> ecoMovementProvider
        }
    }
}
```

### Koin (`MapModule` or equivalent)

```kotlin
single { IchTankeStromAvailabilityClient(get()) }
single<BorneAvailabilityProvider>(named("ichtankestrom")) {
    IchTankeStromAvailabilityProvider(get())
}
// In BorneAvailabilityProviderFactory constructor:
//   ichTankeStromProvider = getOrNull(named("ichtankestrom")),
```

### Imports

```kotlin
import fr.geoking.gaston.api.switzerland.IchTankeStromAvailabilityClient
import fr.geoking.gaston.api.switzerland.IchTankeStromAvailabilityProvider
```

## Follow-ups (out of scope here)

- Entry in [`sources.md`](sources.md) + Settings / `UsedApis`
- Factory + `EuropeanEvCoverageTest` routing for Zurich (`47.3769, 8.5417`)
- Confirm commercial licence before Play Store monetisation
