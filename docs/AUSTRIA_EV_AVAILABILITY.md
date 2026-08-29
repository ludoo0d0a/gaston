# Austria EV availability (E-Control Ladestellenverzeichnis)

Real-time EVSE availability for **Austria**, using the free public **E-Control charge** API (Ladestellenverzeichnis). Live status is returned **inline** on proximity search — no separate status endpoint.

This is **not** the fuel Sprit API already used by Gaston (`AustriaEControlProvider` → `https://api.e-control.at/sprit/1.0/`).

## Source

| | |
|---|---|
| **Operator / NAP** | E-Control (Ladestellenverzeichnis); also listed via [mobilitydata.gv.at](https://mobilitydata.gv.at/en) |
| **Citizen map** | [ladestellen.at](https://www.ladestellen.at) |
| **Tech info** | [e-control.at — technische Informationen](https://www.e-control.at/ladestellenverzeichnis-technische-informationen) |
| **Base URL** | `https://api.e-control.at/charge/1.0` |
| **Search (stations + live status)** | `GET /search?latitude={lat}&longitude={lon}` |
| **Optional detail** | `GET /countries/{AT}/operators/{op}/stations/{id}` (+ `/points`) — not required for map availability |
| **Auth** | Free registration → API key + registered referer domain |
| **Headers** | `Apikey: <key>` · `Referer: https://<registered-domain>` · `Accept: application/json` |
| **Register** | [admin.ladestellen.at/#/api/registrieren](https://admin.ladestellen.at/#/api/registrieren) |
| **Suggested env / `local.properties`** | `ECONTROL_EV_API_KEY`, `ECONTROL_EV_REFERER_DOMAIN` (bare hostname, e.g. `geoking.fr`) |
| **Coverage** | Public chargers reported to the Austrian directory (~national; ~100 nearest sites per `/search`) |
| **Attribution (ToU)** | `Datenquelle: E-Control` |

Without both key and referer domain, the client returns an empty list (no crash).

```bash
# Example (requires your key + a Referer hostname registered for that key)
curl -s 'https://api.e-control.at/charge/1.0/search?latitude=48.2082&longitude=16.3738' \
  -H 'Apikey: YOUR_KEY' \
  -H 'Referer: https://YOUR_REGISTERED_DOMAIN' \
  -H 'Accept: application/json' | head
```

**Status values** (RefillPointStatusEnum, OCPI-like): `AVAILABLE`, `CHARGING`, `OCCUPIED`, `BLOCKED`, `RESERVED`, `OUTOFORDER` / `OUT_OF_ORDER`, `INOPERATIVE`, `UNKNOWN`, … — mapped via `OcpiEvseAvailability` plus `OCCUPIED` → Occupied.

**Note:** Operator import / admin REST uses Basic Auth on the same host; Gaston uses only the **public** Apikey+Referer consumer API.

## Fuel vs EV (do not merge)

| | Fuel Sprit | EV charge |
|---|---|---|
| Package | `api/econtrol/AustriaEControlProvider` | `api/austria/AustriaEControlEv*` |
| Base | `…/sprit/1.0/` | `…/charge/1.0/` |
| Role | Gas stations + prices | EVSE locations + live status |

## In the codebase

- **Client:** `shared/.../api/austria/AustriaEControlEvClient.kt` — `/search`, Apikey+Referer, ~60s cache, radius filter
- **Provider:** `AustriaEControlEvAvailabilityProvider` implements `BorneAvailabilityProvider`
- **Tests:** `shared/.../api/austria/AustriaEControlEvAvailabilityTest.kt` (fixtures; no live network)

## Wiring (do not apply here — owned by another task)

Route [`ParkingRegion.Austria`](../shared/src/commonMain/kotlin/fr/geoking/gaston/parking/ParkingRegion.kt) before Eco-Movement when the key is configured.

### 1. `BorneAvailabilityProviderFactory`

```kotlin
class BorneAvailabilityProviderFactory(
    private val belibProvider: BorneAvailabilityProvider,
    private val qualiChargeProvider: BorneAvailabilityProvider? = null,
    private val belgiumNapProvider: BorneAvailabilityProvider? = null,
    private val austriaEControlEvProvider: BorneAvailabilityProvider? = null,
    private val ecoMovementProvider: BorneAvailabilityProvider? = null,
) {
    fun getProvider(latitude: Double, longitude: Double): BorneAvailabilityProvider? {
        return when (ParkingRegion.containing(latitude, longitude)) {
            ParkingRegion.Belgium -> belgiumNapProvider ?: ecoMovementProvider
            ParkingRegion.Austria -> austriaEControlEvProvider ?: ecoMovementProvider
            ParkingRegion.France -> { /* existing FR / Paris logic */ }
            else -> ecoMovementProvider
        }
    }
}
```

### 2. Koin (`androidApp/.../di/MapModule.kt`)

```kotlin
import fr.geoking.gaston.api.austria.AustriaEControlEvAvailabilityProvider
import fr.geoking.gaston.api.austria.AustriaEControlEvClient

single {
    AustriaEControlEvClient(
        client = get(),
        apiKey = getProperty("ECONTROL_EV_API_KEY", ""),
        refererDomain = getProperty("ECONTROL_EV_REFERER_DOMAIN", ""),
    )
}
single<BorneAvailabilityProvider>(named("austria_econtrol_ev")) {
    AustriaEControlEvAvailabilityProvider(get(), radiusKm = 15, limit = 200)
}
// Factory ctor: austriaEControlEvProvider = getOrNull(named("austria_econtrol_ev"))
```

### 3. Docs / Settings

List E-Control Ladestellen in Settings → About / [`sources.md`](sources.md) and optionally [`ENV_VARS.md`](ENV_VARS.md) / [`API_KEYS.md`](API_KEYS.md) when integrating.
