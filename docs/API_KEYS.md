# API keys for data sources

Most Gaston providers use **open data** and need no key. This guide covers every feed that does, plus map and transit keys used by the app.

Keys are read at **build time** from `local.properties` or environment variables (same names). See [`ENV_VARS.md`](ENV_VARS.md) for the full property list and CI examples.

Some keys can also be entered in the app under **Settings → App config** (stored on device / synced via Firestore). Build-time keys are the default when the in-app field is empty.

| Property | In-app override | Required for |
|----------|-----------------|--------------|
| `GOOGLE_MAPS_KEY` | — | Map tiles ([setup guide](MAPS_API_KEY_SETUP.md)) |
| `OPENCHARGEMAP_KEY` | Yes | Open Charge Map provider |
| `ECO_MOVEMENT_KEY` | Yes | Eco-Movement OCPI provider |
| `FUELPRICES_DK_KEY` | Yes | Fuelprices.dk (Denmark) |
| `NSW_FUELCHECK_KEY` + `NSW_FUELCHECK_SECRET` | Yes | NSW FuelCheck (Australia) |
| `GERMANY_TANKERKOENIG_KEY` | — | Tankerkönig (Germany); demo key available |
| `FASTNED_UK_KEY` | — | Fastned UK OCPI |
| `CHARGY_API_KEY` | — | Chargy Luxembourg KML feed |
| `DKV_SUBSCRIPTION_KEY` (+ optional `DKV_AUTHORIZATION`) | — | DKV Mobility OCPI |
| `ROMANIA_PECO_APPLICATION_ID` + `ROMANIA_PECO_CLIENT_KEY` | — | Peco Online (Romania) |
| `MOBILITEIT_LUXEMBOURG_KEY` | Yes (prefs) | Luxembourg transit (HAFAS) |
| `TOMTOM_KEY` | — | TomTom Traffic (global fallback) |

---

## Open Charge Map (EV, global)

| | |
|---|---|
| **Property** | `OPENCHARGEMAP_KEY` |
| **Auth** | Query parameter `key=` on API requests |
| **Docs** | [openchargemap.org/develop/api](https://openchargemap.org/develop/api) |

1. [Register](https://openchargemap.org/site/loginprovider/register) for an Open Charge Map account.
2. Open **[My Apps](https://openchargemap.org/site/profile/applications)** → **Register an application**.
3. Enter your app name and URL; describe your use case. Save.
4. Copy the API key shown on **My Apps** into `local.properties` or Settings.

Without a key, the API may rate-limit or reject heavy use.

---

## Eco-Movement (EV, EU / global, OCPI 2.2.1)

| | |
|---|---|
| **Property** | `ECO_MOVEMENT_KEY` |
| **Auth** | Header `Authorization: Token <key>` |
| **Docs** | [developers.eco-movement.com](https://developers.eco-movement.com) · [Data API user guide](https://developers.eco-movement.com/docs/data-api-user-guide) |

Eco-Movement is a **commercial** data product. Access is not self-service:

1. Contact Eco-Movement (sales / your account representative) to subscribe to the Data API.
2. They provide an OCPI token for `https://open-chargepoints.com/api/ocpi/cpo/2.2.1`.
3. Set `ECO_MOVEMENT_KEY` or paste the token in Settings.

---

## Fuelprices.dk (fuel, Denmark)

| | |
|---|---|
| **Property** | `FUELPRICES_DK_KEY` |
| **Auth** | Header `X-API-KEY` |
| **API** | `https://fuelprices.dk/api/v1/...` |

1. Go to [fuelprices.dk](https://fuelprices.dk/) and request API access (account registration; name and email are used to deliver the key — see their [privacy policy](https://fuelprices.dk/privatliv)).
2. For questions, use [fuelprices.dk/kontakt](https://fuelprices.dk/kontakt).
3. Set `FUELPRICES_DK_KEY` or enter the key in Settings.

---

## NSW FuelCheck (fuel, Australia NSW)

| | |
|---|---|
| **Properties** | `NSW_FUELCHECK_KEY` (consumer key), `NSW_FUELCHECK_SECRET` (consumer secret) |
| **Auth** | OAuth2 client credentials, then Bearer token + `apikey` header on price calls |
| **Portal** | [api.nsw.gov.au](https://api.nsw.gov.au/) |

1. [Register](https://api.nsw.gov.au/Account/Register) on the NSW API developer portal.
2. Create an app under **My Apps** and subscribe to the **Fuel Price Check** product ([product catalogue](https://api.nsw.gov.au/ProductCatalogue)).
3. After approval, copy the **Consumer Key** and **Consumer Secret** from your app credentials.
4. Set both properties or enter them in Settings.

Support: [api.nsw.gov.au/support](https://api.nsw.gov.au/support/buildapis)

---

## Tankerkönig (fuel, Germany)

| | |
|---|---|
| **Property** | `GERMANY_TANKERKOENIG_KEY` |
| **Auth** | Query parameter `apikey=` |
| **Docs** | [creativecommons.tankerkoenig.de](https://creativecommons.tankerkoenig.de/) |

1. Open the [registration form](https://creativecommons.tankerkoenig.de/#register) on the Tankerkönig API site.
2. Confirm the email you receive.
3. Copy your API key into `GERMANY_TANKERKOENIG_KEY`.

**Demo / testing:** Gaston’s build accepts the public demo key  
`00000000-0000-0000-0000-000000000002` (low rate limits; not for production traffic).

Terms: one request per minute, max 25 km radius; data under Creative Commons — see site for MTS-K restrictions.

---

## Fastned UK (EV, OCPI 2.2.1)

| | |
|---|---|
| **Property** | `FASTNED_UK_KEY` |
| **Auth** | Header `x-api-key` |
| **Base URL** | `https://uk-public.api.fastned.nl/uk-public/ocpi/cpo/2.2.1` |
| **Policy** | [Fastned UK Open Data – Fair Use Policy](https://www.fastnedcharging.com/en-gb/uk-open-data) |

Access is provided under UK Public Charge Point Regulations. There is no public self-service signup page in Gaston’s docs:

1. Contact Fastned (e.g. via their UK website / support) and request access to the **UK Open Data OCPI API**.
2. You receive an `x-api-key` for the endpoints above.
3. Set `FASTNED_UK_KEY`.

---

## Chargy (EV, Luxembourg)

| | |
|---|---|
| **Property** | `CHARGY_API_KEY` |
| **Auth** | Query parameter `API-KEY` on the KML feed |
| **Endpoint** | `https://my.chargy.lu/b2bev-external-services/resources/kml` |

The real-time KML feed is operated by Chargy (not the generic data.public.lu CKAN API).

1. Request API access from [Chargy](https://chargy.lu/) or via Luxembourg open-data contacts (dataset: [bornes de recharge](https://data.public.lu/en/datasets/bornes-de-chargement-publiques-pour-voitures-electriques/)).
2. Set `CHARGY_API_KEY`. If blank, the Chargy provider returns no stations.

---

## DKV Mobility (EV, OCPI via Azure APIM)

| | |
|---|---|
| **Properties** | `DKV_SUBSCRIPTION_KEY` (required), `DKV_AUTHORIZATION` (optional OCPI token) |
| **Auth** | Header `Ocp-Apim-Subscription-Key`; optional `Authorization: Token …` |
| **Portal** | [api-portal.dkv-mobility.com](https://api-portal.dkv-mobility.com/) |

1. API access is granted through DKV Mobility sales / onboarding ([how it works](https://api-portal.dkv-mobility.com/how-to), [FAQs](https://api-portal.dkv-mobility.com/faqs)).
2. Subscribe to the relevant product on the developer portal; the **subscription key** is emailed once.
3. If your contract uses OCPI credentials, set `DKV_AUTHORIZATION` as documented in [API Authentication](https://api-portal.dkv-mobility.com/content/html_widgets/uxlt9.html).

---

## Peco Online (fuel, Romania)

| | |
|---|---|
| **Properties** | `ROMANIA_PECO_APPLICATION_ID`, `ROMANIA_PECO_CLIENT_KEY` |
| **Auth** | Parse REST headers `X-Parse-Application-Id`, `X-Parse-Client-Key` |
| **Site** | [pecoonline.ro](https://pecoonline.ro/) |

There is **no public developer portal**. The app talks to a Parse backend used by the Peco Online Android app.

For local builds and CI you need the same Parse application id and client key the official app uses (often extracted from the app package for personal / research use). Set both properties; if either is blank, the Romania provider returns no data.

---

## mobiliteit.lu (transit, Luxembourg)

| | |
|---|---|
| **Property** | `MOBILITEIT_LUXEMBOURG_KEY` |
| **Auth** | HAFAS OpenData API key (see provider implementation) |
| **Contact** | Request from **opendata-api@atp.etat.lu** (Luxembourg Administration des Transports Publics) |

1. Email [opendata-api@atp.etat.lu](mailto:opendata-api@atp.etat.lu) describing your app and intended use.
2. Set `MOBILITEIT_LUXEMBOURG_KEY` or save the key in app settings (`mobiliteit_luxembourg_key`).

Base API: `https://cdt.hafas.de/opendata/apiserver/`

---

## TomTom Traffic (traffic, global fallback)

| | |
|---|---|
| **Property** | `TOMTOM_KEY` |
| **Auth** | Query parameter `key=` on Traffic Incidents API v5 |
| **Docs** | [developer.tomtom.com/traffic-api](https://developer.tomtom.com/traffic-api/documentation/traffic-incidents/incident-details) |

1. Create a [TomTom Developer](https://developer.tomtom.com/) account.
2. Create an API key with access to the **Traffic** APIs.
3. Set `TOMTOM_KEY`. If blank, regional feeds (e.g. CITA for Luxembourg) still work; TomTom is only used where no regional provider is registered.

---

## Google Maps (map tiles)

Not a POI source, but required for the map UI.

→ Full steps: [`MAPS_API_KEY_SETUP.md`](MAPS_API_KEY_SETUP.md) · property `GOOGLE_MAPS_KEY`

---

## Sources that do **not** need an API key

These providers work without credentials (open data, public APIs, or scraping where allowed):

DataGouv (fuel + IRVE), Gas API, UK CMA feeds, Spain Minetur, Austria E-Control, Italy MIMIT, goriva.si, DrivstoffAppen, DGEG, ANWB, Fuelo, MZOE, polttoaine.net, FuelGR, Pick A Pump, ANRE, CRE, Argentina energy data, Belgium official prices, OpenVan.camp, Routex/Wigeogis, Belib availability (Paris open data), Overpass/OSM, OSRM, Open-Meteo, CITA traffic, RATP, STIB, and most other entries in [`sources.md`](../sources.md).

---

## CI integration tests

The [station-load-integration](../.github/workflows/station-load-integration.yml) workflow uses GitHub secrets for providers that need keys in CI:

| Secret | Country |
|--------|---------|
| `FUELPRICES_DK_KEY` | DK |
| `GERMANY_TANKERKOENIG_KEY` | DE |
| `ROMANIA_PECO_APPLICATION_ID` / `ROMANIA_PECO_CLIENT_KEY` | RO |

Add secrets under **Settings → Secrets and variables → Actions** after you obtain the keys above.

---

## Related configuration (not POI sources)

| Property | Guide |
|----------|--------|
| `GOOGLE_WEB_CLIENT_ID` | [`GOOGLE_PLAY_MIGRATION.md`](GOOGLE_PLAY_MIGRATION.md) §5 (Firebase Sign-In) |
| `ADMOB_APP_ID`, `ADMOB_BANNER_UNIT_ID` | [`ADS_SETUP.md`](ADS_SETUP.md) |
| `REVENUECAT_API_KEY` | RevenueCat dashboard (Play Billing) |
| Play upload / Firebase | [`ENV_VARS.md`](ENV_VARS.md) (GitHub secrets section) |
