# Free API Source Providers for Fuel and IRVE

This document lists the free API source providers integrated into the Julius station finder for fuel and EV charging (IRVE) points across Europe.

| Provider Name | Region / Country | Category | API Base URL | License / Notes |
|---------------|------------------|----------|--------------|-----------------|
| **DataGouv (Prix Carburant)** | France | Fuel | `https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2` | Open Licence 2.0 (Etalab) |
| **DataGouv (Daily)** | France | Fuel | `https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-carburants-quotidien` | Licence Ouverte 1.0 |
| **Gas API** | France | Fuel | `https://gas-api.ovh` | Wraps French government open data |
| **Spain Minetur** | Spain | Fuel | `https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/` | Spanish Government (Minetur) |
| **Germany Tankerkönig** | Germany | Fuel | `https://creativecommons.tankerkoenig.de/json/list.php` | CC BY 4.0 (MTS-K) |
| **Austria E-Control** | Austria | Fuel | `https://api.e-control.at/sprit/1.0/` | E-Control Open Data |
| **OpenVan.camp** | Europe (Official references) | Fuel | `https://openvan.camp/api/fuel/prices` | CC BY 4.0 (PT, IT, SE, DK, FI, LU, BE...) |
| **DataGouv IRVE** | France | IRVE (EV) | `https://odre.opendatasoft.com/api/explore/v2.1/catalog/datasets/bornes-irve` | Licence Ouverte 2.0 |
| **Open Charge Map** | Global / Europe | IRVE (EV) | `https://api.openchargemap.io/v3/poi` | CC BY 4.0 |
| **Chargy** | Luxembourg | IRVE (EV) | `https://my.chargy.lu/b2bev-external-services/resources/kml` | Chargy Luxembourg |
| **Belib'** | Paris, France | IRVE (EV) | `https://parisdata.opendatasoft.com/api/explore/v2.1/catalog/datasets/belib-points-de-recharge-pour-vehicules-electriques-disponibilite-temps-reel` | Paris Data (Real-time availability) |
| **OpenStreetMap (Overpass)** | Global / Europe | Both & More | `https://overpass-api.de/api/interpreter` | ODbL (Base data for many categories) |
| **Routex** | Europe | Fuel | `https://app.wigeogis.com/kunden/routex-sitefinder/backend` | Commercial provider (integrated as a source) |

## Implementation Details

The application uses a unified `PoiProvider` interface to query these different sources. For fuel prices, it attempts to normalize fuel names (e.g., Gazole, SP95, SP98) to provide a consistent filtering experience across different countries.
