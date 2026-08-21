# QualiCharge IRVE dynamique

Optional real-time EV charging-point availability for **mainland France** (outside the Belib Paris zone), from QualiCharge open data via [transport.data.gouv.fr](https://transport.data.gouv.fr).

## Feature flag

- **Setting:** `AppSettings.dynamicIrveEnabled` (prefs key `dynamic_irve_enabled`)
- **Default:** `false` (experimental; downloads large national CSV feeds)
- **Enable:** Settings → Developer / Debug → **IRVE dynamique (QualiCharge)**

When the flag is off, [BorneAvailabilityProviderFactory](../shared/src/commonMain/kotlin/fr/geoking/gaston/api/belib/BorneAvailabilityProviderFactory.kt) never returns the QualiCharge provider (no network calls).

## Feeds (no API key)

| Feed | URL |
|------|-----|
| Dynamique | `https://proxy.transport.data.gouv.fr/resource/qualicharge-irve-dynamique` |
| Statique (lat/lon + station id join) | `https://proxy.transport.data.gouv.fr/resource/qualicharge-irve-statique` |

Dynamique schema (MVP): `id_pdc_itinerance`, `etat_pdc` (`en_service` / `hors_service` / `inconnu`), `occupation_pdc` (`libre` / `occupe` / `reserve` / `inconnu`), `horodatage`.

## In the codebase

- **Client:** `shared/.../api/qualicharge/QualiChargeDynamiqueClient.kt` — CSV fetch/parse, short cache (~45s dynamic, ~1h static)
- **Provider:** `QualiChargeAvailabilityProvider` implements `BorneAvailabilityProvider`
- **Factory:** Belib wins inside Paris bbox; QualiCharge elsewhere in FR when the flag is on
- **Matching:** by `id_station_itinerance` / `IrveDetails.pdcIds`, then distance (same as Belib)

See also [BELIB_AVAILABILITY_API.md](BELIB_AVAILABILITY_API.md).
