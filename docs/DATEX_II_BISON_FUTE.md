# DATEX II — Bison Futé / TIPI (France RRN)

Canonical reference for France national-road **DATEX II** traffic feeds used by Gaston.

Official portal: [Données sur le RRN — Bison Futé](https://www.bison-fute.gouv.fr/donnees-sur-le-rrn,langen.html)  
Also: [Action b](https://www.bison-fute.gouv.fr/action-b.html) · [Action c](https://www.bison-fute.gouv.fr/action-c.html) · [Accès aux données](https://www.bison-fute.gouv.fr/acces-aux-donnees.html)

## What is DATEX II?

**DATEX II** (CEN/TS 16157) is the European exchange model for road traffic data: situations/events, measured traffic (flow, speed, occupancy), travel times, etc. Publications are typically **XML** (sometimes JSON).

Bison Futé (Direction des Infrastructures de Transport) publishes RRN real-time data as DATEX II. Producers:

| Producer | Role |
|----------|------|
| **DIR** | Directions interdépartementales des routes — State network (non-concessioned) |
| **SCA** | Sociétés concessionnaires d’autoroutes — concessioned motorways |

Two product families:

1. **Road events** (`SituationPublication`) — accidents, obstacles, closures, congestion, works, SRTI safety categories, …
2. **Traffic measures** — average speed, flow (Q), occupancy (T), traffic state (normal / slow / saturated); travel times *planned later*

Gaston today consumes **situation / event** feeds only (see [In Gaston](#in-gaston)).

---

## 1) Road events

| Feed | Coverage | Auth | Legal frame | Gaston |
|------|----------|------|-------------|--------|
| **Action C** (SRTI) | RRN incl. **SCA** — sudden/dangerous events | Basic (`TIPI_USER` / `TIPI_PASSWORD`) | Directive ITS 2010/40/EU + [Reg. (EU) 886/2013](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32013R0886) (“priority action c”) | Yes — restricted |
| **Action B** (dynamic events) | RRN — other real-time events (jams, lane/road closures, works, …) | Same Basic credentials | Directive ITS + [Reg. (EU) 2015/962](https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32015R0962) (“priority action b”) | Yes — restricted |
| **Open DIR events** | RRN **non-concessioned** only (national roads + non-concessioned motorways) | None | Licence ouverte / opendata.fr | Yes — always on |

### Action C — safety categories (France)

Per [Décret n° 2015-474](https://www.bison-fute.gouv.fr/action-c.html) / NAP Action C page:

1. Temporarily slippery road  
2. Animal / person / obstacle / debris on the road  
3. Unsecured accident area  
4. Short-duration road works  
5. Reduced visibility  
6. Wrong-way driver  
7. Unmanaged road obstruction  

### Credentials (Action B / C)

1. Email **diffusion-numerique@info-routiere.gouv.fr**  
2. Accept the published reuse licence (Action B / Action C PDF on the NAP pages)  
3. Set `TIPI_USER` + `TIPI_PASSWORD` in `local.properties` (or env) — see [`ENV_VARS.md`](ENV_VARS.md), [`API_KEYS.md`](API_KEYS.md)

Open DIR events need **no** credentials (State open-data policy; former login-only DIR URLs were renamed — see [URL migration](#3-url-migration)).

---

## 2) Traffic measures (not wired in Gaston)

| Feed | Content | Auth | Cadence | Gaston |
|------|---------|------|---------|--------|
| **Action B — RRN speed/flow** | Average speed + flow on RRN axes | Basic (same TIPI account) | ~**6 min** (counting stations) | No |
| **Open DIR — QTV + traffic state** | Speed, flow, occupancy (QTV) + state in main agglomerations | None | ~1–6 min | No |
| **Action B — SCA traffic** | Concessionaire volume (etc.) | Basic | — | No |
| **Travel times** | Announced “later” on the portal | — | — | No |

Open DIR traffic / Traficolor / Debit-Vitesse links are listed on the [RRN data page](https://www.bison-fute.gouv.fr/donnees-sur-le-rrn,langen.html) and [Action b](https://www.bison-fute.gouv.fr/action-b.html) (“Accès aux données Débit-Vitesse des DIR”).

---

## 3) URL migration

DIR data moved from login-gated hosts to **free** `tipi.bison-fute.gouv.fr` open paths. Restricted Action B/C (and SCA traffic) stay behind Basic auth under `bison-fute-restreint`.

Correspondence tables (old → new) live on the official page; Gaston uses the **new** TIPI URLs below.

### URLs used by Gaston

| Role | URL | Auth |
|------|-----|------|
| Open DIR events (RRN NC) | `https://tipi.bison-fute.gouv.fr/bison-fute-ouvert/publicationsDIR/Evenementiel-DIR/grt/RRN/content.xml` | None |
| Action B events | `https://tipi.bison-fute.gouv.fr/bison-fute-restreint/publications-restreintes/grt/ACTION-B/content.xml` | Basic |
| Action C events | `https://tipi.bison-fute.gouv.fr/bison-fute-restreint/publications-restreintes/grt/ACTION-C/content.xml` | Basic |

Catalog / discovery: [transport.data.gouv.fr — évènements RRN non concédé](https://transport.data.gouv.fr/datasets/evenements-routiers-sur-le-reseau-routier-national-non-concede).

---

## In Gaston

| Component | Role |
|-----------|------|
| `BisonFuteTrafficClient` | Open DATEX dump + ETag / TTL cache |
| `TipiRestrictedTrafficClient` | Action B / Action C + Basic auth + TTL cache |
| `DatexSituationParser` / `BisonFuteDatexParser` | Minimal `SituationPublication` → `TrafficEvent` |
| `TipiActionTrafficProvider` | Restricted feed → `TrafficProvider` |
| `BisonFuteTrafficProvider` | Open feed → `TrafficProvider` |
| `FranceTrafficProvider` | Merge open → B → C (same `sourceId` → later wins, C preferred) |

Live e2e (local): `TipiRestrictedTrafficClientRealApiTests` — needs TIPI credentials in `local.properties`; not on default CI (see `*RealApiTests*` filter).

### Not implemented

- MeasuredData / QTV / Traficolor / Debit-Vitesse publications  
- Travel-time publications  
- Third-party operator feeds listed on Action B/C NAP pages (Coyote, Mediamobile, Michelin, TomTom as *operators* — TomTom Incidents is a separate global fallback via `TOMTOM_KEY`)

---

## Related docs

| Doc | Topic |
|-----|--------|
| [`API_KEYS.md`](API_KEYS.md#bison-futé--tipi-france-traffic) | How to obtain TIPI credentials |
| [`ENV_VARS.md`](ENV_VARS.md) | `TIPI_USER` / `TIPI_PASSWORD` |
| [`sources.md`](sources.md) | Short catalog row |
| [`trafic_opendata_guide.md`](trafic_opendata_guide.md) | Broader FR open-data traffic index |
| [`motorway_apis.md`](motorway_apis.md) | Motorway / service-area context |
