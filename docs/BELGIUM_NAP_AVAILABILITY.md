# Belgium NAP EV availability (transportdata.be)

Real-time EVSE availability for **Belgium**, using an open dataset registered on Belgium’s National Access Point ([transportdata.be](https://transportdata.be)).

## Source used (no API key)

| | |
|---|---|
| **NAP dataset** | [Road Public Charging Network](https://transportdata.be/dataset/road-public-charging-network) |
| **Feed** | OCPI-style locations JSON (status embedded per EVSE) |
| **URL** | `https://roaming.road.io/files/9ef09c78-2666-418a-aa45-4f2261e2e305/locations.json?force=true` |
| **API key** | None |
| **Coverage** | Selected CPOs on the Road / E-Flux network (not every Belgian CPO) |

Always on when the map center is inside the Belgium bounding box (checked **before** the France bbox so Brussels is not routed to QualiCharge).

## Other NAP resources (not used yet) — where to request access

### Eco-Movement DATEX II (Belgium AFIR)

| | |
|---|---|
| **Endpoints** | `https://nap-be.eco-movement.com/datex2/v1/locations` (static) · `…/status/{evse_id}` (dynamic) |
| **NAP listing** | [Public charging infrastructure dynamic/static dataset selected CPOs (DATEX II)](https://transportdata.be/organization/eco-movement) |
| **Auth today** | **401 Unauthorized** without credentials (not free pull) |
| **Self-service?** | No |

**Who to contact for a key / token:**

| Contact | Use |
|---------|-----|
| **support@eco-movement.com** | Dataset contact on transportdata.be (Peter Hanekamp) — NAP DATEX access questions |
| **partners@eco-movement.com** | Partnerships / AFIR / NAP compliance |
| [developers.eco-movement.com](https://developers.eco-movement.com) | Data API docs; OCPI handshake / token via an Eco-Movement representative |

Same commercial relationship as the existing Gaston OCPI key (`ECO_MOVEMENT_KEY`) — see [`API_KEYS.md`](API_KEYS.md#eco-movement-ev-eu--global-ocpi-221). A DATEX NAP credential may be separate from the OCPI Data API token; ask when contacting them.

### EnergyVision (AFIR / DATEX)

| | |
|---|---|
| **NAP listing** | [EnergyVision Public Charging Network (AFIR / DATEX II)](https://transportdata.be/dataset/energyvision-public-charging-network-locations-afir-ocpi-2-2-1) |
| **Access** | By request (resource URL listed as “Communicated via email”) |
| **Contact** | **myevplatform@energyvision.be** |

### Monta / other AFIR feeds

Commercial / partner APIs — request via the provider’s portal or sales (see Monta public API docs if needed). Not wired in Gaston.

## In the codebase

- **Client:** `shared/.../api/belgiumnap/BelgiumNapAvailabilityClient.kt` — fetch/parse/cache (~60s), radius filter
- **Provider:** `BelgiumNapAvailabilityProvider` implements `BorneAvailabilityProvider`
- **Factory:** Belgium NAP for BE; QualiCharge (+ Belib in Paris) for FR; Eco-Movement OCPI as fallback elsewhere (LU, DE, NL, …). Country routing uses [ParkingRegion](../shared/src/commonMain/kotlin/fr/geoking/gaston/parking/ParkingRegion.kt) sub-boxes (BE/LU do not overlap).

Status mapping (OCPI → app): `AVAILABLE` → Available, `CHARGING`/`BLOCKED` → Occupied, `OUTOFORDER`/`INOPERATIVE` → Maintenance, `REMOVED` skipped.

Listed in Settings → About / sources and [`sources.md`](sources.md).
