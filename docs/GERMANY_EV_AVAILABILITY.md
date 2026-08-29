# Germany EV stations + availability

Pragmatic status for **Germany (DE)**: there is **no** Belgium-style free national OCPI dump. Use **Eco-Movement OCPI** for stations and live EVSE status when `ECO_MOVEMENT_KEY` is set. A direct **Mobilithek** DATEX consumer is **not** implemented (mTLS + org registration).

## Recommended approach (Gaston today)

| Need | Source | Auth |
|------|--------|------|
| EV locations (POI) | Eco-Movement OCPI 2.2.1 | `ECO_MOVEMENT_KEY` |
| EVSE availability | Same Eco-Movement locations (EVSE `status`) | same key |
| National NAP (AFIR) | Mobilithek DATEX II — **not wired** | mTLS client cert + subscription |

Country routing already treats DE like other EU fallbacks: when the map center is outside FR/BE specialty feeds, [BorneAvailabilityProviderFactory](../shared/src/commonMain/kotlin/fr/geoking/gaston/api/belib/BorneAvailabilityProviderFactory.kt) returns Eco-Movement. POI merge includes Eco-Movement for DE via [AutoPoiProviderResolver](../shared/src/commonMain/kotlin/fr/geoking/gaston/poi/AutoPoiProviderResolver.kt).

Code marker (no network): `shared/.../api/germany/GermanyEcoMovementAvailabilityNote.kt` and optional wrapper `GermanyEcoMovementAvailabilityProvider`.

## Mobilithek (German NAP) — why not a thin client yet

| | |
|---|---|
| **Portal** | [mobilithek.info](https://mobilithek.info) |
| **Role** | Federal NAP (BMDV / T-Systems); successor to MDM |
| **EV data** | Per-CPO **offers** (static + dynamic), AFIR Art. 20 |
| **Payload** | DATEX II (XML; v3 also JSON) — [AFIR-DATEX-II-Recharging-Profil](https://github.com/MobilithekDE/AFIR-DATEX-II-Recharging-Profil) |
| **Pull URL pattern** | `https://mobilithek.info:8443/mobilithek/api/V1.0/subscription?subscriptionID=<id>` (and SOAP/OCIT-C variants) |
| **Auth** | **mTLS**: X.509 client certificate issued by Mobilithek after organisation / machine-account registration — **not** `Authorization: Bearer` / API key |
| **Open pull without cert?** | **No.** Unauthenticated broker calls fail at TLS handshake or with 403/404; there is no public anonymous dump URL comparable to Belgium’s Road/E-Flux JSON |

DATEX becomes the mandatory exchange format on Mobilithek for AFIR from **2026-04-14** (see NOW GmbH / Nationale Leitstelle Ladeinfrastruktur announcements). Until then (and after), consumers still need a registered subscription + cert.

### Auth barriers (checklist)

1. Register as **data consumer** on Mobilithek admin UI.
2. Submit CSR / receive **PKCS#12** (`.p12`) machine-account bundle; present cert on every TLS handshake.
3. Subscribe to each relevant **CPO offer** (static + dynamic); obtain **subscription ID(s)**.
4. Implement DATEX II AFIR energy-infrastructure parse + map to `PdcAvailability` / POI models.
5. On Android/KMP: ship or securely store client certs (non-trivial for a Play Store app; often done via backend proxy).

A fake “open DATEX URL” client would be dishonest — none was found that returns live national EVSE status without credentials.

## Eco-Movement vs Mobilithek

| | Eco-Movement OCPI | Mobilithek DATEX |
|---|-------------------|------------------|
| Protocol | OCPI 2.2.1 JSON | DATEX II AFIR profile |
| Coverage | EU/global commercial catalogue (includes DE) | Per-CPO national offers via NAP |
| Gaston today | Yes (`EcoMovementOcpiClient` / `EcoMovementAvailabilityProvider`) | No |
| Auth model | Token header (`ECO_MOVEMENT_KEY`) | mTLS + subscription |
| Docs | [`API_KEYS.md`](API_KEYS.md#eco-movement-ev-eu--global-ocpi-221) | This page + Mobilithek CMS interface docs |

Belgium contrast: open Road Public Charging Network dump — [`BELGIUM_NAP_AVAILABILITY.md`](BELGIUM_NAP_AVAILABILITY.md). Germany has no equivalent free dump on Mobilithek.

## Contacts / links

| Contact / link | Use |
|----------------|-----|
| [mobilithek.info](https://mobilithek.info) | Browse offers, register as consumer, subscription admin |
| Mobilithek support / help in portal | Certificate issuance, machine accounts, broker access |
| [AFIR DATEX II Recharging Profil (GitHub)](https://github.com/MobilithekDE/AFIR-DATEX-II-Recharging-Profil) | Schema / profile for static + dynamic recharging data |
| [Nationale Leitstelle Ladeinfrastruktur](https://nationale-leitstelle.de/) | AFIR Art. 20 guidance for DE |
| [NOW GmbH AFIR DATEX announcement](https://www.now-gmbh.de/en/news/pressreleases/implementation-of-article-20-afir-datex-2-data-profile-now-ready-for-use/) | Profile readiness + DATEX mandate timeline |
| **partners@eco-movement.com** / [developers.eco-movement.com](https://developers.eco-movement.com) | OCPI Data API token (`ECO_MOVEMENT_KEY`) — current Gaston path for DE |

## Wiring snippets (not applied — parallel-agent ownership)

Do **not** commit these from the Germany-only package work; apply in a follow-up that owns factory / DI / docs index.

### Optional explicit Germany branch in `BorneAvailabilityProviderFactory`

Today DE already hits Eco-Movement via `else`. To make DE explicit (and use the thin wrapper):

```kotlin
// BorneAvailabilityProviderFactory constructor: add
private val germanyEcoMovementProvider: BorneAvailabilityProvider? = null,

// getProvider():
return when (ParkingRegion.containing(latitude, longitude)) {
    ParkingRegion.Belgium -> belgiumNapProvider ?: ecoMovementProvider
    ParkingRegion.Germany -> germanyEcoMovementProvider ?: ecoMovementProvider
    ParkingRegion.France -> { /* existing Paris / QualiCharge logic */ }
    else -> ecoMovementProvider
}
```

### MapModule DI

```kotlin
import fr.geoking.gaston.api.germany.GermanyEcoMovementAvailabilityProvider

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
    germanyEcoMovementProvider = eco?.let { GermanyEcoMovementAvailabilityProvider(it) },
    ecoMovementProvider = eco,
)
```

### `sources.md` row (when updating the catalogue)

| **Eco‑Movement (OCPI)** | Germany (among EU) | EV + availability | `open-chargepoints.com` … | `ECO_MOVEMENT_KEY` — preferred for DE until Mobilithek DATEX; see [`GERMANY_EV_AVAILABILITY.md`](GERMANY_EV_AVAILABILITY.md) |

### Future Mobilithek consumer (out of scope)

Only when product accepts: org registration, cert lifecycle (ideally a small backend puller), DATEX AFIR XML/JSON parser, and subscription IDs per CPO offer. Then add `shared/.../api/germany/Mobilithek*Client` + `BorneAvailabilityProvider` and prefer it over Eco-Movement for `ParkingRegion.Germany`.

## In the codebase (this deliverable)

- **Note + wrapper:** `shared/.../api/germany/GermanyEcoMovementAvailabilityNote.kt`
- **Doc:** this file
- **No** Mobilithek HTTP client (auth barrier)
- **No** unit tests (no parsing code)
- **Not** wired in factory / MapModule / `sources.md` / European coverage tests (owned by other agents)
