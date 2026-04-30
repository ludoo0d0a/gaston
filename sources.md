# Data sources — Pumperly vs Gaston

This document compares **Pumperly**’s documented sources (from its “Data Sources” tables) with what is currently implemented in **Gaston**.

## Implemented in Gaston (selectable providers)

| Provider | Country / coverage | Category | API base / endpoint | Notes |
|---|---|---|---|---|
| **DataGouv (Flux instantané prix carburants)** | France | Fuel | `https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2` | Official (Etalab) |
| **DataGouv (Prix carburants quotidien)** | France | Fuel | `https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-carburants-quotidien` | Official (Etalab) |
| **Gas API** | France | Fuel | `https://gas-api.ovh` | Wrapper around French open data |
| **UK Fuel Finder / CMA open data feeds** | United Kingdom | Fuel | Multiple retailer JSON feeds (see GOV.UK “Access fuel price data”) | Aggregated client-side |
| **Spain Minetur (MITECO/Minetur)** | Spain | Fuel | `https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/` | Official |
| **Germany Tankerkönig (MTS-K)** | Germany | Fuel | `https://creativecommons.tankerkoenig.de/` | API key required in some modes |
| **Austria E‑Control** | Austria | Fuel | `https://api.e-control.at/sprit/1.0/` | Official |
| **MIMIT (Prezzi + Anagrafica)** | Italy | Fuel | `https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv` + `.../anagrafica_impianti_attivi.csv` | Pipe-delimited since Feb 2026 |
| **goriva.si** | Slovenia | Fuel | `https://goriva.si/api/v1/search/` | Public REST API |
| **DrivstoffAppen** | Norway | Fuel | `https://backend.drivstoffapp.no/stations/fuel/nearby` | Public API (OpenAPI available) |
| **DGEG (Preços Combustíveis)** | Portugal | Fuel | `https://precoscombustiveis.dgeg.gov.pt/api/PrecoComb/PesquisarPostos` | Official |
| **ANWB** | Netherlands + Belgium + Luxembourg | Fuel | `https://api.anwb.nl/routing/points-of-interest/v3/all` | Bounding-box queries (commercial) |
| **Fuelprices.dk** | Denmark | Fuel | (API) | API key required |
| **Fuelo.net** | Multi-country (EU + more) | Fuel | `https://{country}.fuelo.net/...` | Scraper-style; country-scoped |
| **FuelCheck (NSW)** | Australia (NSW) | Fuel | `https://api.onegov.nsw.gov.au/FuelPriceCheck/v1/fuel/prices` | API key + secret required |
| **MZOE** | Croatia | Fuel | `https://mzoe-gor.hr/data.json` | Official dataset |
| **polttoaine.net** | Finland | Fuel | `https://www.polttoaine.net/` | HTML scraping |
| **FuelGR** | Greece | Fuel | `https://fuelgr.gr/` | Nearby query / community |
| **Pick A Pump** | Ireland | Fuel | (API) | Community |
| **ANRE** | Moldova | Fuel | `https://api.ecarburanti.anre.md/public/` | Official-ish open API |
| **Peco Online** | Romania | Fuel | (API) | Community |
| **NIS + cenagoriva.rs** | Serbia | Fuel | `https://www.nisgazprom.rs/...` + `https://cenagoriva.rs/...` | Mixed sources |
| **CRE** | Mexico | Fuel | (places + prices) | Government open data |
| **Secretaría de Energía** | Argentina | Fuel | (CSV open data) | Government open data |
| **Belgium official (max prices)** | Belgium | Fuel | (client-backed) | Official max prices (not ANWB) |
| **OpenVan.camp** | Multi-country (reference prices) | Fuel | `https://openvan.camp/api/fuel/prices` | Weekly reference prices, CC BY 4.0 |
| **Routex / Wigeogis** | Europe | Fuel | `https://app.wigeogis.com/kunden/routex-sitefinder/backend` | Commercial |
| **DataGouv IRVE** | France | EV charging | `https://odre.opendatasoft.com/api/explore/v2.1/catalog/datasets/bornes-irve` | Official |
| **Open Charge Map** | Global | EV charging | `https://api.openchargemap.io/v3/poi` | ODbL |
| **Chargy** | Luxembourg | EV charging | `https://my.chargy.lu/.../kml` | KML feed |
| **Fastned (OCPI 2.2.1)** | UK (Open Data) | EV charging | `https://api.fastned.nl/public/ocpi/cpo/2.2.1/` | OCPI |
| **DKV Mobility (OCPI)** | EU (network) | EV charging | (via API portal / Azure APIM) | Subscription key / auth header |
| **Eco‑Movement (OCPI 2.2.1)** | EU / Global | EV charging | `https://open-chargepoints.com/api/ocpi/cpo/2.2.1` | API key required |
| **Belib’ availability** | Paris (FR) | EV charging | `https://parisdata.opendatasoft.com/.../belib-...` | Real-time availability overlay |
| **OpenStreetMap (Overpass)** | Global | POIs (many) | `https://overpass-api.de/api/interpreter` | Toilets, water, camping, etc. |

## Present in Pumperly but NOT implemented in Gaston (missing providers)

These sources are listed by Pumperly, but Gaston currently has no corresponding provider implementation wired.

| Country | Pumperly source | Type | Notes / what’s missing in Gaston |
|---|---|---|---|
| Sweden | **Drivstoffappen** / **bensinpriser.nu** | Community | Missing provider |
| Australia (WA) | **FuelWatch** | Government API | Still missing (endpoint was unstable from CI) |

## Notes

- Gaston is a **client app** and its providers are implemented as `PoiProvider`s; Pumperly’s long list includes many **scraper-based per-country sources**.
- If you want parity with Pumperly’s full country coverage, the next step is to implement the missing providers above one-by-one (starting with **UK CMA**, **Portugal DGEG**, **Italy MIMIT**, **Slovenia goriva.si**, **Denmark FuelPrices.dk** which are “official/government-like” and best-documented).
