# Poland EIPA EV availability (UDT)

Real-time EVSE availability for **Poland**, using the free EIPA reader JSON dumps
([Ewidencja Infrastruktury Paliw Alternatywnych](https://eipa.udt.gov.pl/) — Urząd Dozoru Technicznego).

## Source used

| | |
|---|---|
| **Portal / docs** | [eipa.udt.gov.pl/reader/docs](https://eipa.udt.gov.pl/reader/docs) |
| **Register (free)** | [eipa.udt.gov.pl/reader/register](https://eipa.udt.gov.pl/reader/register) — personal export key + hourly rate limits |
| **Feed** | Custom JSON dumps: `station` + `point` + `dynamic` |
| **URL pattern** | `https://eipa.udt.gov.pl/reader/export-data/{resource}/{exportKey}` |
| **Resources** | `dynamic`, `point`, `station` (also `pool`, `operator`, `dictionary` — unused here) |
| **API key** | Export key in the path. Default = public map-reader key shipped in client; override with `EIPA_EXPORT_KEY` |

```bash
# Probe (default public map key)
KEY=cc00241029ceddb4013bf2e166193882
curl -s "https://eipa.udt.gov.pl/reader/export-data/dynamic/$KEY" | head -c 400
curl -s "https://eipa.udt.gov.pl/reader/export-data/point/$KEY" | head -c 400
curl -s "https://eipa.udt.gov.pl/reader/export-data/station/$KEY" | head -c 400
```

Verified 2026-08-29: `dynamic` ~14.5k rows with `status`, `generated` fresh; join to `point`/`station` works for Warsaw EVSEs.

### `dynamic.json` status (electric only)

| Field | Values | Meaning |
|-------|--------|---------|
| `status.availability` | `1` / `0` | Operationally available / not available |
| `status.status` | `1` / `0` | Free (wolny) / occupied (zajęty) |

App mapping: `availability=0` → Maintenance; `availability=1` + `status=1` → Available; `availability=1` + `status=0` → Occupied.

IDs prefer EIPA `code` (e.g. `PL-GJC-EEVP01295`); station id = numeric `station.id`.

## Gaps / auth notes

- Reader dumps require an export key (registration is free). The default key is the one used by the official EIPA map; prefer a personal key if rate-limited.
- Dumps are national (~MB each). Client caches ~3 minutes and radius-filters after join.
- Gas / H2 points have no `status` and are skipped.
- Optional `pool.json` (address street/house) is **not** fetched; address uses `station.location` city + province.

## In the codebase

- **Client:** `shared/.../api/poland/EipaAvailabilityClient.kt` — fetch/parse/cache, join, radius filter, status map
- **Provider:** `EipaAvailabilityProvider` implements `BorneAvailabilityProvider`
- **Tests:** `shared/.../api/poland/EipaAvailabilityTest.kt` (fixtures; no live network)

## Wiring recommendation (do not apply here — owned by integrator)

### ParkingRegion gap

**`ParkingRegion` has no Poland entry today.** Until a bbox exists, factory routing cannot select EIPA by `ParkingRegion.containing`. Options:

1. **Add region** (preferred when wiring):

```kotlin
// ParkingRegion.kt — illustrative only (do not edit from Poland agent)
Poland(
    latMin = 49.00,
    latMax = 54.84,
    lonMin = 14.07,
    lonMax = 24.15,
    countryCode = "PL",
),
```

Rough mainland Poland bbox (Warsaw ~52.23, 21.01). Place it so it does not steal neighbours (DE/CZ/SK/LT already have regions — check enum order / `containing` priority).

2. **Interim:** leave PL on Eco-Movement fallback until `ParkingRegion.Poland` exists.

### Factory + Koin

```kotlin
// BorneAvailabilityProviderFactory — illustrative only
ParkingRegion.Poland -> eipaProvider ?: ecoMovementProvider
```

```kotlin
// MapModule — illustrative only
single {
    EipaAvailabilityClient(
        get(),
        exportKey = getProperty("EIPA_EXPORT_KEY", EipaAvailabilityClient.DEFAULT_EXPORT_KEY),
    )
}
single { EipaAvailabilityProvider(get(), radiusKm = 15, limit = 200) }
// pass eipaProvider = get() into BorneAvailabilityProviderFactory when that constructor grows
```

Suggested `local.properties` / env (optional override):

```properties
EIPA_EXPORT_KEY=...
```

Also list EIPA in Settings → About / [`sources.md`](sources.md) when wiring lands. Do **not** hardcode secrets beyond documenting the public map key already used by EIPA’s own reader UI.
