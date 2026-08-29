# Spain EV stations + availability

Pragmatic status for **Spain (ES)** (probed **2026-08-29**): **high product demand**, but **no** Belgium-style free national OCPI / JSON dump for **live** EVSE status that a third-party app can pull without CPO credentials. Use **Eco-Movement OCPI** for availability (and EV locations) when `ECO_MOVEMENT_KEY` is set until REVE/SGV or the DGT NAP publish a usable consumer status API.

## Probe result (honest)

| Source | What it is | Open consumer pull for **availability**? |
|--------|------------|------------------------------------------|
| **SGV** (Red Eléctrica) | System that collects dynamic data from CPOs | **No** — OCPI 2.1.1 / 2.2.1 is **CPO → SGV push** (Locations, Tariffs, EVSE `status`). Credentials via PASOS client portal for **operators**, not MSP apps. |
| **REVE** | Public map / apps built on SGV + RIPREE static | **No** — [mapareve.es](https://www.mapareve.es/) (and App Store / Play apps) for humans. No documented REST/OCPI pull, bulk download, or developer API. |
| **RIPREE** (MITECO) | Static registry obligation for CPOs | **No** simple bulk/status API for third-party apps; citizen/map products + CPO inscription portal. |
| **DGT NAP** | ITS National Access Point | **Static only** today — DATEX II v3 table pull exists; **no** public status publication found. |
| **datos.gob.es** request | [Puntos de carga VE](https://datos.gob.es/es/solicitud-de-datos/puntos-de-carga-de-vehiculos-electricos) (open since 2021) | Still **assigned** / unresolved as open CSV-or-API from REVE; community notes map-only publication. |

### DGT NAP DATEX (static — do not treat as availability)

| | |
|---|---|
| **Catalogue** | [nap.dgt.es — Puntos de recarga eléctrica](https://nap.dgt.es/dataset/puntos-de-recarga-electrica-para-vehiculos) |
| **Pull URL** | `https://infocar.dgt.es/datex2/v3/miterd/EnergyInfrastructureTablePublication/electrolineras.xml` |
| **Type** | `EnergyInfrastructureTablePublication` (DATEX II v3) |
| **Category** | Metadata: **Static road data**; update frequency **24h** |
| **Auth** | Anonymous HTTPS GET works (HTTP 200; large XML, ~100 MB class) |
| **Contents (sampled)** | Sites / `ElectricChargingPoint` inventory (name, address, coords, operator, power/connectors) — **not** live occupied/available |
| **Status sibling** | Probed paths under `…/EnergyInfrastructureStatusPublication/…` → **404** |

A DATEX **status** feed (or OCPI eMSP-style dump) would be the unlock for a national `BorneAvailabilityProvider`. The static table alone is **stations inventory**, not availability — do **not** fake status from it.

### SGV / REVE — why no thin client yet

Official procedure (SEE PDF / [BOE res. 2025-04-02](https://www.boe.es/eli/es/res/2025/04/02/(1))):

- CPOs register in **RIPREE**, then integrate **SGV** via **PASOS** ([portalclientes.ree.es](https://www.portalclientes.ree.es/)).
- Protocol: **OCPI** REST JSON; CPO is sender of locations + tariffs + EVSE status (≥43 kW mandatory dynamic; lower power often static-only on the map).
- Downstream “access” in the procedure is described for **CNMC**, **JCT (NAP)**, and **MITERD apps** via *documentos específicos* — **not** a published anonymous app pull URL.
- Public UX: **REVE** web + mobile — not a documented open API.

Scraping mapareve.es or reverse-engineering the app is out of scope and would be fragile/ToS-risk; this deliverable does **not** invent endpoints.

## Recommended approach (Gaston today)

| Need | Source | Auth |
|------|--------|------|
| EV locations (POI) | Eco-Movement OCPI 2.2.1 (optional: OCM) | `ECO_MOVEMENT_KEY` |
| EVSE availability | Same Eco-Movement locations (EVSE `status`) | same key |
| National dynamic (SGV/REVE) | **Not wired** — no public pull | CPO / institutional only |
| National static (DGT DATEX) | Optional future **stations-only** POI — **not** availability | None (large XML) |

Country routing already treats ES like other EU fallbacks: when the map center is outside FR/BE specialty feeds, `BorneAvailabilityProviderFactory` returns Eco-Movement. Fuel stations in ES remain Minetur (separate from EV availability).

Code marker (no network): `shared/.../api/spain/SpainEcoMovementAvailabilityNote.kt` and optional wrapper `SpainEcoMovementAvailabilityProvider`.

## Eco-Movement vs official ES stack

| | Eco-Movement OCPI | SGV / REVE | DGT DATEX table |
|---|-------------------|------------|-----------------|
| Role | Commercial EU catalogue | National dynamic + map | Static inventory dump |
| Live status | Yes (EVSE `status`) | Yes **inside** REVE UI | **No** |
| Gaston today | Yes (`EcoMovementAvailabilityProvider`) | No | No |
| Auth | Token (`ECO_MOVEMENT_KEY`) | CPO PASOS / institutional | Open GET (static) |

Belgium contrast: open Road Public Charging Network dump — [`BELGIUM_NAP_AVAILABILITY.md`](BELGIUM_NAP_AVAILABILITY.md). Spain has no equivalent free **status** dump.

## Contacts / links

| Link | Use |
|------|-----|
| [mapareve.es](https://www.mapareve.es/) | Public map / apps (no pull API) |
| [REE — operador puntos de recarga](https://www.ree.es/es/clientes/operador-punto-de-recarga-vehiculo-electrico) | SGV / CPO onboarding overview |
| [REE clientes](https://www.ree.es/es/clientes) | Procedure PDFs, PASOS guides |
| [BOE Resolución 2025-04-02](https://www.boe.es/eli/es/res/2025/04/02/(1)) | Legal/tech frame for dynamic remisión to SGV (OCPI) |
| [SEE procedimiento SGV (PDF)](https://www.ree.es/sites/default/files/12_CLIENTES/Documentos/Resolucion_SEE_procedimiento_SGV.pdf) | OCPI versions, flows, NAP = JCT |
| [MITECO RIPREE](https://www.miteco.gob.es/es/energia/hidrocarburos-nuevos-combustibles/sitio-web-de-informacion-al-ciudadano-con-vehiculo-con-motor-ele/ripree.html) | Static registry / citizen info |
| [DGT NAP dataset](https://nap.dgt.es/dataset/puntos-de-recarga-electrica-para-vehiculos) | Static DATEX pull metadata |
| [datos.gob.es open-data request](https://datos.gob.es/es/solicitud-de-datos/puntos-de-carga-de-vehiculos-electricos) | Track if REVE data ever opens |
| **partners@eco-movement.com** / [developers.eco-movement.com](https://developers.eco-movement.com) | OCPI token (`ECO_MOVEMENT_KEY`) — **current** Gaston path for ES |
| Roadmap | [`EV_AVAILABILITY_ROADMAP.md`](EV_AVAILABILITY_ROADMAP.md#spain-es) |

## Recommended next step

1. **Ship Eco-Movement** for `ParkingRegion.Spain` (explicit factory branch optional; `else` already covers ES).
2. **Re-probe** when AFIR DATEX II consumer publication hardens (**2026-04-14** mandate wave): look for a DGT/MITERD **`EnergyInfrastructureStatusPublication`** (or OCPI dump) with live occupancy — not only the existing static table.
3. Watch REVE / REE developer docs and the datos.gob.es request; if a free authenticated or anonymous **status** pull appears, implement `SpainReve*` / NAP client + `BorneAvailabilityProvider` and prefer it over Eco-Movement for ES (keep Eco-Movement as fallback).

## Wiring snippets (not applied — parallel-agent ownership)

Do **not** commit these from the Spain-only package work; apply in a follow-up that owns factory / DI / docs index.

### Optional explicit Spain branch in `BorneAvailabilityProviderFactory`

Today ES already hits Eco-Movement via `else`. To make ES explicit (and use the thin wrapper):

```kotlin
// BorneAvailabilityProviderFactory constructor: add
private val spainEcoMovementProvider: BorneAvailabilityProvider? = null,

// getProvider():
return when (ParkingRegion.containing(latitude, longitude)) {
    ParkingRegion.Belgium -> belgiumNapProvider ?: ecoMovementProvider
    ParkingRegion.Spain -> spainEcoMovementProvider ?: ecoMovementProvider
    ParkingRegion.France -> { /* existing Paris / QualiCharge logic */ }
    else -> ecoMovementProvider
}
```

### MapModule DI

```kotlin
import fr.geoking.gaston.api.spain.SpainEcoMovementAvailabilityProvider

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
    spainEcoMovementProvider = eco?.let { SpainEcoMovementAvailabilityProvider(it) },
    ecoMovementProvider = eco,
)
```

### `sources.md` row (when updating the catalogue)

| **Eco‑Movement (OCPI)** | Spain (among EU) | EV + availability | `open-chargepoints.com` … | `ECO_MOVEMENT_KEY` — preferred for ES until REVE/SGV/NAP status pull; see [`SPAIN_EV_AVAILABILITY.md`](SPAIN_EV_AVAILABILITY.md) |

## In the codebase (this deliverable)

- **Note + wrapper:** `shared/.../api/spain/SpainEcoMovementAvailabilityNote.kt`
- **Doc:** this file
- **No** REVE/SGV HTTP client (no public pull API)
- **No** DGT DATEX availability parser (static-only feed)
- **No** unit tests (no parsing code)
- **Not** wired in factory / MapModule / `sources.md` / European coverage tests (owned by other agents)
