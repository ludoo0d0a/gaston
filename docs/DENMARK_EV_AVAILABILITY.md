# Denmark EV stations + availability

Pragmatic status for **Denmark (DK)**: there is **no** Belgium-style free national OCPI dump. Use **Eco-Movement OCPI** for stations and live EVSE status when `ECO_MOVEMENT_KEY` is set. A direct **Dataudveksleren** DATEX consumer is **not** implemented (portal login + servicekonto). Klimadatastyrelsen’s aggregated realtime programme is CPO-facing / TBD for apps.

## Recommended approach (Gaston today)

| Need | Source | Auth |
|------|--------|------|
| EV locations (POI) | Eco-Movement OCPI 2.2.1 | `ECO_MOVEMENT_KEY` |
| EVSE availability | Same Eco-Movement locations (EVSE `status`) | same key |
| National NAP (AFIR) | Dataudveksleren DATEX II / CPO reference APIs — **not wired** | MitID Erhverv / eID login + **servicekonto** (Basic) |
| Aggregated national programme | Klimadatastyrelsen “Ladepunktsdata i realtid” — **no open pull docs** | CPO OCPI in; consumer API TBD |

Country routing already treats DK like other EU fallbacks: when the map center is outside FR/BE specialty feeds, [BorneAvailabilityProviderFactory](../shared/src/commonMain/kotlin/fr/geoking/gaston/api/belib/BorneAvailabilityProviderFactory.kt) returns Eco-Movement. `ParkingRegion.Denmark` already exists.

Code marker (no network): `shared/.../api/denmark/DenmarkEcoMovementAvailabilityNote.kt` and optional wrapper `DenmarkEcoMovementAvailabilityProvider`.

Fuel stations in DK remain separate: Fuelprices.dk (`FUELPRICES_DK_KEY`) under the same `api/denmark/` package.

## Dataudveksleren (Danish NAP) — why not a thin client yet

| | |
|---|---|
| **Portal** | [nap.vd.dk](https://nap.vd.dk/) → [du-portal-ui.dataudveksler.app.vd.dk](https://du-portal-ui.dataudveksler.app.vd.dk/) |
| **Operator** | Vejdirektoratet (Danish Road Directorate) |
| **Role** | National Access Point for mobility / AFIR Art. 20 energy infrastructure |
| **EV data** | Per-CPO **metadata** entries (often “Reference til data”) + some DATEX II publications; Eco-Movement also publishes an **aggregated** DK DATEX set |
| **Payload** | DATEX II (XML; AFIR energy-infrastructure / CEN/TS 16157-10 from **2026-04-14**) |
| **Pull URL pattern** | `https://distribution.dataudveksler.app.vd.dk/api/dataset/{id}/latest/DatexII` |
| **Auth** | **Logged-in download** or **servicekonto** credentials for machine HTTP — **not** anonymous |
| **Open pull without account?** | **No.** Unauthenticated distribution calls return **HTTP 401** (probed for dataset ids including aggregated chargepoints). “Offentligt” on the catalogue means metadata visibility / eligible for registered users — not a Belgium-style anonymous dump |

AFIR guide on the portal: CPOs register a dataset with **Reference til data** pointing at their API; they do **not** upload bulk files to the NAP. From **2026-04-14**, referenced APIs must expose DATEX II (IR (EU) 2025/655). See [Færdselsstyrelsen DATEX news](https://www.fstyr.dk/nyheder/2025/dec/afir-operatoerer-skal-sikre-data-tilgaengeliggoerelse-i-det-europaeiske-format-datex-ii-fra-den-14-april-2026).

### Aggregated DATEX on the NAP (Eco-Movement)

| | |
|---|---|
| **Title** | Chargepoints in Denmark DATEXII |
| **Catalogue** | [data/950/overview](https://du-portal-ui.dataudveksler.app.vd.dk/data/950/overview) |
| **Publisher** | Eco-Movement (`nap@eco-movement.com`) |
| **Content** | Multi-CPO DK charge points (DATEX II v3); includes Allego, Circle K, Clever, Fastned, … |
| **Gaston note** | Same commercial party as `ECO_MOVEMENT_KEY` OCPI — prefer the **existing OCPI client** over a second DATEX stack for the same catalogue |

Per-CPO DATEX examples also appear (e.g. OK a.m.b.a. DATEXII). Many other listings are OCPI “reference” connections that still need bilateral handshake (e.g. Spirii).

### Auth barriers (checklist)

1. Create a Dataudveksleren account: **MitID Erhverv** (DK org) or **non-Danish eID** ([guides](https://du-portal-ui.dataudveksler.app.vd.dk/guides)).
2. Create a **servicekonto** (username + one-time password) under Profil → servicekonti.
3. Subscribe / attach the service account to the target dataset(s); obtain download/HTTP access from the dataset page.
4. Call `distribution.dataudveksler.app.vd.dk` with Basic (or portal session) auth; parse DATEX II AFIR recharging profile → `PdcAvailability` / POI models.
5. On Android/KMP: store servicekonto secrets securely (often via backend proxy) — not suitable as a hard-coded Play Store client secret.

A fake “open DATEX URL” client would be dishonest — none was found that returns live national EVSE status without credentials.

## Klimadatastyrelsen — “Ladepunktsdata i realtid”

| | |
|---|---|
| **Programme** | National mapping / realtime charge-point data with Færdselsstyrelsen |
| **CPO path** | Integrate via **OCPI** into Klimadatastyrelsen’s IT solution (voluntary vs NAP DATEX obligation) |
| **Law** | Bekendtgørelse on AFIR data: Klimadatastyrelsen **shall provide an API for data users** (§ 6) and collects/distributes Art. 20 data (§ 9) — see [lovtidende PDF](https://www.lovtidende.dk/api/pdf/249159) / [retsinformation PDF](https://www.retsinformation.dk/api/pdf/253795) |
| **Consumer docs** | **Not** published as a documented free bulk REST/OCPI dump for third-party apps (as of this note). No Belgium/CH-style URL. Programme described in [Fstyr DATEX news](https://www.fstyr.dk/nyheder/2025/dec/afir-operatoerer-skal-sikre-data-tilgaengeliggoerelse-i-det-europaeiske-format-datex-ii-fra-den-14-april-2026) (“Ladepunktsdata i realtid”). |
| **AFIR note** | CPO→KDS OCPI does **not** replace the duty to publish DATEX II API info on the NAP |

Until KDS publishes stable open consumer endpoints (URL, auth, format), do not implement a dedicated Gaston client.

## Eco-Movement vs NAP DATEX vs KDS

| | Eco-Movement OCPI | Dataudveksleren DATEX | KDS Ladepunktsdata |
|---|-------------------|------------------------|--------------------|
| Protocol | OCPI 2.2.1 JSON | DATEX II (XML) | OCPI in (CPO); consumer TBD |
| Coverage | EU/global commercial (includes DK) | Per-CPO + Eco-Movement aggregated DK offer | Intended national aggregate |
| Gaston today | Yes (`EcoMovementOcpiClient` / `EcoMovementAvailabilityProvider`) | No | No |
| Auth model | Token header (`ECO_MOVEMENT_KEY`) | Portal + servicekonto | Undocumented for apps |
| Docs | [`API_KEYS.md`](API_KEYS.md#eco-movement-ev-eu--global-ocpi-221) | This page + [DU guides](https://du-portal-ui.dataudveksler.app.vd.dk/guides) | Fstyr news / bekendtgørelse |

Belgium contrast: open Road Public Charging Network dump — [`BELGIUM_NAP_AVAILABILITY.md`](BELGIUM_NAP_AVAILABILITY.md). Denmark has no equivalent free dump.

## Contacts / links

| Contact / link | Use |
|----------------|-----|
| [nap.vd.dk](https://nap.vd.dk/) / [Dataudveksleren portal](https://du-portal-ui.dataudveksler.app.vd.dk/) | Browse AFIR datasets, register, servicekonto, subscribe |
| [DU guides](https://du-portal-ui.dataudveksler.app.vd.dk/guides) | Login, AFIR metadata tips, how to access data |
| **vd@vd.dk** | Dataudveksleren technical support |
| [Færdselsstyrelsen AFIR](https://www.danishroadtrafficauthority.dk/afir) | CPO ID registration, API guidance |
| **info@fstyr.dk** | AFIR regulation questions |
| [DATEX mandate news (DA)](https://www.fstyr.dk/nyheder/2025/dec/afir-operatoerer-skal-sikre-data-tilgaengeliggoerelse-i-det-europaeiske-format-datex-ii-fra-den-14-april-2026) | 2026-04-14 DATEX II deadline + “Ladepunktsdata i realtid” |
| [Bekendtgørelse (PDF)](https://www.retsinformation.dk/api/pdf/253795) | KDS consumer API obligation (§ 6) + CPO API registration |
| [OCPI guidance for CPOs](https://www.danishroadtrafficauthority.dk/publications/guidance-for-operators-of-recharging-points) | OCPI ↔ AFIR field mapping (operator-facing) |
| [Chargepoints DK DATEXII (Eco-Movement)](https://du-portal-ui.dataudveksler.app.vd.dk/data/950/overview) | Aggregated NAP DATEX listing |
| **nap@eco-movement.com** / [developers.eco-movement.com](https://developers.eco-movement.com) | NAP DATEX contact + OCPI Data API (`ECO_MOVEMENT_KEY`) — current Gaston path for DK |
| [IR (EU) 2025/655](https://eur-lex.europa.eu/eli/reg_impl/2025/655/oj) | DATEX II AFIR implementing regulation |

## Wiring snippets (not applied — parallel-agent ownership)

Do **not** commit these from the Denmark-only package work; apply in a follow-up that owns factory / DI / docs index.

### Optional explicit Denmark branch in `BorneAvailabilityProviderFactory`

Today DK already hits Eco-Movement via `else`. To make DK explicit (and use the thin wrapper):

```kotlin
// BorneAvailabilityProviderFactory constructor: add
private val denmarkEcoMovementProvider: BorneAvailabilityProvider? = null,

// getProvider():
return when (ParkingRegion.containing(latitude, longitude)) {
    ParkingRegion.Belgium -> belgiumNapProvider ?: ecoMovementProvider
    ParkingRegion.Denmark -> denmarkEcoMovementProvider ?: ecoMovementProvider
    ParkingRegion.France -> { /* existing Paris / QualiCharge logic */ }
    else -> ecoMovementProvider
}
```

### MapModule DI

```kotlin
import fr.geoking.gaston.api.denmark.DenmarkEcoMovementAvailabilityProvider

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
    denmarkEcoMovementProvider = eco?.let { DenmarkEcoMovementAvailabilityProvider(it) },
    ecoMovementProvider = eco,
)
```

### `sources.md` row (when updating the catalogue)

| **Eco‑Movement (OCPI)** | Denmark (among EU) | EV + availability | `open-chargepoints.com` … | `ECO_MOVEMENT_KEY` — preferred for DK until Dataudveksleren DATEX / KDS consumer API; see [`DENMARK_EV_AVAILABILITY.md`](DENMARK_EV_AVAILABILITY.md) |

### Future NAP / KDS consumer (out of scope)

Only when product accepts: org eID registration, servicekonto lifecycle (ideally a small backend puller), DATEX AFIR XML parser (share with DE Mobilithek once proven), and/or documented KDS consumer endpoints. Then add `shared/.../api/denmark/Dataudveksleren*Client` or `Klimadatastyrelsen*Client` + `BorneAvailabilityProvider` and prefer it over Eco-Movement for `ParkingRegion.Denmark`.

## In the codebase (this deliverable)

- **Note + wrapper:** `shared/.../api/denmark/DenmarkEcoMovementAvailabilityNote.kt`
- **Doc:** this file
- **No** Dataudveksleren / DATEX HTTP client (auth barrier; 401 without servicekonto)
- **No** Klimadatastyrelsen client (no public consumer pull docs)
- **No** unit tests (no parsing code)
- **Not** wired in factory / MapModule / `sources.md` / European coverage tests (owned by other agents)
