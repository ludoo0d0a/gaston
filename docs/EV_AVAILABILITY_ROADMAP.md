# EV stations + live availability — European roadmap

Practical roadmap for **stations + live EVSE availability** in European countries **not** already covered by the current parallel workstream. Goal: prefer national open (or free-registration) NAP feeds over commercial fallbacks where Gaston can pull reliably.

**Related:** [`sources.md`](sources.md) · [`API_KEYS.md`](API_KEYS.md#eco-movement-ev-eu--global-ocpi-221) · [`BELGIUM_NAP_AVAILABILITY.md`](BELGIUM_NAP_AVAILABILITY.md) · [`IRVE_DYNAMIQUE.md`](IRVE_DYNAMIQUE.md) · factory routing via [`BorneAvailabilityProviderFactory`](../shared/src/commonMain/kotlin/fr/geoking/gaston/api/belib/BorneAvailabilityProviderFactory.kt) + [`ParkingRegion`](../shared/src/commonMain/kotlin/fr/geoking/gaston/parking/ParkingRegion.kt)

---

## Already done / in progress (do not re-scope here)

| Country | Status | Source (short) |
|---------|--------|----------------|
| **FR** | Done | QualiCharge IRVE dynamique + Belib (Paris) |
| **BE** | Done | Belgium NAP Road/E-Flux OCPI dump ([transportdata.be](https://transportdata.be/dataset/road-public-charging-network)) |
| **NL** | In progress | DOT-NL / NDW OCPI |
| **DE** | In progress | Mobilithek DATEX / Eco-Movement |
| **IT** | In progress | PUN |
| **NO / SE** | In progress | NOBIL |
| **LU** | Stations + fallback | Chargy (stations) + Eco-Movement availability |
| **GB** | Stations (+ network OCPI) | char.gy OCPI + Fastned UK OCPI |
| **Global fallback** | Always | Eco-Movement OCPI (`ECO_MOVEMENT_KEY`), Open Charge Map |

---

## Summary — remaining countries

Legend for pull columns: **Yes** = documented consumer/open pull today · **Partial** = registry/portal or free registration with gaps · **No** = CPO→NAP only, map UI only, or unknown public API.

Priorities: **P0** = open no-key (or trivial) + high reuse of existing OCPI/JSON patterns · **P1** = free registration NAP or high traffic · **P2** = DATEX/heavy or unclear consumer API · **P3** = Eco-Movement / OCM only for now.

| Country | NAP / official source | Format | Stations open pull? | Availability open pull? | Auth | Priority | Notes / URLs |
|---------|----------------------|--------|---------------------|-------------------------|------|----------|--------------|
| **CH** | BFE / ich-tanke-strom (opentransportdata.swiss) | Custom JSON (OICP-inspired) | Yes | Yes | None (OGD; commercial use may need BFE OK) | **P0** | Static + status dumps: [opendata.swiss ladestationen](https://opendata.swiss/en/dataset/ladestationen); docs [SFOE GitHub](https://github.com/SFOE/ichtankestrom_Documentation); map [ich-tanke-strom.ch](https://www.ich-tanke-strom.ch). See [§ Switzerland](#switzerland-ch) |
| **FI** | Fintraffic Digitraffic AFIR | GeoJSON + DATEX II + MQTT | Yes | Yes | None | **P0** | [`digitraffic.fi` AFIR](https://www.digitraffic.fi/en/road-traffic/afir/); CPO onboarding via [Fintraffic AFIR](https://www.fintraffic.fi/en/digitalservices/afir) |
| **AT** | E-Control Ladestellenverzeichnis (+ NAP [mobilitydata.gv.at](https://mobilitydata.gv.at/en)) | Custom REST + DATEX II | Yes | Yes | Free API key + Referer (Basic for some ops) | **P0** | Public map [ladestellen.at](https://www.ladestellen.at); tech [e-control.at tech info](https://www.e-control.at/ladestellenverzeichnis-technische-informationen); NAP dataset listing |
| **PL** | EIPA (UDT) | Custom JSON dumps | Yes | Yes | Free reader registration + rate limits | **P1** | [`eipa.udt.gov.pl/reader/docs`](https://eipa.udt.gov.pl/reader/docs) — `point.json` + `dynamic.json` (availability + ad-hoc prices) |
| **ES** | MITECO RIPREE + REE SGV / mapa **REVE** | CPO→SGV: OCPI 2.1.1/2.2.1; consumer: map UI | Partial | Partial | CPO credentials for OCPI push; **no documented public pull API** | **P1** | Strong product value; use Eco-Movement until open API. [mapareve.es](https://www.mapareve.es/), [MITECO RIPREE](https://www.miteco.gob.es/es/energia/hidrocarburos-nuevos-combustibles/sitio-web-de-informacion-al-ciudadano-con-vehiculo-con-motor-ele/ripree.html). See [§ Spain](#spain-es) |
| **DK** | Vejdirektoratet Dataudveksleren ([nap.vd.dk](https://nap.vd.dk/)) + Klimadatastyrelsen “Ladepunktsdata i realtid” | OCPI (aggregator) → DATEX II mandatory **2026-04-14** | Partial | Partial | CPO self-service; consumer API TBD / Eco-Movement | **P1** | [Færdselsstyrelsen AFIR](https://www.danishroadtrafficauthority.dk/afir); DATEX deadline news [fstyr.dk](https://www.fstyr.dk/nyheder/2025/dec/afir-operatoerer-skal-sikre-data-tilgaengeliggoerelse-i-det-europaeiske-format-datex-ii-fra-den-14-april-2026) |
| **PT** | Mobi.E as **EADME** → IMT NAP (PAN) | OCPI 2.2/2.2.1 or DATEX II (CPO→EADME) | Partial | Partial | Portal / no clear free bulk API for apps | **P1** | [MOBI.Data](https://mobie.pt/mobilidade/mobi.data); DL 93/2025; Regra Técnica 1/EADME/2026. See [§ Portugal](#portugal-pt) |
| **IE** | TII Data Exchange Platform (DXP) → [data.gov.ie](https://data.gov.ie) NAP | OCPI 2.2.1 (CPO→DXP) | Partial | Partial | CPO onboarding; consumer API via NAP when published | **P1** | [TII DXP](https://www.tii.ie/en/roads-tolling/alt-fuel-projects-unit/alt-fuels-data-office/register-dxp/); contact `AFIRdata@tii.ie` |
| **CZ** | NAP [registr.dopravniinfo.cz](http://registr.dopravniinfo.cz/en/) | DATEX / registry metadata | No | No | Provider registration | **P2** | Expect Eco-Movement until AFIR DATEX feeds are openly listed |
| **SK** | National ITS NAP (NAPCORE / national portal) | DATEX II (AFIR path) | No | No | Typically registration | **P2** | Eco-Movement / OCM default |
| **HU** | [napportal.kozut.hu](https://napportal.kozut.hu/) | DATEX / NAP catalogue | No | No | Registration | **P2** | Eco-Movement / OCM |
| **RO** | [pna.cestrin.ro](https://pna.cestrin.ro/) | DATEX / NAP | No | No | Registration | **P2** | Eco-Movement / OCM |
| **BG** | National NAP (ITS) | DATEX / catalogue | No | No | Registration | **P3** | Eco-Movement / OCM |
| **GR** | [nap.gov.gr](https://nap.gov.gr/) | NAP catalogue | No | No | Registration | **P3** | Eco-Movement / OCM; fuel already via FuelGR |
| **HR** | [prometinfo.hr](https://www.prometinfo.hr/) NAP | DATEX / traffic NAP | No | No | Registration | **P3** | Eco-Movement / OCM |
| **SI** | NAP.si / National Traffic Management Centre | OCPI or REST (CPO→NAP) | No | No | Registration | **P2** | Fuel via goriva.si; EV → Eco-Movement until open pull confirmed |
| **EE / LV / LT** | National NAPs (Baltic ITS) | DATEX / OCPI per MS | No | No | Registration | **P3** | Small markets; Eco-Movement / OCM |
| **IS** | No EU AFIR NAP | — | No | No | — | **P3** | Eco-Movement / OCM only |
| **AD / MC / SM / VA** | Microstates | — | No | No | — | **P3** | Use neighbouring FR/IT/ES coverage + Eco-Movement |
| **RS / BA / ME / MK / AL / XK** | Non-EU / limited AFIR | — | No | No | — | **P3** | Eco-Movement / OCM; ME/MK already have `ParkingRegion` boxes for other POIs |

EU catalogue of ITS NAPs (background): [EC NAP list PDF](https://transport.ec.europa.eu/document/download/963c997d-efd9-40ae-a38b-5d4b935bdfcf_en?filename=its-national-access-points.pdf). AFIR data rules: [IR (EU) 2025/655](https://eur-lex.europa.eu/eli/reg_impl/2025/655/oj).

---

## Phased roadmap

Recommend order: **open no-key OCPI/JSON (like NL/BE) → free API-key NAPs → DATEX/heavy registries → Eco-Movement-only**.

### Phase A — open dumps / no-key APIs (next after NL/DE/IT/NO/SE)

| Order | Country | Why |
|-------|---------|-----|
| 1 | **CH** | Same shape as Belgium: bulk static + status JSON, no key; `ParkingRegion.Switzerland` already exists |
| 2 | **FI** | Digitraffic REST (+ optional MQTT) for locations + statuses; no key; clear docs |

**Verify:** radius-filtered client + `BorneAvailabilityProvider` + factory branch for that `ParkingRegion` + host tests + row in `sources.md` (+ short country doc if non-obvious).

### Phase B — free registration / national APIs

| Order | Country | Why |
|-------|---------|-----|
| 3 | **AT** | E-Control charge API used by many apps; free key; DATEX also on NAP |
| 4 | **PL** | EIPA reader dumps include live availability; free account + rate limits |
| 5 | **IE** | Watch TII DXP publication on data.gov.ie; OCPI-shaped if exposed |
| 6 | **DK** | Prefer Klimadatastyrelsen aggregated feed if/when consumer access is documented; else stay on Eco-Movement until DATEX NAP pulls are usable |

### Phase C — high value but blocked / DATEX-heavy

| Order | Country | Why |
|-------|---------|-----|
| 7 | **ES** | Highest Iberian demand; **blocked on public pull** — track REVE/SGV open API; interim Eco-Movement |
| 8 | **PT** | Track EADME→IMT NAP consumer endpoints; interim Eco-Movement + existing station POIs if any |
| 9 | **CZ / SK / HU / RO / SI** | Revisit after Apr 2026 DATEX II wave: many NAPs may finally expose uniform energy-infrastructure publications |

### Phase D — Eco-Movement / OCM only (until evidence of open pull)

BG, GR, HR, Baltics, IS, Balkans, microstates — keep global fallback; no dedicated national provider unless a free dump appears.

---

## Definition of “done” (per country)

A country NAP availability integration is **done** when all of the following are true:

1. **Provider** — `BorneAvailabilityProvider` (or merged) with client, short cache, status mapping to app enums (same spirit as QualiCharge / Belgium NAP / `OcpiEvseAvailability`).
2. **Factory + region** — `BorneAvailabilityProviderFactory` routes via `ParkingRegion.containing` (add/adjust bbox if missing); Eco-Movement remains fallback when national provider null or unconfigured.
3. **Tests** — commonTest coverage for parse/status mapping + factory routing for that region (see existing Belgium / Ocpi / EuropeanCities factory tests).
4. **Docs** — entry in [`sources.md`](sources.md); optional short `docs/<COUNTRY>_….md` when auth/endpoints need explanation (pattern: Belgium, IRVE).
5. **About / UsedApis** — listed in Settings sources when user-facing.

Stations-only providers (locations without live status) do **not** count as availability “done”; they may still ship as POI providers.

---

## Cross-cutting

### Eco-Movement as default

- With `ECO_MOVEMENT_KEY`, Eco-Movement OCPI is the **default availability** for every region without a dedicated national provider (current factory `else` branch).
- **Prefer national NAP** when: (a) open or free-registration pull, (b) better coverage or fresher status than Eco-Movement for that country, (c) no commercial dependency for that geography.
- Keep Eco-Movement as **fallback** even after a national provider ships (Belgium already does this if NAP provider is null).

### When national NAP wins

| Prefer national | Stay on Eco-Movement |
|-----------------|----------------------|
| Free bulk/dump like BE/CH/FI | Only CPO→NAP push, no consumer API (ES today) |
| Documented status semantics + ids we can match to POIs | Auth is sales-only / opaque DATEX without sample |
| Rate limits acceptable for map viewport fetches | Coverage of the open feed is a small CPO subset and Eco-Movement is denser |

### AFIR + DATEX II (Apr 2026)

- **AFIR Art. 20** — from **2025-04-14**, CPOs must make static + dynamic data available free of charge via the Member State NAP.
- **Implementing Regulation (EU) 2025/655** — from **2026-04-14**, those data must follow the **DATEX II** energy-infrastructure model (CEN/TS 16157-10:2022 and successors). Many NAPs currently accept **OCPI** as an interim CPO upload path and convert centrally (FI Fintraffic, PT EADME transitional rules, etc.).
- **Gaston impact:** after Apr 2026, expect more **DATEX pull** endpoints on NAPs (Mobilithek-style). Invest in a shared DATEX/EnergyInfrastructure parser once DE (in progress) proves the pattern; reuse for DK/AT/others rather than one-off OCPI clones everywhere.
- Until then, **OCPI JSON dumps and national JSON APIs** remain the fastest path (BE, CH, FI, PL, IE).

### Matching & POIs

National availability feeds only help if Gaston can **match** EVSEs to map POIs (`id_station_itinerance` / OCPI ids / distance — see QualiCharge/Belib matching). When adding a country, note id strategy in the country doc.

---

## Spain (ES)

Spain is a **P1 product target** with a **P2/P3 engineering reality** until a consumer API exists.

| Layer | What exists | Open for Gaston? |
|-------|-------------|------------------|
| **Static registry** | **RIPREE** (MITECO) — CPO obligation for static data | Citizen/map products; no simple bulk API documented for third-party apps |
| **Dynamic** | **SGV** run by **Red Eléctrica** — CPOs push OCPI 2.1.1 / 2.2.1 (Locations, Tariffs, status) | CPO→SGV only; procedure PDFs on [REE clientes](https://www.ree.es/es/clientes) / [BOE res. 2025-04-02](https://www.boe.es/eli/es/res/2025/04/02/(1)) |
| **Public UX** | **REVE** map — [mapareve.es](https://www.mapareve.es/) — static + dynamic (availability, prices) for a large share of public points (≥43 kW mandatory dynamic; lower power often static-only) | **Map/app for humans**, not a documented open pull API |

**Gaps**

- No Belgium/CH-style dump URL for apps.
- NAP role for DGT / traffic NAP vs energy SGV split is administrative; neither currently exposes a clear free REST dump for MSP clients.
- AFIR DATEX II may force a NAP publication path by **2026-04-14** — re-check then.

**Gaston plan**

1. Keep **Eco-Movement** (and OCM for stations) as ES availability/stations until an official pull API is published.
2. Periodically probe REVE/SGV/MITECO for open data or NAP DATEX listings.
3. If a pull API appears, treat like QualiCharge: join static inventory + dynamic status, match by official point ids.

---

## Portugal (PT)

Portugal centralises AFIR data through **Mobi.E as EADME**, which aggregates CPO data and forwards to the **IMT National Access Point (PAN)** (Decreto-Lei n.º 93/2025; Portaria on EADME↔PAN).

| Layer | What exists | Open for Gaston? |
|-------|-------------|------------------|
| **CPO → EADME** | DATEX II XML hyperlinks (no auth) **or** OCPI 2.2 / 2.2.1 (Credentials, Locations, Tariffs) | Operator-facing ([Regra Técnica 1/EADME/2026](https://www.mobie.pt/documents/699315/699782/Regra+T%C3%A9cnica+1_EADME_18052026.pdf/28f8cc57-7748-ef63-099b-840de7103d5f)) |
| **Citizen / stats** | [MOBI.Data](https://mobie.pt/mobilidade/mobi.data) portal | Interactive portal; **not** a documented machine bulk API for third parties |
| **PAN (IMT)** | Legal NAP for AFIR publication | Consumer API URL / format for apps **not clearly published** as of this roadmap |

**Gaps**

- Transitional period (through end-2026 in technical rules): many OPCs stay on the legacy Mobi.E platform while EADME converts to DATEX for the NAP — good for compliance, unclear for Gaston pull.
- No confirmed free OCPI eMSP-style dump analogous to char.gy / Belgium Road.

**Gaston plan**

1. Eco-Movement availability until IMT/Mobi.E publish a stable open pull (DATEX or OCPI locations+status).
2. Contact path for clarification: Mobi.E / EADME technical contacts in Regra Técnica; watch IMT NAP catalogue.
3. If DATEX links are published **without auth** (as required of OPCs upstream), prefer those over scraping MOBI.Data.

---

## Switzerland (CH)

Best **Phase A** candidate after the current in-progress set: open government data with **stations + live availability**, similar operational model to Belgium’s dump.

| | |
|---|---|
| **Programme** | National electromobility data infrastructure (**ich-tanke-strom**) — BFE / EnergieSchweiz |
| **NAP / portal** | [opentransportdata.swiss](https://opentransportdata.swiss) / [opendata.swiss dataset](https://opendata.swiss/en/dataset/ladestationen) |
| **Static JSON** | `https://data.geo.admin.ch/ch.bfe.ladestellen-elektromobilitaet/data/ch.bfe.ladestellen-elektromobilitaet.json` |
| **Availability JSON** | `https://data.geo.admin.ch/ch.bfe.ladestellen-elektromobilitaet/status/ch.bfe.ladestellen-elektromobilitaet.json` |
| **Format** | OICP-inspired / custom JSON (not OCPI 2.2.1) — map statuses explicitly in a small client |
| **Auth** | None for download; licence **O-By-Ask** — attribute source; **commercial use may need BFE permission** ([EnergieSchweiz](https://www.energieschweiz.ch/ladeinfrastruktur/werkzeuge/ich-tanke-strom/)) |
| **Docs** | [SFOE/ichtankestrom_Documentation](https://github.com/SFOE/ichtankestrom_Documentation) |

**Gaps**

- Not full OCPI — cannot reuse `OcpiEvseAvailability` without an adapter.
- Coverage is “most” public operators, not necessarily 100%.
- Confirm commercial redistribition terms before shipping in a paid store build if licence is interpreted strictly.

**Gaston plan**

1. Implement `SwitzerlandIchTankeStromAvailabilityProvider` (name TBD) + cache (~60s status, longer static).
2. Wire `ParkingRegion.Switzerland` in the factory **before** Eco-Movement.
3. Tests for status mapping + factory; document licence note in `sources.md`.

---

## Suggested next 8 (after NL / DE / IT / NO / SE)

| Rank | Country | Rationale |
|------|---------|-----------|
| 1 | **CH** | Open static+status dumps, bbox ready |
| 2 | **FI** | Digitraffic open AFIR API |
| 3 | **AT** | E-Control free API + live status |
| 4 | **PL** | EIPA free JSON + `dynamic.json` |
| 5 | **ES** | High demand; unblock when pull API exists (Eco-Movement until then) |
| 6 | **DK** | Aggregated real-time programme; DATEX NAP path |
| 7 | **PT** | EADME/NAP maturing; high Iberian pairing with ES |
| 8 | **IE** | TII OCPI DXP → NAP publication |

---

*Last reviewed: 2026-08-29. NAP portals move quickly under AFIR — re-check linked URLs before implementation.*
