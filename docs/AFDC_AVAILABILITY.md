# AFDC / NREL EV stations (United States + Canada)

Station inventory and coarse status for **US** and **Canada** via the NREL Alternative Fuel Data Center (AFDC) Alternative Fuel Stations API — the same backend that powers the [AFDC Station Locator](https://afdc.energy.gov/stations) and NRCan’s locator.

| | |
|---|---|
| **API** | [Alt-Fuel Stations v1](https://developer.nrel.gov/docs/transportation/alt-fuel-stations-v1/) |
| **Nearest URL** | `https://developer.nlr.gov/api/alt-fuel-stations/v1/nearest.json` (alias of `developer.nrel.gov`) |
| **Auth** | Query `api_key=` — free signup at [developer.nrel.gov/signup](https://developer.nrel.gov/signup/) |
| **Env / local.properties** | `NREL_AFDC_KEY` |
| **Gaston types** | `AfdcAvailabilityClient`, `AfdcAvailabilityProvider` |
| **Regions** | `ParkingRegion.UnitedStates`, `ParkingRegion.Canada` |

Without `NREL_AFDC_KEY`, the client returns an empty list (no crash) and the factory falls back to Eco-Movement when configured.

## Status mapping

AFDC exposes **station-level** `status_code` (not full OCPI per-EVSE realtime):

| `status_code` | Gaston `AvailabilityStatus` |
|---------------|-----------------------------|
| `E` | Available |
| `T` | Maintenance (temporarily unavailable) |
| `P` | PlannedIntoService |

Queries use `fuel_type=ELEC`, `access=public`, `country=all` (US+CA), radius converted from km → miles.

## Wiring

```kotlin
AfdcAvailabilityClient(get(), apiKey = BuildConfig.NREL_AFDC_KEY, country = "all")
// factory: Canada / UnitedStates → afdcProvider ?: ecoMovementProvider
```

## Caveat

Many networks sync into AFDC on a delay. Federally funded (NEVI) networks must also expose their **own** OCPI 2.2.1 APIs — not wired here (per-network tokens). See [`EV_AVAILABILITY_ROADMAP_NON_EU.md`](EV_AVAILABILITY_ROADMAP_NON_EU.md).
