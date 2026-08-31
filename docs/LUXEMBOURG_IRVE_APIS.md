# Luxembourg IRVE APIs & P+R Bouillon Availability Analysis

This document summarizes the availability provider options for EV charging infrastructure (IRVE) in Luxembourg, with a focus on P+R Bouillon (Luxembourg City), and details why Chargy's website displays specific availability figures while public external APIs behave differently.

---

## 1. Provider Information: Eco-Movement, Enovos, and Swio

### Eco-Movement
* **Overview**: Eco-Movement is an independent global EV charging data aggregator. They aggregate, sanitize, and normalize live telemetry and tariff data directly from over 3,000 Charge Point Operators (CPOs) into standardized OCPI 2.2 REST APIs covering plug-level real-time status.
* **Website & Documentation**:
  * Corporate Website: [https://www.eco-movement.com](https://www.eco-movement.com)
  * Developer Docs: [https://developers.eco-movement.com](https://developers.eco-movement.com)
  * AWS Marketplace: [Eco-Movement EV Charging Station Location & Tariffs Data API](https://aws.amazon.com/marketplace/pp/prodview-6jp5e5z6py3su)
* **Registration Procedure**:
  1. Contact sales via their website or initiate a subscription via AWS Marketplace / EIT Urban Mobility Marketplace.
  2. Sign a Data Licensing Agreement specifying target geographic regions (e.g., Luxembourg / Greater Region or EU-wide).
  3. Receive outbound OCPI 2.2 / REST API token credentials (`Authorization: Token <key>`) to query `/v2/locations` or stream real-time socket availability updates.
* **Pricing**:
  * Enterprise B2B SaaS pricing model based on geographic scope, requested data attributes (static metadata vs. live availability vs. tariffs), and API call volume / number of tracked connectors.

---

### Enovos (Encevo Group)
* **Overview**: Enovos is Luxembourg's primary energy supplier and main commercial partner behind the national **Chargy** and **SuperChargy** networks (operated technically by Creos Luxembourg). They manage public charging billing, RFID cards, and eMSP/CPO roaming.
* **Website & Documentation**:
  * Corporate Website: [https://www.enovos.lu](https://www.enovos.lu)
  * Chargy Portal: [https://chargy.lu](https://chargy.lu) & [https://my.chargy.lu](https://my.chargy.lu)
* **Registration Procedure**:
  * **For B2B / eMSP Roaming (API Access)**:
    1. Contact Enovos Mobility / Creos B2B roaming team (`charge@enovos.eu` / `info@chargy.lu`).
    2. Establish an OCPI roaming agreement directly or connect via roaming clearinghouses like **Hubject (Intercharge)** or **GIREVE**.
    3. Exchange OCPI credentials tokens (`/ocpi/cpo/2.2.1/versions`).
  * **For End-Users**:
    * Register an account at `my.chargy.lu` or request an Enovos mKaart / Chargy card.
* **Pricing**:
  * Public AC Tariffs (Chargy): ~€0.35 – €0.45 per kWh (varies by mobility service provider card).
  * B2B Roaming Fees: Negotiated per roaming contract (bilateral OCPI or clearinghouse fee per session/kWh).

---

### Swio (SWIO)
* **Overview**: Swio is a joint venture between **Losch Luxembourg** (importing VW Group vehicles) and **Enovos**. They provide turnkey EV charging solutions, B2B backend management software (OCPP/OCPI) for corporate fleets, commercial car parks, and underground P+R facilities in Luxembourg.
* **Website & Documentation**:
  * Corporate Website: [https://swio.lu](https://swio.lu)
* **Registration Procedure**:
  1. **B2B & Fleet Management**: Submit an inquiry via [https://swio.lu/contact](https://swio.lu/contact) to set up a B2B account on the SWIO Back-Office platform.
  2. **eMSP / Data Integration**: Request a bilateral OCPI agreement with Swio's CPO platform to receive socket-level telemetry for Swio-operated charging locations.
* **Pricing**:
  * Back-Office SaaS Fee: ~€5 – €15 per charge point / month for CPO backend monitoring & OCPI gateway management.
  * Hardware & Installation: Project-based quotes for hardware setup and smart load management.

---

## 2. Investigation: Why Chargy Website Displays P+R Bouillon Availability

### Observed Website State for P+R Bouillon
* **Connectors**: 68 total connectors (22 kW, Type 2)
* **Real-time Status**: 57 available connectors, 11 occupied connectors

### Technical Analysis & Data Flow

1. **Chargy Frontend Map Architecture**:
   - The public website (`https://chargy.lu`) renders an interactive web map powered by a backend API endpoint (`https://my.chargy.lu/b2bev-external-services/resources/kml` or internal JSON API).
   - At the hub location (P+R Bouillon, 61 Rue de Bouillon, 1248 Luxembourg), all physical charge points are grouped under a single master station node (e.g. *"Chargy Ok - Parking aérien à étages BOUILLON"* / *"Chargy P+R Bouillon"*).

2. **Why the Website Shows Detailed Connector Counts**:
   - The Chargy web portal connects directly to Creos's internal CPO Back-Office (OCPP Central System), which maintains live WebSocket/OCPP heartbeat and status notifications (`StatusNotification`: `Available`, `Preparing`, `Charging`, `SuspendedEVSE`, `Finishing`, `Faulted`).
   - The website aggregates these 68 physical EVSEs in real time, summing up the count of `Available` EVSEs (57) vs. occupied/charging EVSEs (11).

3. **Public KML Feed vs. Web Display**:
   - **Public KML Feed (`CHARGY_API_KEY`)**: When queried via external applications, the Chargy KML API parses location placemarks. In the KML representation, multi-connector hubs like P+R Bouillon are represented either as single aggregated placemarks with connector totals or split placemarks.
   - **Authentication Requirement**: Accessing `https://my.chargy.lu/b2bev-external-services/resources/kml` directly returns HTTP 401 Unauthorized unless the `API-KEY` query parameter is provided.
   - **Data Granularity**: When parsed with a valid key, Chargy's public feed returns `availableConnectors` and `totalConnectors` for the placemark node. If an application queries Chargy for P+R Bouillon, it receives `totalConnectors = 68` and `availableConnectors = 57` (or the corresponding live placemark values), which match the web portal's totals.
