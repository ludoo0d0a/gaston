# EV stations + live availability — non-EU roadmap

OCPI-or-equivalent sources for **United States, Australia, Canada, China, Japan**. Complements the European NAP roadmap: [`EV_AVAILABILITY_ROADMAP.md`](EV_AVAILABILITY_ROADMAP.md).

**Related:** [`sources.md`](sources.md) · [`API_KEYS.md`](API_KEYS.md) · [`AFDC_AVAILABILITY.md`](AFDC_AVAILABILITY.md) · global fallbacks **Eco-Movement** (`ECO_MOVEMENT_KEY`) and **Open Charge Map** · factory via [`BorneAvailabilityProviderFactory`](../shared/src/commonMain/kotlin/fr/geoking/gaston/api/belib/BorneAvailabilityProviderFactory.kt) + [`ParkingRegion`](../shared/src/commonMain/kotlin/fr/geoking/gaston/parking/ParkingRegion.kt) (US + CA wired for AFDC; AU present; CN/JP not yet)

Legend: **Yes** / **Partial** / **No** for open consumer pull. Priorities **P0–P3** same as EU doc (open/free-key first).

---

## Summary

| Country | Official / primary source | Format | Stations open pull? | Live availability open pull? | Auth | Priority | Notes |
|---------|---------------------------|--------|---------------------|------------------------------|------|----------|-------|
| **US** | AFDC / NREL Alt-Fuel Stations API (+ NEVI OCPI mandate on federally funded CPOs) | Custom REST (OCPI-inspired inventory); per-network OCPI 2.2.1 for NEVI realtime | **Yes** | **Partial** | Free NREL API key | **Done** | [`AFDC_AVAILABILITY.md`](AFDC_AVAILABILITY.md) |
| **CA** | NRCan Electric Charging & Alternative Fuelling Stations Locator (same NREL/AFDC stack as US) | Same AFDC REST as US (`country=all`) | **Yes** | **Partial** | Free NREL API key | **Done** | Same client as US. [`AFDC_AVAILABILITY.md`](AFDC_AVAILABILITY.md) |
| **AU** | No national EV NAP; DCCEEW MOS guidance; fragmented CPOs (Evie, Chargefox, …) | App/proprietary; OCM / Eco-Movement | **Partial** | **No** | Commercial / none for OCM | **P2** | Fuel already via FuelCheck/FuelWatch/PetrolSpy. See [§ Australia](#australia-au) |
| **JP** | No public NAP dump; commercial aggregators (EVsmart / ENECHANGE OCPI API, Plugo, …) | OCPI (B2B) / proprietary | **Partial** | **Partial** | Partner / commercial | **P2** | Track EVsmart Data API if a self-serve key appears. See [§ Japan](#japan-jp) |
| **CN** | Provincial / grid operator platforms; **GB/T 44130** public info exchange (operator↔platform) | Chinese national specs (not OCPI); map LBS APIs (commercial) | **No** | **No** | Operator registration / commercial LBS | **P3** | No Belgium-style open dump for foreign apps. See [§ China](#china-cn) |

**Global fallbacks (already in Gaston):** Eco-Movement OCPI (EU-centric coverage may be thin outside Europe), Open Charge Map (stations; availability uneven).

---

## Phased roadmap (non-EU)

### Phase N1 — North America AFDC (done)

| Order | Country | Why |
|-------|---------|-----|
| 1 | **US** | Free NREL key; `ParkingRegion.UnitedStates` in `containing()`; fuel via EIA |
| 2 | **CA** | Same AFDC client (`country=all`); `ParkingRegion.Canada` |

**Shipped:** `AfdcAvailabilityClient` / `AfdcAvailabilityProvider`; factory US/CA → AFDC; `NREL_AFDC_KEY`; [`AFDC_AVAILABILITY.md`](AFDC_AVAILABILITY.md).

**Caveat:** AFDC status is **not** true OCPI realtime for every port — many networks sync daily via OCPI into AFDC; NEVI requires networks to expose **their own** OCPI 2.2.1 APIs free to developers (per-network tokens), not a single national OCPI hub.

### Phase N2 — watch / partner

| Order | Country | Why |
|-------|---------|-----|
| 3 | **AU** | No open national pull; stay OCM + Eco-Movement; revisit if National Charge Link / MOS hardens into a registry API |
| 4 | **JP** | Prefer a documented commercial OCPI feed (EVsmart) only if terms fit a free-tier or affordable key; else OCM |

### Phase N3 — blocked for now

| Order | Country | Why |
|-------|---------|-----|
| 5 | **CN** | Operator→regulator GB/T pipelines; no public OCPI NAP for MSP apps; commercial map APIs only |

---

## United States (US)

| Layer | What exists | Open for Gaston? |
|-------|-------------|------------------|
| **National locator** | [AFDC Station Locator](https://afdc.energy.gov/stations) — US + Canada inventory maintained with NREL | **Yes** — [Alternative Fuel Stations API](https://developer.nrel.gov/docs/transportation/alt-fuel-stations-v1/) (`developer.nrel.gov`; free [API key](https://developer.nrel.gov/signup/)) |
| **Ingest path** | Networks feed AFDC via **OCPI** daily API import or CSV | Gaston talks to **AFDC REST**, not each CPO |
| **Realtime (NEVI)** | Federally funded CPOs must expose **OCPI 2.2.1**-shaped APIs free to third-party developers ([23 CFR 680.116(c)](https://www.ecfr.gov/current/title-23/chapter-I/subchapter-G/part-680); DOE/NREL guidance on standardized realtime APIs) | **Per network** — token request to each CPO; no single US OCPI dump URL |
| **Other** | State CSV programmes (NYSERDA, etc.) | Fragmented; lower priority than AFDC |

**AFDC practical notes**

- Endpoints: `/api/alt-fuel-stations/v1/nearest`, `/v1.json`, `/v1/nearby-route`, fuel filter `fuel_type=ELEC`.
- Station-level `status_code` (e.g. E available / T temp unavailable / P planned) — coarser than OCPI EVSE status; use for availability when present.
- Rate limits apply (document in country doc when implementing).

**Gaston**

1. **Done (P0):** `AfdcAvailabilityProvider` with `NREL_AFDC_KEY`, `country=all`, US+CA factory branches.
2. Optional later: opt-in per-network NEVI OCPI for fresher status on major networks (Electrify America, EVgo, ChargePoint, …) — high maintenance.

---

## Canada (CA)

| Layer | What exists | Open for Gaston? |
|-------|-------------|------------------|
| **National locator** | [NRCan Electric Charging and Alternative Fuelling Stations Locator](https://natural-resources.canada.ca/energy-efficiency/transportation-energy-efficiency/electric-charging-alternative-fuelling-stationslocator-map) | **Yes** via **same NREL/AFDC API** (`country=CA` / `all`) |
| **Ingest** | NRCan partners with NREL; networks push OCPI into the shared North American DB | Same as US |

**Gaston**

1. **Done:** same `AfdcAvailabilityProvider` as US (`country=all`); `ParkingRegion.Canada` in `containing()`.
2. No separate Canadian OCPI NAP required for v1.

---

## Australia (AU)

| Layer | What exists | Open for Gaston? |
|-------|-------------|------------------|
| **Policy** | DCCEEW [Minimum operating standards](https://www.dcceew.gov.au/sites/default/files/documents/guidance-document-minimum-operating-standards-electric-vehicles-charging-infrastructure.pdf) — funded projects should publish availability/pricing online | Guidance, **not** a national machine API |
| **National Charge Link** | RACE for 2030 concept for a national charge data platform | Research / future; **not** a live open API |
| **CPOs** | Evie, Chargefox, Ampol, etc. | Proprietary apps; Evie API undocumented to anonymous callers |
| **Aggregators** | Open Charge Map, Eco-Movement (if coverage) | **Yes** (existing keys) |

**Gaston plan**

1. Stations: OCM (+ Eco-Movement if key set). Fuel providers already cover AU fuel.
2. Do **not** scrape CPO apps.
3. Revisit if a government registry publishes OCPI/JSON dumps.

---

## Japan (JP)

| Layer | What exists | Open for Gaston? |
|-------|-------------|------------------|
| **Aggregators** | EVsmart (ENECHANGE) — large JP inventory; **EVsmart Data API** advertised as **OCPI**-compatible for eMSP partners | **Partial** — partnership / commercial, not a free self-serve dump |
| **CPO platforms** | Plugo OPEN CHARGE LAB (OCPI roaming, dynamic POI APIs for partners) | Partner |
| **Roaming** | Hubject intercharge (+ native OCPI GA path) | B2B |
| **Public** | OCM community data | Thin vs commercial apps |

**Gaston plan**

1. Default: OCM + Eco-Movement.
2. If product needs JP depth: evaluate EVsmart Data API contract (OCPI locations+status) as a paid/partner provider — same integration shape as Eco-Movement.
3. Add `ParkingRegion.Japan` only when a provider is wired.

---

## China (CN)

| Layer | What exists | Open for Gaston? |
|-------|-------------|------------------|
| **National standards** | **GB/T 44130** series — EV charging/swap **public information exchange** between operator platforms and third-party/management platforms | Operator↔platform; **not** an open anonymous REST for foreign MSPs |
| **Grid / provincial** | State Grid / provincial “充换电监管” platforms — CPO **push** of station + interface status | Registration as data provider/consumer inside CN regulatory stack |
| **Consumer map APIs** | Commercial LBS (e.g. Navinfo EV search) | Paid keys; ToS / map compliance |
| **OCPI** | Not the domestic interchange standard | — |

**Gaston plan**

1. **P3:** no dedicated CN availability provider unless a clear open/commercial API with acceptable ToS appears.
2. OCM only if useful; do not reverse-engineer WeChat mini-program / app APIs.
3. Long-term: partner feed that already aggregates GB/T status into OCPI or JSON for overseas apps.

---

## Cross-cutting (non-EU)

| Topic | Guidance |
|-------|----------|
| **Prefer AFDC over per-CPO OCPI** | One key, US+CA, good enough status for v1 |
| **NEVI OCPI** | Only if AFDC freshness is insufficient for a named network |
| **Eco-Movement outside EU** | Verify coverage before promising AU/JP/CN parity |
| **ParkingRegion** | US + Canada in `containing()`; AU already present; JP later |
| **Ids / matching** | AFDC station id + lat/lon distance match (same pattern as Belib) |

---

## Suggested next 5 (non-EU)

| Rank | Country | Rationale |
|------|---------|-----------|
| 1 | **AU** | Document-only until a national registry API exists; keep OCM |
| 2 | **JP** | Partner OCPI (EVsmart) if budget/terms OK |
| 3 | **CN** | Blocked on open pull; commercial LBS only as last resort |
| — | **US / CA** | **Done** — AFDC (`NREL_AFDC_KEY`) |

---

*Last reviewed: 2026-08-29. Re-check NREL domain (`developer.nrel.gov` / `developer.nlr.gov` transition) and NEVI guidance before implementation.*
