# Portugal EV stations + availability

Pragmatic status for **Portugal (PT)**: national AFIR data is aggregated by **Mobi.E as EADME** and published toward the **IMT National Access Point (PAN)**. Open DATEX II pull URLs now exist on the NAP, but they are **national bulk XML dumps** unsuitable for direct mobile viewport fetches. Use **Eco-Movement OCPI** for stations and live EVSE status when `ECO_MOVEMENT_KEY` is set until a filtered/streaming national consumer is viable.

## Recommended approach (Gaston today)

| Need | Source | Auth |
|------|--------|------|
| EV locations (POI) | Eco-Movement OCPI 2.2.1 | `ECO_MOVEMENT_KEY` |
| EVSE availability | Same Eco-Movement locations (EVSE `status`) | same key |
| National NAP (AFIR) | MOBI.E DATEX II on IMT NAP — **not wired in-app** | None for pull (open URLs); see sizes below |

Country routing already treats PT like other EU fallbacks: when the map center is outside FR/BE specialty feeds, [BorneAvailabilityProviderFactory](../shared/src/commonMain/kotlin/fr/geoking/gaston/api/belib/BorneAvailabilityProviderFactory.kt) returns Eco-Movement. Fuel POIs remain **DGEG** (`PortugalDgegProvider`); EV is separate.

Code marker (no DATEX network): `shared/.../api/portugal/PortugalEcoMovementAvailabilityNote.kt` and optional wrapper `PortugalEcoMovementAvailabilityProvider`.

## Probe results (2026-08-29)

### IMT NAP catalogue (metadata API)

| | |
|---|---|
| **Portal** | [nap-portugal.imt-ip.pt](https://nap-portugal.imt-ip.pt/nap/home) |
| **Metadata API** | `https://nap-portugal.imt-ip.pt/API/api/` (Angular SPA `baseUrl`) |
| **Static supply** | Multimodal supply **#148** — *Dados estáticos da Rede de Postos de Carregamento Portuguesa (Rede MOBI.E)* |
| **Dynamic supply** | Multimodal supply **#149** — *Dados dinâmicos … estado da infraestrutura* |
| **Owner** | MOBI.E S.A (`pedro.santos@mobie.pt`) |
| **Licence (catalogue)** | “No licence – No contract” / *Livre acesso* |
| **Claimed update** | Up to 5 min |
| **Declared format** | DATEX II, syntax JSON in metadata — **actual payload is XML** |

### Open DATEX pull URLs (no API key)

| Layer | URL | Probe |
|-------|-----|-------|
| **Static** | `https://ev-nap.mobie.pt/integration/nap/evChargingInfra` | HTTP **200**, `application/xml`, **~191 MB** (`EnergyInfrastructureTablePublication`) |
| **Status** | `https://ev-nap.mobie.pt/integration/nap/evActualStatus` | HTTP **200**, `application/xml`, **~41 MB** (`EnergyInfrastructureStatusPublication`) |

Sample status fragment (ids match static site/station refs; `<ns3:status>available</ns3:status>` observed). CORS header allows `https://pgm.mobie.pt` only (browsers); raw HTTPS GET from apps/servers works without auth.

### Partner OCPI hub (not for third-party apps)

| | |
|---|---|
| **Versions** | `https://pgm.mobie.pt/ocpi/hub/versions/` |
| **Probe** | HTTP **401** — `UnauthorizedError: Missing authentication header` |
| **Docs** | [MOBI.E OCPI Phase 2](https://www.mobie.pt/documents/699315/699782/20230620_MOBIE_OCPI_Phase2_Internal_v1_6.pdf/fdb22f6a-3ffc-529d-8f9f-d27706875de7) — CPO/CEME credentials exchange |

### Other probes (negative / not national)

| Target | Result |
|--------|--------|
| `api.mobie.pt` / `ocpi.mobie.pt` / `eadme.mobie.pt` / `data.mobie.pt` | DNS fail |
| [MOBI.Data](https://mobie.pt/mobilidade/mobi.data) | HTML portal only — no documented machine bulk API |
| [dados.gov.pt](https://dados.gov.pt) `AFIR` / `EADME` | No national AFIR dump; Lisbon/local map services & stats only |
| Road NAP `informationTypes` | RTTI / SRTI / SSTP only — **no AFIR EV type** on the road map layer |

## Why not a thin DATEX client yet

1. **Bulk size** — ~191 MB static + ~41 MB status per full refresh; unacceptable for phone/Auto viewport polls (contrast Belgium’s smaller OCPI JSON dumps).
2. **No shared DATEX AFIR parser** in `:shared` yet (Germany Mobilithek path also deferred for DATEX + mTLS).
3. **No bbox/filter query** on `ev-nap.mobie.pt` — only full-nation publications.
4. EADME [Regra Técnica 1/EADME/2026](https://www.mobie.pt/documents/699315/699782/Regra+T%C3%A9cnica+1_EADME_18052026.pdf/28f8cc57-7748-ef63-099b-840de7103d5f) is **OPC→EADME** (DATEX hyperlinks or OCPI 2.2/2.2.1), with transitional period through **2026-12-31** — consumer NAP publication is maturing but still dump-shaped.

A backend proxy that caches DATEX and serves radius queries could unlock a national provider later; that is out of scope for this package.

## Eco-Movement vs MOBI.E / IMT NAP

| | Eco-Movement OCPI | MOBI.E DATEX via IMT NAP |
|---|-------------------|---------------------------|
| Protocol | OCPI 2.2.1 JSON | DATEX II AFIR energy-infrastructure (XML) |
| Coverage | EU/global commercial catalogue (includes PT) | National Rede MOBI.E static + status |
| Gaston today | Yes (`EcoMovementOcpiClient` / `EcoMovementAvailabilityProvider`) | Catalogue + open URLs documented; **not wired** |
| Auth model | Token header (`ECO_MOVEMENT_KEY`) | None for pull |
| Mobile fit | Radius-friendly OCPI locations | Full-country dumps |

Belgium contrast: open Road Public Charging Network dump — [`BELGIUM_NAP_AVAILABILITY.md`](BELGIUM_NAP_AVAILABILITY.md). Portugal’s open feeds are DATEX dumps, not OCPI JSON.

## Contacts / links

| Contact / link | Use |
|----------------|-----|
| [IMT NAP Portugal](https://nap-portugal.imt-ip.pt/nap/home) | Catalogue UI; supplies #148 / #149 |
| NAP metadata API | `POST …/MultimodalSupplies/Search`, `GET …/MultimodalSupplies/{id}` |
| [MOBI.Data](https://mobie.pt/mobilidade/mobi.data) | Citizen/stats portal (not a bulk API) |
| [Regra Técnica 1/EADME/2026](https://www.mobie.pt/documents/699315/699782/Regra+T%C3%A9cnica+1_EADME_18052026.pdf/28f8cc57-7748-ef63-099b-840de7103d5f) | OPC→EADME DATEX/OCPI obligations |
| [Technical rules (MOBI.E)](https://www.mobie.pt/instalarpostos/regras-tecnicas-procedimentos) | OCPI partner docs; `mic@mobie.pt` for `[OCPI]` |
| `pedro.santos@mobie.pt` | NAP supply owner contact |
| `tsalgado@imt-ip.pt` | NAP catalogue contact point |
| **partners@eco-movement.com** / [developers.eco-movement.com](https://developers.eco-movement.com) | OCPI Data API token (`ECO_MOVEMENT_KEY`) — current Gaston path for PT |

## Wiring snippets (not applied — parallel-agent ownership)

Do **not** commit these from the Portugal-only package work; apply in a follow-up that owns factory / DI / docs index.

### Optional explicit Portugal branch in `BorneAvailabilityProviderFactory`

Today PT already hits Eco-Movement via `else`. To make PT explicit (and use the thin wrapper):

```kotlin
// BorneAvailabilityProviderFactory constructor: add
private val portugalEcoMovementProvider: BorneAvailabilityProvider? = null,

// getProvider():
return when (ParkingRegion.containing(latitude, longitude)) {
    ParkingRegion.Belgium -> belgiumNapProvider ?: ecoMovementProvider
    ParkingRegion.Portugal -> portugalEcoMovementProvider ?: ecoMovementProvider
    ParkingRegion.France -> { /* existing Paris / QualiCharge logic */ }
    else -> ecoMovementProvider
}
```

### MapModule DI

```kotlin
import fr.geoking.gaston.api.portugal.PortugalEcoMovementAvailabilityProvider

// Inside BorneAvailabilityProviderFactory single { ... }:
val eco = if (BuildConfig.ECO_MOVEMENT_KEY.isBlank()) {
    null
} else {
    EcoMovementAvailabilityProvider(get(), radiusKm = 15, limit = 200)
}
BorneAvailabilityProviderFactory(
    belibProvider = get(named("belib")),
    qualiChargeProvider = get(named("qualicharge")),
    belgiumNapProvider = get(named("belgium_nap")),
    portugalEcoMovementProvider = eco?.let { PortugalEcoMovementAvailabilityProvider(it) },
    ecoMovementProvider = eco,
)
```

### `sources.md` row (when updating the catalogue)

| **Eco‑Movement (OCPI)** | Portugal (among EU) | EV + availability | `open-chargepoints.com` … | `ECO_MOVEMENT_KEY` — preferred for PT until filtered DATEX/NAP; see [`PORTUGAL_EV_AVAILABILITY.md`](PORTUGAL_EV_AVAILABILITY.md) |

### Future MOBI.E DATEX consumer (out of scope)

Only when product accepts: a cache/proxy (or official filtered API), DATEX II AFIR energy-infrastructure parse, and id matching (`EZC-…` / `AMD-00051-1`-style refs) to map POIs. Then add `shared/.../api/portugal/MobieDatex*Client` + `BorneAvailabilityProvider` and prefer it over Eco-Movement for `ParkingRegion.Portugal`.

## In the codebase (this deliverable)

- **Note + wrapper:** `shared/.../api/portugal/PortugalEcoMovementAvailabilityNote.kt`
- **Doc:** this file
- **No** DATEX HTTP client (bulk size / no shared parser)
- **No** unit tests (no parsing code)
- **Not** wired in factory / MapModule / `sources.md` / European coverage tests (owned by other agents)
