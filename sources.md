# Data sources — Pumperly vs Gaston

This document compares **Pumperly**’s documented sources (from its “Data Sources” tables) with what is currently implemented in **Gaston**.

## Implemented in Gaston (selectable providers)

| Provider | Country / coverage | Category | API base / endpoint | Notes |
|---|---|---|---|---|
| **DataGouv (Flux instantané prix carburants)** | France | Fuel | `https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2` | Official (Etalab) |
| **DataGouv (Prix carburants quotidien)** | France | Fuel | `https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-carburants-quotidien` | Official (Etalab) |
| **Gas API** | France | Fuel | `https://gas-api.ovh` | Wrapper around French open data |
| **Spain Minetur (MITECO/Minetur)** | Spain | Fuel | `https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/` | Official |
| **Germany Tankerkönig (MTS-K)** | Germany | Fuel | `https://creativecommons.tankerkoenig.de/` | API key required in some modes |
| **Austria E‑Control** | Austria | Fuel | `https://api.e-control.at/sprit/1.0/` | Official |
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
| UK | **CMA Open Data** | Government feeds | Missing provider + parsing for the 13 retailer feeds |
| Portugal | **DGEG** | Government API | Missing provider (and non-commercial disclaimer handling) |
| Italy | **MIMIT** | Government CSV | Missing provider (CSV ingestion + mapping) |
| Slovenia | **goriva.si** | Government API | Missing provider |
| Netherlands | **ANWB** | Commercial API | Missing provider |
| Belgium | **ANWB** | Commercial API | Gaston uses Belgium official max prices, not ANWB; ANWB provider missing |
| Luxembourg | **ANWB** | Commercial API | Gaston has Chargy for EV and OpenVan.camp references; ANWB fuel provider missing |
| Romania | **Peco Online** | Community | Missing provider |
| Greece | **FuelGR** | Community API | Missing provider |
| Ireland | **Pick A Pump** | Community API | Missing provider |
| Croatia | **MZOE** | Government API | Missing provider |
| Denmark | **FuelPrices.dk** | Commercial API | Missing provider (and API key support) |
| Norway | **DrivstoffAppen** | Government-mandated | Missing provider |
| Sweden | **Drivstoffappen** / **bensinpriser.nu** | Community | Missing provider |
| Serbia | **NIS / cenagoriva** | Brand-level | Missing provider |
| Finland | **polttoaine.net** | Community | Missing provider |
| Switzerland + many EU countries | **Fuelo.net** | Community | Missing provider(s) / scrapers; would likely be country-scoped |
| Turkey | **Fuelo.net** | Community | Missing provider |
| Moldova | **ANRE** | Government | Missing provider |
| Australia (WA + NSW) | **FuelWatch / FuelCheck** | Government API | Missing provider |
| Argentina | **Secretaría de Energía** | Government API | Missing provider |
| Mexico | **CRE** | Government API | Missing provider |

## Notes

- Gaston is a **client app** and its providers are implemented as `PoiProvider`s; Pumperly’s long list includes many **scraper-based per-country sources**.
- If you want parity with Pumperly’s full country coverage, the next step is to implement the missing providers above one-by-one (starting with **UK CMA**, **Portugal DGEG**, **Italy MIMIT**, **Slovenia goriva.si**, **Denmark FuelPrices.dk** which are “official/government-like” and best-documented).
