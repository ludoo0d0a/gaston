# Fuel price sources — backlog (major countries)

Research backlog for **government or official open fuel-price data** and **free APIs suitable for a mobile client** (no paid tiers, no hard per-day caps where possible).

**Already in Gaston** (see [`sources.md`](sources.md)): auto-mode countries in `AutoModeCountryProbes` + `Fuelo` fallback for several EU states.

**Legend**

| Tag | Meaning |
|-----|---------|
| ✅ | Official/open, no API key; no published hard quota (still cache responsibly) |
| 🔑 | Free but registration / token / OAuth |
| 📊 | Official open **files** (CSV/XLSX/JSON bulk), not a documented REST API |
| ⚠️ | Third-party, paid, scrape-only, or documented rate limits |
| 🏛️ | Government **national reference** only (averages/caps), not per-station |

---

## Already covered (major countries) — maintenance only

| ISO | Gaston provider | Source | API key | Station-level |
|-----|-----------------|--------|---------|---------------|
| **MX** | `MexicoCre` | CRE open publication (Azure) | No | Yes |
| | | `https://publicacionexterna.azurewebsites.net/publicaciones/places` | | |
| | | `https://publicacionexterna.azurewebsites.net/publicaciones/prices` | | |
| **AR** | `ArgentinaEnergia` | Secretaría de Energía (datos.gob.ar CSV) | No | Yes (surtidor, res. 314/2016) |
| | | [precios-en-surtidor CSV](https://datos.energia.gob.ar/dataset/1c181390-5045-475e-94dc-410429be4b17/resource/80ac25de-a44a-4445-9215-090cf55cfda5/download/precios-en-surtidor-resolucin-3142016.csv) | | |
| **AU** | `AustraliaPetrolSpy`, `AustraliaFuelWatch`, `AustraliaNswFuelCheck` | Mixed (community + WA gov + NSW/TAS gov API) | NSW: yes | Partial national |
| **US** | `UsaEia` | EIA state weekly averages + OSM stations | Yes (`EIA_KEY`) | State averages only |

**AU gaps (same country, not yet wired):** QLD, VIC, TAS (extend NSW API v2), SA, ACT, NT — see [Australia](#australia-au) below.

---

## Priority — large countries not in auto-mode

### Brazil (BR) — **high priority**

| Priority | Source | Type | Endpoint / access | Key | Granularity | Notes |
|----------|--------|------|-------------------|-----|-------------|-------|
| P0 | **ANP — Levantamento de Preços (dados abertos)** | 📊 ✅ | [Série histórica CSV](https://www.gov.br/anp/pt-br/centrais-de-conteudo/dados-abertos/serie-historica-de-precos-de-combustiveis); [preços por posto revendedor](https://www.gov.br/anp/pt-br/assuntos/precos-e-defesa-da-concorrencia/precos/levantamento-de-precos-de-combustiveis-ultimas-semanas-pesquisadas) | No | **Per station**, weekly | Best official feed; ~3k+ stations, geo in open-data decree files; update Fridays |
| P1 | ANP — médias Brasil/região/estado/município | 📊 ✅ | Same portal, weekly XLSX | No | Regional averages | Good fallback / map legend |
| — | Apify “brazil-fuel-prices-anp” | ⚠️ | `https://api.apify.com/v2/...` | Apify token | Station | Re-hosts ANP; **not** unlimited free |

**Implementation sketch:** `BrazilAnpProvider` — download latest “preços por posto” CSV/ZIP weekly, cache on device, merge with Overpass for missing coords.

---

### Canada (CA)

| Priority | Source | Type | Endpoint / access | Key | Granularity | Notes |
|----------|--------|------|-------------------|-----|-------------|-------|
| P0 | **Statistics Canada** table 18-10-0001-01 | 📊 ✅ | CSV: `https://www150.statcan.gc.ca/n1/tbl/csv/18100001-eng.zip`; SDMX: `.../tbl/sdmx/18100001-SDMX.zip` | No | City/province **monthly** averages | National coverage; not per station |
| P1 | **Ontario** motor fuel prices | ✅ | `https://ontario.ca/v1/files/fuel-prices/fueltypesall.csv` ([open.canada.ca dataset](https://open.canada.ca/data/en/dataset/c6ec6da3-2a8c-4b67-b59e-1d567efdaeac)) | No | 10 ON markets, **weekly** | Only ON, not all provinces |
| P2 | NRCan transportation fuel prices | ⚠️ | [Page](https://natural-resources.canada.ca/domestic-international-markets/transportation-fuel-prices) + legacy weekly tables | No | National/province weekly | **Third-party** underlying data per disclaimer |
| P2 | Atlantic provinces (NL, etc.) | 🏛️ | PUB/maximum price orders (regulatory) | No | Regulated caps | Useful in NL/NB/NS/PE only |

**Implementation sketch:** `CanadaStatCanProvider` for reference prices + Overpass stations; optional ON CSV for southern Ontario detail.

---

### China (CN)

| Priority | Source | Type | Endpoint / access | Key | Granularity | Notes |
|----------|--------|------|-------------------|-----|-------------|-------|
| — | **No central government retail station API found** | — | NDRC/省级发改委 publish policy; retail varies by province/city | — | — | Do not assume a single MOF/NDRC pump-price feed |
| P2 | 上海石油天然气交易中心 SHPGX | 📊 | Wholesale/index on [sina/finance mirrors](https://finance.sina.com.cn/money/future/roll/); official: [shpgx.com](https://www.shpgx.com/) | Varies | Wholesale / indices | Not retail pump prices |
| ⚠️ | Juhe 聚合 «今日油价» | ⚠️ | `GET https://apis.juhe.cn/cnoil/oil_city` | Yes | Province/city | **Quota** on free tier |
| ⚠️ | istero / others | ⚠️ | Commercial aggregators | Token | City | Documented QPS caps |

**Implementation sketch:** Treat CN as **unsupported** until a stable official open feed is identified; avoid scrapers unless product explicitly allows it.

---

### Japan (JP)

| Priority | Source | Type | Endpoint / access | Key | Granularity | Notes |
|----------|--------|------|-------------------|-----|-------------|-------|
| P0 | **資源エネルギー庁 — 石油製品価格調査** (給油所小売) | 📊 ✅ | [週次 Excel 一覧](https://www.enecho.meti.go.jp/statistics/petroleum_and_lpgas/pl007/results.html) | No | National/regional **weekly** retail survey | Official; parse XLSX |
| P1 | **e-Stat API** (小売物価統計 — ガソリン) | 🔑 ✅ | `http://api.e-stat.go.jp/rest/3.0/app/json/getStatsData` ([docs](https://www.e-stat.go.jp/api/api-dev/how_to_use)) | `appId` (free registration) | City-level **monthly** CPI gasoline | Good for trends, not live pumps |
| — | NAVITIME `/gas_price` | ⚠️ | [Spec](https://api-sdk.navitime.co.jp/api/specs/api_guide/gas_price.html) | Commercial contract | Prefecture/municipality averages | Paid option |

**Implementation sketch:** `JapanMetiProvider` (weekly XLSX) + optional e-Stat for charts; station POIs from Overpass.

---

### India (IN)

| Priority | Source | Type | Endpoint / access | Key | Granularity | Notes |
|----------|--------|------|-------------------|-----|-------------|-------|
| P0 | **PPAC** — metro RSP (IOC/BPC/HPC) | 📊 | [Metro cities since 16.6.2017](https://ppac.gov.in/retail-selling-price-rsp-of-petrol-diesel-and-domestic-lpg/rsp-of-petrol-and-diesel-in-metro-cities-since-16-6-2017); daily ~6 AM revision | No | **~4 metros** daily | Official; likely HTML/table scrape or mirror dataset |
| P1 | Mirror: Dataful PPAC dataset | 📊 ✅ | [Dataset 329](https://dataful.in/datasets/329/) | No | Metro daily | Easier machine read; confirm license/refresh |
| P2 | State capitals via OMC sites | 📊 | IOCL/BPCL/HPCL state-wise pages | No | State capital | Heavy scrape; 28 states |
| ⚠️ | PurePriceIO / DH BOSS / nixinfo | ⚠️ | Commercial APIs | Paid / per-hit | City/state | **Not** quota-free |

**Implementation sketch:** Start with metro PPAC (+ Dataful CSV if stable); expand to state capitals only if product needs it.

---

### Australia (AU) — extend existing

| State/territory | Source | Type | Endpoint / access | Key | In Gaston? |
|-----------------|--------|------|-------------------|-----|------------|
| NSW (+TAS via v2) | **FuelCheck / API NSW** | 🔑 | `https://api.onegov.nsw.gov.au/FuelPriceCheck/v2/...` ([product](https://api.nsw.gov.au/Product/Index/22)) | OAuth + API key | **Partial** (`AustraliaNswFuelCheck`) |
| VIC | **Servo Saver Public API** | 🔑 ✅ | [Service Victoria](https://service.vic.gov.au/find-services/transport-and-driving/servo-saver/help-centre/servo-saver-public-api) — free, application for Consumer ID | API Consumer ID | No — **24h delayed** |
| QLD | **Fuel Prices Queensland** | 🔑 | `https://fppdirectapi-prod.fuelpricesqld.com.au/` ([signup](https://www.fuelpricesqld.com.au/)); [open data](https://www.data.qld.gov.au/dataset/fuel-price-reporting-2026) | Subscriber token | No — near real-time (~30 min reporting rule) |
| WA | **FuelWatch** | ✅ | `https://www.fuelwatch.wa.gov.au/` | No | **Yes** |
| National fallback | PetrolSpy | ⚠️ | `https://petrolspy.com.au/` | No | **Yes** (not gov) |

**Todos:** `AustraliaVicServoSaverProvider`, `AustraliaQldFpqProvider`; wire TAS through NSW FuelCheck v2; document NSW rate guidance (“may restrict full dump frequency”).

---

## Other large economies (shorter)

| ISO | Country | Best official angle | Free unlimited API? | Suggested Gaston priority |
|-----|---------|---------------------|----------------------|---------------------------|
| **RU** | Russia | No stable open retail API identified | — | Low (sanctions/API risk) |
| **ID** | Indonesia | Pertamina/Harga BBM — ministry pages, not REST | — | Medium |
| **TR** | Turkey | Fuelo.net only in app today | Fuelo (community) | Medium — EPİAŞ/energy ministry caps |
| **KR** | South Korea | OPINET (oil price portal) — check TOU for API | Registration likely | Medium |
| **SA** | Saudi Arabia | Aramco domestic list prices (regulated) | — | Low |
| **ZA** | South Africa | Dept. Energy historical spreadsheets | 📊 | Medium |

---

## Multi-country official (not station-level) — useful fallbacks

| Source | Coverage | Access | Update | Use in Gaston |
|--------|----------|--------|--------|---------------|
| **EU Weekly Oil Bulletin** | EU-27 (+ UK history) national averages | 📊 ✅ [energy.ec.europa.eu](https://energy.ec.europa.eu/data-and-analysis/weekly-oil-bulletin_en) XLSX weekly + history | Weekly | Reference prices for EU countries without dedicated provider |
| **OpenVan.camp** | EU + ref. prices | ✅ API | Weekly | Already `OpenVanCamp` (LU etc.) |

---

## “Free API, no quota” — reality check

Truly **unmetered** public APIs are rare. For Gaston, prefer:

1. **Government bulk open data** (CSV/XLSX/JSON) with **local cache** and weekly/daily refresh — treat as ✅ if no key.
2. **Government APIs with free registration** (e-Stat, QLD, VIC, NSW) — 🔑; cache aggressively; respect documented limits.
3. **Avoid** for default auto-mode: Juhe, fuel-prices.eu, Apify, PurePriceIO, DH BOSS (paid or capped).

---

## Suggested implementation order (major countries)

1. **BR** — ANP per-station open CSV (largest gap, strong official data).
2. **CA** — StatCan monthly + Ontario weekly CSV (quick win for reference + partial detail).
3. **AU** — QLD + VIC government APIs (complete home market).
4. **JP** — METI weekly XLSX parser.
5. **IN** — PPAC metro daily (+ Dataful mirror).
6. **CN** — blocked until official retail open data exists.

---

## Provider checklist (new `PoiProvider` work)

For each new country provider:

- [ ] Add `PoiProviderType` + factory wiring in `:shared`
- [ ] Add `CountryStationProbe` if auto-mode should select it
- [ ] Document env vars in [`API_KEYS.md`](API_KEYS.md) (if any)
- [ ] Add row to [`sources.md`](sources.md)
- [ ] Optional: probe in `CountryStationLoadRealApiTests`

---

*Generated 2026-05-25. Re-verify URLs and ToS before implementation; government portals change frequently.*
