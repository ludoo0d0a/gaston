# Data sources — Pumperly vs Gaston

This document compares **Pumperly**’s documented sources (from its “Data Sources” tables) with what is currently implemented in **Gaston**.

**API keys:** which feeds need credentials and how to register → [`docs/API_KEYS.md`](docs/API_KEYS.md). Build-time property names → [`docs/ENV_VARS.md`](docs/ENV_VARS.md).

## Implemented in Gaston (selectable providers)

| Provider | Country / coverage | Category | API base / endpoint | API key |
|---|---|---|---|---|
| **DataGouv (Flux instantané prix carburants)** | France | Fuel | `https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2` | No |
| **DataGouv (Prix carburants quotidien)** | France | Fuel | `https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-carburants-quotidien` | No |
| **Gas API** | France | Fuel | `https://gas-api.ovh` | No |
| **UK Fuel Finder / CMA open data feeds** | United Kingdom | Fuel | Multiple retailer JSON feeds (see GOV.UK “Access fuel price data”) | No |
| **Spain Minetur (MITECO/Minetur)** | Spain | Fuel | `https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/` | No |
| **Germany Tankerkönig (MTS-K)** | Germany | Fuel | `https://creativecommons.tankerkoenig.de/` | Yes — `GERMANY_TANKERKOENIG_KEY` |
| **Austria E‑Control** | Austria | Fuel | `https://api.e-control.at/sprit/1.0/` | No |
| **MIMIT (Prezzi + Anagrafica)** | Italy | Fuel | `https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv` + `.../anagrafica_impianti_attivi.csv` | No |
| **goriva.si** | Slovenia | Fuel | `https://goriva.si/api/v1/search/` | No |
| **DrivstoffAppen** | Norway | Fuel | `https://backend.drivstoffapp.no/stations/fuel/nearby` | No |
| **DGEG (Preços Combustíveis)** | Portugal | Fuel | `https://precoscombustiveis.dgeg.gov.pt/api/PrecoComb/PesquisarPostos` | No |
| **ANWB** | Netherlands + Belgium + Luxembourg | Fuel | `https://api.anwb.nl/routing/points-of-interest/v3/all` | No |
| **Fuelprices.dk** | Denmark | Fuel | `https://fuelprices.dk/api` | Yes — `FUELPRICES_DK_KEY` |
| **Fuelo.net** | Multi-country (EU + more) | Fuel | `https://{country}.fuelo.net/...` | No |
| **FuelCheck (NSW)** | Australia (NSW) | Fuel | `https://api.onegov.nsw.gov.au/FuelPriceCheck/v1/fuel/prices` | Yes — `NSW_FUELCHECK_KEY` + secret |
| **MZOE** | Croatia | Fuel | `https://mzoe-gor.hr/data.json` | No |
| **polttoaine.net** | Finland | Fuel | `https://www.polttoaine.net/` | No |
| **FuelGR** | Greece | Fuel | `https://fuelgr.gr/` | No |
| **Pick A Pump** | Ireland | Fuel | (API) | No |
| **ANRE** | Moldova | Fuel | `https://api.ecarburanti.anre.md/public/` | No |
| **Peco Online** | Romania | Fuel | Parse REST API | Yes — `ROMANIA_PECO_*` |
| **NIS + cenagoriva.rs** | Serbia | Fuel | `https://www.nisgazprom.rs/...` + `https://cenagoriva.rs/...` | No |
| **CRE** | Mexico | Fuel | (places + prices) | No |
| **Secretaría de Energía** | Argentina | Fuel | (CSV open data) | No |
| **Belgium official (max prices)** | Belgium | Fuel | (client-backed) | No |
| **OpenVan.camp** | Multi-country (reference prices) | Fuel | `https://openvan.camp/api/fuel/prices` | No |
| **Routex / Wigeogis** | Europe | Fuel | `https://app.wigeogis.com/kunden/routex-sitefinder/backend` | No |
| **DataGouv IRVE** | France | EV charging | `https://odre.opendatasoft.com/api/explore/v2.1/catalog/datasets/bornes-irve` | No |
| **Open Charge Map** | Global | EV charging | `https://api.openchargemap.io/v3/poi` | Yes — `OPENCHARGEMAP_KEY` |
| **Chargy** | Luxembourg | EV charging | `https://my.chargy.lu/.../kml` | Yes — `CHARGY_API_KEY` |
| **Fastned (OCPI 2.2.1)** | UK (Open Data) | EV charging | `https://uk-public.api.fastned.nl/uk-public/ocpi/cpo/2.2.1` | Yes — `FASTNED_UK_KEY` |
| **DKV Mobility (OCPI)** | EU (network) | EV charging | `https://api-portal.dkv-mobility.com/...` | Yes — `DKV_SUBSCRIPTION_KEY` |
| **Eco‑Movement (OCPI 2.2.1)** | EU / Global | EV charging | `https://open-chargepoints.com/api/ocpi/cpo/2.2.1` | Yes — `ECO_MOVEMENT_KEY` |
| **Belib’ availability** | Paris (FR) | EV charging | `https://parisdata.opendatasoft.com/.../belib-...` | No — see [`BELIB_AVAILABILITY_API.md`](docs/BELIB_AVAILABILITY_API.md) |
| **OpenStreetMap (Overpass)** | Global | POIs (many) | `https://overpass-api.de/api/interpreter` | No |

## Present in Pumperly but NOT implemented in Gaston (missing providers)

These sources are listed by Pumperly, but Gaston currently has no corresponding provider implementation wired.

| Country | Pumperly source | Type | Notes / what’s missing in Gaston |
|---|---|---|---|
| Sweden | **Drivstoffappen** / **bensinpriser.nu** | Community | Implemented (DrivstoffAppen SE) |
| Australia (WA) | **FuelWatch** | Government API | Implemented |
| Switzerland | **Comparis** | Scraper | Implemented |
| Australia (national) | **PetrolSpy** | API | Implemented |

## Notes

- Gaston is a **client app** and its providers are implemented as `PoiProvider`s; Pumperly’s long list includes many **scraper-based per-country sources**.
- If you want parity with Pumperly’s full country coverage, the next step is to implement the missing providers above one-by-one (starting with **UK CMA**, **Portugal DGEG**, **Italy MIMIT**, **Slovenia goriva.si**, **Denmark FuelPrices.dk** which are “official/government-like” and best-documented).
