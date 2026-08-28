# QualiCharge IRVE dynamique

Real-time EV charging-point availability for **mainland France**, from QualiCharge open data via [transport.data.gouv.fr](https://transport.data.gouv.fr).

Always on when the map is centered in mainland France. In Paris, Belib is merged as a secondary source (PDCs not already present from QualiCharge).

## Feeds (no API key)

| Feed | URL |
|------|-----|
| Dynamique | `https://proxy.transport.data.gouv.fr/resource/qualicharge-irve-dynamique` |
| Statique (lat/lon + station id join) | `https://proxy.transport.data.gouv.fr/resource/qualicharge-irve-statique` |

Dynamique schema (MVP): `id_pdc_itinerance`, `etat_pdc` (`en_service` / `hors_service` / `inconnu`), `occupation_pdc` (`libre` / `occupe` / `reserve` / `inconnu`), `horodatage`.

## In the codebase

- **Client:** `shared/.../api/qualicharge/QualiChargeDynamiqueClient.kt` — CSV fetch/parse, short cache (~45s dynamic, ~1h static)
- **Provider:** `QualiChargeAvailabilityProvider` implements `BorneAvailabilityProvider`
- **Factory:** QualiCharge for mainland France; in Paris, `MergedBorneAvailabilityProvider(QualiCharge, Belib)`
- **Matching:** by `id_station_itinerance` / `IrveDetails.pdcIds`, then distance (same as Belib)

Listed in Settings → About / sources (`UsedApisList`) and [`sources.md`](sources.md).

See also [BELIB_AVAILABILITY_API.md](BELIB_AVAILABILITY_API.md).
