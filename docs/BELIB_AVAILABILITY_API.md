# Belib (Paris Data) – Borne availability API

Gaston uses **Paris Data** (Opendatasoft) as a **secondary** real-time availability source for Belib' charging points when the map is centered on Paris. QualiCharge is the primary source for all of mainland France (including Paris); Belib only adds PDCs not already returned by QualiCharge.

## No API key required

The Belib availability API is **open data**. You do **not** need to register or use an API key.

- **Dataset:** [Belib' – Points de recharge – Disponibilité temps réel](https://parisdata.opendatasoft.com/explore/dataset/belib-points-de-recharge-pour-vehicules-electriques-disponibilite-temps-reel/)
- **API base:** `https://parisdata.opendatasoft.com/api/explore/v2.1/catalog/datasets/belib-points-de-recharge-pour-vehicules-electriques-disponibilite-temps-reel`
- **Console / docs:** [Paris Data API (v2.1)](https://opendata.paris.fr/api/explore/v2.1/console)

There is no “register API key” step on the Paris Data console; public read access works without authentication. If you hit rate limits in the future, Paris Data would document that separately.

## In the codebase

- **Client:** `shared/.../api/belib/BelibAvailabilityClient.kt` – calls the API with no API key or auth headers.
- **Provider:** `BelibAvailabilityProvider` + `MergedBorneAvailabilityProvider` via `BorneAvailabilityProviderFactory` in the Paris bbox.
- Primary nationwide: QualiCharge IRVE dynamique — see [`IRVE_DYNAMIQUE.md`](IRVE_DYNAMIQUE.md).
