# NOBIL EV stations + availability (Norway / Sweden)

NOBIL (Enova + Norwegian EV Association) is the Nordic public charging-station database. Gaston uses the **shared** `NobilClient` for country dumps; Norway wires availability via `NobilAvailabilityProvider` (`countrycode=NOR`). **Sweden** reuses the same client with `SWE` (Sweden agent / follow-up wiring).

| | |
|---|---|
| **Site** | [info.nobil.no/english](https://info.nobil.no/english) |
| **API docs** | [API_NOBIL_Documentation_v3](https://info.nobil.no/files/API_NOBIL_Documentation_v3_20250827.pdf) |
| **License** | Creative Commons Attribution (register and accept terms) |
| **Coverage** | Norway + Sweden (DK/FI/IS dropped from NOBIL) |

## API key

| | |
|---|---|
| **Env / local.properties** | `NOBIL_API_KEY` |
| **Auth** | Query parameter `apikey=` on datadump / search |
| **Register** | [info.nobil.no/api](https://info.nobil.no/api) — name, email, mobile, website; key by email (often within two working days) |

Without a key, `NobilClient` returns empty lists (no network call).

NOBIL’s terms ask that the raw API not be exposed directly to end users (cache / intermediate processing). Gaston keeps the key server-side / build-time and caches the dump in memory.

## Endpoints

### Static / dump (used now)

```
GET https://nobil.no/api/server/datadump.php
  ?apikey=<NOBIL_API_KEY>
  &countrycode=NOR   # or SWE
  &format=json
  &file=false
```

Optional: `fromdate=YYYY-MM-DD` for delta dumps. Server-side cache for static data is ~1 hour.

Search helpers (rectangle / near / by id) live on `https://nobil.no/api/server/search.php` with `apiversion=3` — not required for the current client.

### Real-time (follow-up)

Enova WebSocket stream — [info.nobil.no/sanntid](https://info.nobil.no/sanntid):

1. Sign up at [data.enova.no](https://data.enova.no) (test: `test.data.enova.no`)
2. Subscribe to product **NOBIL Real-time** → separate API key (not `NOBIL_API_KEY`)
3. `GET https://data.enova.no/real-time/v1/Realtime` with header `x-api-key` → `wss://…` URL
4. Messages: `{ "nobilId": "NOR_23314", "evseUId": "…", "status": "AVAILABLE" }` (OCPI-like)

Suggested env name for later: `NOBIL_REALTIME_API_KEY`. A long-lived WebSocket + state store does not fit cleanly in KMP without a backend; leave as follow-up. Until then, Gaston maps connector status from the **v3 dump** when present (`attr` connector status / error, or OCPI strings in `attrval`).

Statuses: `AVAILABLE`, `CHARGING`, `BLOCKED`, `OUTOFORDER`, `INOPERATIVE`, `RESERVED`, `PLANNED`, `REMOVED`, `UNKNOWN` — mapped via `OcpiEvseAvailability` (legacy Vacant / Busy → Available / Occupied).

## In the codebase

| | |
|---|---|
| **Client** | `shared/.../api/nobil/NobilClient.kt` — datadump fetch/parse/cache (~15 min), `countryCode` NOR/SWE |
| **Provider** | `NobilAvailabilityProvider` implements `BorneAvailabilityProvider` |
| **Tests** | `shared/.../api/nobil/NobilAvailabilityTest.kt` (fixture JSON, no live key) |

## Wiring snippets (do not apply in this PR unless asked)

`BorneAvailabilityProviderFactory` / `MapModule` / `sources.md` are owned elsewhere. Suggested Norway wiring:

```kotlin
// DI (e.g. MapModule) — build-time key from local.properties / env
val nobilApiKey = localProperties["NOBIL_API_KEY"].orEmpty()
val nobilNorClient = NobilClient(httpClient, apiKey = nobilApiKey, countryCode = NobilClient.DEFAULT_COUNTRY_NOR)
val nobilNorAvailability = NobilAvailabilityProvider(nobilNorClient)

// Sweden (same client class):
val nobilSweClient = NobilClient(httpClient, apiKey = nobilApiKey, countryCode = NobilClient.COUNTRY_SWE)
val nobilSweAvailability = NobilAvailabilityProvider(nobilSweClient)
```

```kotlin
// BorneAvailabilityProviderFactory.getProvider — route by ParkingRegion
when (ParkingRegion.containing(latitude, longitude)) {
    ParkingRegion.Norway -> nobilNorAvailability ?: ecoMovementProvider
    ParkingRegion.Sweden -> nobilSweAvailability ?: ecoMovementProvider
    // …
}
```

`ParkingRegion.Norway` / `ParkingRegion.Sweden` already exist (`countryCode` `NO` / `SE`). NOBIL ISO-639-2 codes are `NOR` / `SWE`.
