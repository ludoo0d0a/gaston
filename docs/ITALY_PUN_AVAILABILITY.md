# Italy PUN EV availability (Piattaforma Unica Nazionale)

Real-time-ish EVSE status for **Italy**, using a publicly queryable ArcGIS FeatureServer that historically backed the [PUN](https://www.piattaformaunicanazionale.it/) map. Not a Belgium-style OCPI locations dump.

## Probe results (2026-08-29, readonly curl)

| Endpoint | Result |
|---|---|
| `https://services2.arcgis.com/pROHh69WvVijk4nR/.../IdR_latest_ready/FeatureServer/0` (ondata legacy) | **499 Token Required** |
| `…/usrsvcs/servers/7fc26f462fb040f09538cc76162b5766/.../PdR_latest_ready` (ondata “URL_nuovo”) | **400** item inaccessible |
| `…/usrsvcs/servers/2e7cacf0bba54e17ae9f1f6d41961391/.../PdR_latest_ready` | **403** no permission |
| Experience-embedded `…/0a7de59eac154f248408fd7a281b3611/.../PdR_latest_new` | **403** no permission |
| **`…/695bae597e5c4346b9c2f5923d88749d/.../PdR_latest_new`** (portal title `PdR_latest_new_public`) | **OK** — `count≈48916`, Query capability, no token |
| `https://www.piattaformaunicanazionale.it/api` / `/api/locations` | SPA HTML fallback (not a JSON API) |
| Open Rome planning layers (`IdR2023`, `IdR_Roma`, …) | Public but **not** national live status |

**Freshness caveat:** on the working layer, `max(Data_ultimo_aggiornamento) ≈ 2024-09-04` and `max(Data_di_caricamento) ≈ 2024-09-17` at probe time. Status field `Stato` still has a full OCPI-like mix (`AVAILABLE` ~42k, `CHARGING` ~2k, `OUTOFORDER` ~3k, …), but timestamps look **frozen**. Prefer **Eco-Movement OCPI** for live availability until PUN timestamps move again.

[ondata/rete_ricarica_veicoli_elettrici](https://github.com/ondata/rete_ricarica_veicoli_elettrici) notes the same: PUN changed publication; their extraction is no longer applicable.

## Source used (no API key)

| | |
|---|---|
| **Platform** | [PUN — Piattaforma Unica Nazionale](https://www.piattaformaunicanazionale.it/) (MASE / GSE / RSE) |
| **Citizen map** | https://www.piattaformaunicanazionale.it/ (and `/idr` SPA routes) |
| **Feed** | ArcGIS FeatureServer layer `PdR_latest_new` via public utility proxy |
| **Query URL** | `https://utility.arcgis.com/usrsvcs/servers/695bae597e5c4346b9c2f5923d88749d/rest/services/PdR_latest_new/FeatureServer/0/query` |
| **Portal item** | GSE org `gse-sta.maps.arcgis.com`, title `PdR_latest_new_public`, item id `695bae597e5c4346b9c2f5923d88749d` |
| **API key** | None (anonymous Query) |
| **Auth** | None for this proxy; other proxies / `IdR_latest_ready` need ArcGIS token |
| **Filter** | Attribute bbox on `Latitudine_EVSE` / `Longitudine_EVSE`, then haversine radius |
| **Status field** | `Stato` (OCPI-like strings) |
| **IDs** | Prefer `ID_EVSE` (e.g. `IT*ENX*E…`), fallback `ID_univoco_EVSE`; station = `ID_location` |

`usrsvcs` proxy UUIDs **rotate**. If Query starts returning 403/400, re-discover via GSE portal search for `PdR_latest_new_public` or by inspecting network calls from the PUN / Experience map — do not invent endpoints.

## Gaps vs OCPI / Belgium NAP

| | Belgium NAP (Road.io dump) | Italy PUN ArcGIS | Eco-Movement OCPI |
|---|---|---|---|
| Format | OCPI locations JSON dump | ArcGIS feature attributes | OCPI 2.2.1 locations |
| Auth | None | None (this proxy) | `ECO_MOVEMENT_KEY` |
| Live status | Embedded per EVSE | `Stato` present; **timestamps may be stale** | Designed for live roaming status |
| National coverage | Selected Road/E-Flux CPOs | PUN-registered PdR (~49k rows when probed) | Commercial roaming footprint |
| AFIR NAP dump | Registered on transportdata.be | No free OCPI/DATEX pull found for IT | Often used as AFIR intermediary |

## AFIR / Italian NAP context

- **AFIR Art. 20:** CPOs must publish static + dynamic charging data via each member state’s NAP (OCPI and/or DATEX II depending on national specs).
- **PUN** is Italy’s institutional register/map for charging points (citizen UI + CPO upload; news on the site mentions an API for **CPO acquisition**, not a public open dump).
- **Transport NAP:** [nap.mit.gov.it](https://nap.mit.gov.it/) / [CCISS](https://www.cciss.it/) — no Belgium-style open EVSE JSON dump found during probe; Spirii and others list Italy’s NAP method as unclear / evolving.
- **Practical availability source for Gaston in IT today:** Eco-Movement OCPI (`ECO_MOVEMENT_KEY`), with optional PUN ArcGIS as a no-key complement when its timestamps are fresh.

## In the codebase

- **Client:** `shared/.../api/italy/ItalyPunAvailabilityClient.kt` — bbox query, pagination (`maxRecordCount` 2000), ~60s cache, radius filter
- **Provider:** `ItalyPunAvailabilityProvider` implements `BorneAvailabilityProvider`
- **Tests:** `shared/.../api/italy/ItalyPunAvailabilityTest.kt` (parser / filter / status; no live network)

Status mapping (OCPI-like `Stato` → app): via shared `OcpiEvseAvailability` — `AVAILABLE` → Available, `CHARGING`/`BLOCKED` → Occupied, `OUTOFORDER`/`INOPERATIVE` → Maintenance, `REMOVED` skipped.

## Wiring recommendation (do not apply here — owned by another parallel task)

`BorneAvailabilityProviderFactory` currently routes non-FR/BE to Eco-Movement. For Italy, prefer **Eco-Movement first** while PUN timestamps lag; keep PUN as fallback when Eco-Movement is unset:

```kotlin
// BorneAvailabilityProviderFactory — illustrative only
ParkingRegion.Italy -> ecoMovementProvider ?: italyPunProvider
// or, once PUN Data_ultimo_aggiornamento is fresh again:
// ParkingRegion.Italy -> italyPunProvider ?: ecoMovementProvider
```

Koin (`MapModule`):

```kotlin
single { ItalyPunAvailabilityClient(get()) }
single { ItalyPunAvailabilityProvider(get()) }
// pass italyPunProvider = get() into BorneAvailabilityProviderFactory when that constructor grows
```

Also list PUN in Settings → About / [`sources.md`](sources.md) when wiring lands.
