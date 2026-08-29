# Ireland EV stations + availability (TII DXP → data.gov.ie)

Pragmatic status for **Ireland (IE)**: there is **no** Belgium/NL-style free national OCPI locations dump on the NAP yet. Use **Eco-Movement OCPI** for EV stations and live EVSE status when `ECO_MOVEMENT_KEY` is set. A direct **TII DXP** consumer client is **not** implemented (publication pending / no open pull URL).

## Probe results (2026-08-29, readonly curl)

| Endpoint / query | Result |
|---|---|
| CKAN `package_search?q=OCPI` on [data.gov.ie](https://data.gov.ie) | **count = 0** |
| CKAN `resource_search?query=name:ocpi` / `locations.json` | **0** resources |
| CKAN org `transport-infrastructure-ireland` | Traffic/Luas packages only — **no** AFIR/OCPI/DXP EV feed |
| CKAN `package_search?q=AFIR` / `DXP` / `alternative fuels` | **count = 0** |
| [ESB EV Public Charging Network](https://data.gov.ie/dataset/esb-ev-public-charging-network) CSV | **200** — static site list (lat/lon, plugs, prices); **no** EVSE `status` |
| `https://dxp.tii.ie/` and `…/ocpi/cpo/2.2.1/locations` | Host resolves (`20.82.177.60`); **502** Bad Gateway (Azure App Gateway) |
| `https://afir.tii.ie/`, `https://nap.tii.ie/` | Same **502** pattern / unreachable as open pull |
| `https://ocpi.esb.ie/…`, `https://api.esb.ie/ecars/locations` | **DNS fail / 404** — not a public OCPI dump |
| Eco-Movement `…/ocpi/cpo/2.2.1/locations` | **401** without token (expected) |

Local Cork / DLR EV GIS layers and CSO **PCIEV\*** statistical cubes are **not** live national availability feeds.

**Conclusion:** No open OCPI/locations JSON suitable for a `BorneAvailabilityProvider` client. Interim = Eco-Movement.

## Official sources (AFIR / TII)

| | |
|---|---|
| **NAP** | [data.gov.ie](https://data.gov.ie) (current Irish National Access Point per TII FAQs) |
| **DXP** | [Register on the DXP](https://www.tii.ie/en/roads-tolling/alt-fuel-projects-unit/alt-fuels-data-office/register-dxp/) — **CPO onboarding** (OCPI 2.2.1 push/handshake with TII) |
| **FAQs** | [IDRO / DXP FAQs](https://www.tii.ie/en/roads-tolling/alt-fuel-projects-unit/alt-fuels-data-office/idro-dxp-faqs/) |
| **Protocol** | OCPI **2.2.1** (CPO → DXP); DXP then “made discoverable” on the NAP |
| **Consumer API today** | **Not published** as an anonymous dump URL (unlike BE Road.io / NL NDW) |
| **Auth for CPOs** | DXP registration form + OCPI credentials via TII onboarding — not a public Bearer for apps |
| **Contact** | **AFIRdata@tii.ie** (AFIR data / DXP); **IDRO@tii.ie** (party IDs) |
| **IDRO register** | [IDRO Public Register (PDF)](https://www.tii.ie/media/pqigaadi/idro-public-register-19-march-2026.pdf) — CPO party IDs (IE-ESB, IE-IOY, …) |

TII states AFIR data should become **freely available** via the NAP API once DXP publication lands. Until a concrete resource URL appears on data.gov.ie, do not invent endpoints.

## Recommended approach (Gaston today)

| Need | Source | Auth |
|------|--------|------|
| EV locations (POI) | Eco-Movement OCPI 2.2.1 | `ECO_MOVEMENT_KEY` |
| EVSE availability | Same Eco-Movement locations (EVSE `status`) | same key |
| Fuel stations | Existing Pick A Pump (`IrelandPickAPumpProvider`) | none (existing) |
| National NAP (AFIR) | TII DXP → data.gov.ie — **not wired** | TBD when published (expect open or light registration) |

Country routing already treats IE like other EU fallbacks: when the map center is outside FR/BE specialty feeds, [BorneAvailabilityProviderFactory](../shared/src/commonMain/kotlin/fr/geoking/gaston/api/belib/BorneAvailabilityProviderFactory.kt) returns Eco-Movement. Region box: [ParkingRegion.Ireland](../shared/src/commonMain/kotlin/fr/geoking/gaston/parking/ParkingRegion.kt).

Code marker (no network): `shared/.../api/ireland/IrelandTiiEcoMovementAvailabilityNote.kt` and optional wrapper `IrelandTiiEcoMovementAvailabilityProvider`.

## Why not a thin TII OCPI client yet

| Barrier | Detail |
|---------|--------|
| No NAP resource | Zero OCPI / `locations.json` packages or resources on data.gov.ie (probe above) |
| DXP is CPO-facing | Registration docs describe CPO → DXP OCPI connection, not a documented public consumer pull |
| Guessed hosts unhealthy | `dxp.tii.ie` exists but returns **502**; no JSON body to parse |
| ESB open CSV | Static inventory only — cannot map to `AvailabilityStatus` |
| Honest client | Shipping a fake “open TII URL” would fail at runtime |

When TII publishes a Belgium-style dump (or OCPI pull with a free token), add `IrelandTiiAvailabilityClient` + `IrelandTiiAvailabilityProvider` under `api/ireland/` (or `api/tii/`), map status via [`OcpiEvseAvailability`](../shared/src/commonMain/kotlin/fr/geoking/gaston/api/common/OcpiEvseAvailability.kt), and prefer it over Eco-Movement for `ParkingRegion.Ireland`.

## Eco-Movement vs TII DXP

| | Eco-Movement OCPI | TII DXP → NAP |
|---|-------------------|---------------|
| Protocol | OCPI 2.2.1 JSON | OCPI 2.2.1 (CPO→DXP); NAP discovery TBD |
| Coverage | EU/global commercial catalogue (includes IE) | Irish public CPO footprint via IDRO/DXP |
| Gaston today | Yes (`EcoMovementOcpiClient` / `EcoMovementAvailabilityProvider`) | No |
| Auth model | Token header (`ECO_MOVEMENT_KEY`) | Unknown until NAP resource; CPO onboarding is separate |
| Docs | [`API_KEYS.md`](API_KEYS.md#eco-movement-ev-eu--global-ocpi-221) | This page + TII Alt Fuels Data Office |

Belgium / NL contrast: open dumps — [`BELGIUM_NAP_AVAILABILITY.md`](BELGIUM_NAP_AVAILABILITY.md), [`DOTNL_AVAILABILITY.md`](DOTNL_AVAILABILITY.md).

## Contacts / links

| Contact / link | Use |
|----------------|-----|
| **AFIRdata@tii.ie** | Ask when/where DXP OCPI data is published on data.gov.ie; consumer access |
| **IDRO@tii.ie** | Party ID registration (CPO/eMSP) |
| [TII Alt Fuels Data Office](https://www.tii.ie/en/roads-tolling/alt-fuel-projects-unit/alt-fuels-data-office/) | IDRO + DXP overview |
| [data.gov.ie](https://data.gov.ie) | Watch for TII / AFIR / OCPI datasets |
| **partners@eco-movement.com** / [developers.eco-movement.com](https://developers.eco-movement.com) | OCPI Data API token (`ECO_MOVEMENT_KEY`) — current Gaston path for IE |

## Wiring (do not apply here — factory / MapModule / sources owned elsewhere)

### 1. `BorneAvailabilityProviderFactory`

Today IE already hits Eco-Movement via `else`. To make Ireland explicit (and use the thin wrapper):

```kotlin
class BorneAvailabilityProviderFactory(
    private val belibProvider: BorneAvailabilityProvider,
    private val qualiChargeProvider: BorneAvailabilityProvider? = null,
    private val belgiumNapProvider: BorneAvailabilityProvider? = null,
    private val irelandTiiProvider: BorneAvailabilityProvider? = null,
    private val ecoMovementProvider: BorneAvailabilityProvider? = null,
) {
    fun getProvider(latitude: Double, longitude: Double): BorneAvailabilityProvider? {
        return when (ParkingRegion.containing(latitude, longitude)) {
            ParkingRegion.Belgium -> belgiumNapProvider ?: ecoMovementProvider
            ParkingRegion.Ireland -> irelandTiiProvider ?: ecoMovementProvider
            ParkingRegion.France -> { /* existing FR / Paris logic */ }
            else -> ecoMovementProvider
        }
    }
}
```

Until a real TII client exists, pass the Eco-Movement-backed wrapper as `irelandTiiProvider` (same as Germany’s pattern).

### 2. Koin DI (`androidApp/.../di/MapModule.kt`)

```kotlin
import fr.geoking.gaston.api.ireland.IrelandTiiEcoMovementAvailabilityProvider

val eco = if (BuildConfig.ECO_MOVEMENT_KEY.isBlank()) {
    null
} else {
    EcoMovementAvailabilityProvider(get(), radiusKm = 15, limit = 200)
}
single<BorneAvailabilityProviderFactory> {
    BorneAvailabilityProviderFactory(
        belibProvider = get(named("belib")),
        qualiChargeProvider = get(named("qualicharge")),
        belgiumNapProvider = get(named("belgium_nap")),
        irelandTiiProvider = eco?.let { IrelandTiiEcoMovementAvailabilityProvider(it) },
        ecoMovementProvider = eco,
    )
}
```

### 3. Docs / Settings (when integrating)

| **Eco‑Movement (OCPI)** | Ireland (among EU) | EV + availability | `open-chargepoints.com` … | `ECO_MOVEMENT_KEY` — preferred for IE until TII DXP on data.gov.ie; see [`IRELAND_TII_AVAILABILITY.md`](IRELAND_TII_AVAILABILITY.md) |

Fuel: keep Pick A Pump listed separately in [`sources.md`](sources.md).

### 4. Future TII client (out of scope until NAP URL exists)

1. Re-run CKAN search / ask **AFIRdata@tii.ie** for the consumer locations URL.
2. If open OCPI locations JSON (optionally gzipped): copy `DotNlAvailabilityClient` / `BelgiumNapAvailabilityClient` shape — cache, haversine filter, skip `REMOVED`, map via `OcpiEvseAvailability`.
3. Prefer `IrelandTiiAvailabilityProvider` over Eco-Movement for `ParkingRegion.Ireland`.
4. Add host tests under `shared/src/commonTest/.../api/ireland/`.

## In the codebase (this deliverable)

- **Note + wrapper:** `shared/.../api/ireland/IrelandTiiEcoMovementAvailabilityNote.kt`
- **Doc:** this file
- **No** TII HTTP client (no open endpoint)
- **No** unit tests (no parsing code)
- **Not** wired in factory / MapModule / `sources.md` / European coverage tests (owned by other agents)
