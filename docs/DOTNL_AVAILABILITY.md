# Netherlands DOT-NL EV availability (NDW)

Real-time EVSE availability for the **Netherlands**, using the open DOT-NL / NDW OCPI dump (no API key).

## Source used (no API key)

| | |
|---|---|
| **Portal** | [DOT-NL / NDW DAFNE](https://docs.ndw.nu/en/data-uitwisseling/interface-beschrijvingen/dafne-api/dafne_api_consumer_pull/) |
| **Feed** | Full OCPI 2.2.1 locations JSON (gzipped; status embedded per EVSE) |
| **URL** | `https://opendata.ndw.nu/charging_point_locations_ocpi.json.gz` |
| **API key** | None |
| **Coverage** | Public charging points reported to DOT-NL (Netherlands) |

```bash
curl -s https://opendata.ndw.nu/charging_point_locations_ocpi.json.gz | gunzip | head
```

### Optional bbox GeoJSON (not used in Gaston yet)

Max area **1.0 deg²**, max 1000 features, 10 req/s:

`https://dotnl.ndw.nu/api/rest/geojson/dynamic-road-status/charge-point-data/v1/features?bbox=minLon,minLat,maxLon,maxLat`

Example: `bbox=5.136386,52.081982,5.172843,52.097560`

## In the codebase

- **Client:** `shared/.../api/dotnl/DotNlAvailabilityClient.kt` — fetch + gunzip, parse, cache (~3 min), radius filter
- **Provider:** `DotNlAvailabilityProvider` implements `BorneAvailabilityProvider`
- Status mapping: shared [`OcpiEvseAvailability`](../shared/src/commonMain/kotlin/fr/geoking/gaston/api/common/OcpiEvseAvailability.kt) (`AVAILABLE` → Available, `CHARGING`/`BLOCKED` → Occupied, `OUTOFORDER`/`INOPERATIVE` → Maintenance, `REMOVED` skipped)

## Wiring (do not skip — parallel country agents leave this to the integrator)

### 1. `BorneAvailabilityProviderFactory`

Add an optional `dotNlProvider` and route Netherlands before the Eco-Movement fallback:

```kotlin
class BorneAvailabilityProviderFactory(
    private val belibProvider: BorneAvailabilityProvider,
    private val qualiChargeProvider: BorneAvailabilityProvider? = null,
    private val belgiumNapProvider: BorneAvailabilityProvider? = null,
    private val dotNlProvider: BorneAvailabilityProvider? = null,
    private val ecoMovementProvider: BorneAvailabilityProvider? = null,
) {
    fun getProvider(latitude: Double, longitude: Double): BorneAvailabilityProvider? {
        return when (ParkingRegion.containing(latitude, longitude)) {
            ParkingRegion.Belgium -> belgiumNapProvider ?: ecoMovementProvider
            ParkingRegion.Netherlands -> dotNlProvider ?: ecoMovementProvider
            ParkingRegion.France -> { /* existing FR / Paris logic */ }
            else -> ecoMovementProvider
        }
    }
}
```

### 2. Koin DI (`androidApp/.../di/MapModule.kt`)

```kotlin
import fr.geoking.gaston.api.dotnl.DotNlAvailabilityClient
import fr.geoking.gaston.api.dotnl.DotNlAvailabilityProvider

single { DotNlAvailabilityClient(get()) }
single<BorneAvailabilityProvider>(named("dotnl")) {
    DotNlAvailabilityProvider(get(), radiusKm = 15, limit = 200)
}
single<BorneAvailabilityProviderFactory> {
    BorneAvailabilityProviderFactory(
        belibProvider = get(named("belib")),
        qualiChargeProvider = get(named("qualicharge")),
        belgiumNapProvider = get(named("belgium_nap")),
        dotNlProvider = get(named("dotnl")),
        ecoMovementProvider = if (BuildConfig.ECO_MOVEMENT_KEY.isBlank()) {
            null
        } else {
            EcoMovementAvailabilityProvider(get(), radiusKm = 15, limit = 200)
        },
    )
}
```

### 3. Docs / Settings

List DOT-NL / NDW in Settings → About / sources and [`sources.md`](sources.md) when integrating.
