# DKV Mobility OCPI (EV stations)

Gaston’s DKV provider pulls **OCPI 2.2.1 Locations** (and tariffs) from DKV Mobility’s Azure API Management gateway. Credentials are **not self-service** on the developer portal.

## Quick reference

| | |
|---|---|
| **API host (calls)** | `https://api.dkv-mobility.com/ocpi/cpo/2.2.1` |
| **Developer portal (docs only)** | [api-portal.dkv-mobility.com](https://api-portal.dkv-mobility.com/) |
| **EAPI overview** | [eapi-one-page](https://api-portal.dkv-mobility.com/eapi-one-page) |
| **Auth** | `Ocp-Apim-Subscription-Key` + optional `Authorization: Bearer …` or `Token …` |
| **Gaston keys** | `DKV_SUBSCRIPTION_KEY` (required), `DKV_AUTHORIZATION` (optional) |
| **Code** | `shared/.../api/dkv/DkvOcpiClient.kt`, `DkvOcpiProvider.kt` |

Do **not** call `https://api-portal.dkv-mobility.com/ocpi/...` — that host serves the HTML portal and returns **404 HTML**. The real APIM gateway is `api.dkv-mobility.com`.

```bash
# Expect JSON (often 403 Access Denied until credentials/product are valid)
curl -sS -o /dev/null -w "%{http_code}\n" \
  -H "Ocp-Apim-Subscription-Key: $DKV_SUBSCRIPTION_KEY" \
  -H "Accept: application/json" \
  "https://api.dkv-mobility.com/ocpi/cpo/2.2.1/locations?limit=1&offset=0"
```

## How to obtain credentials

Portal signup alone is **not** enough for production APIs. Official rule: *API access cannot be requested via the API Portal* — see [Customer onboarding](https://api-portal.dkv-mobility.com/content/html_widgets/rr03k.html).

### 1. Be a DKV customer

If you are not already a customer, contact **DKV Sales** or **Customer Service**, sign contracts / GTC, and get a customer number.

### 2. Request Enterprise API onboarding

Contact **DKV Customer Service** (or your sales counterpart) and ask for an **Enterprise API Customer Connection** (internal Matrix42 request).

Provide:

- main company name  
- main **customer number** (and any other customer numbers that should share the technical user)  
- e-mail  
- **mobile phone** (required for the client secret SMS)  
- legal documents they ask you to sign  

Typical lead time: **7–10 days**.

If you have no dedicated contact yet: [api-management@dkv-mobility.com](mailto:api-management@dkv-mobility.com) ([FAQ](https://api-portal.dkv-mobility.com/faqs)).

### 3. What you receive

| Channel | Credential | Use |
|---------|------------|-----|
| **E-mail** | Technical user name, **client_id**, **subscription key** | Identity + APIM product access |
| **SMS** | **client_secret** | OAuth2 client credentials |

The onboarding mail usually includes sample request shapes. Keep the **technical username** for later change requests (not the client_id).

### 4. Ask explicitly for OCPI / e-Mobility Locations

The public Enterprise API docs focus on **transactions, masterdata (cards), toll**. Gaston needs **OCPI Locations** (charging stations), which may be a separate **e-Mobility** product.

When opening the service request, state clearly that you need:

> OCPI CPO 2.2.1 Locations (and preferably Tariffs / EVSE status) on  
> `https://api.dkv-mobility.com/ocpi/cpo/2.2.1/...`  
> for a mapping / MSP-style consumer app (read-only).

Without that product under the subscription, a valid Enterprise key still returns **403 Access Denied** on `/ocpi/...`.

### 5. Portal account (docs + Try it)

Separately you can [sign up](https://api-portal.dkv-mobility.com/) on the developer portal ([how it works](https://api-portal.dkv-mobility.com/how-to)):

1. Register → confirm e-mail.  
2. Browse products / APIs (including e-Mobility).  
3. Subscribe where allowed → see subscription keys in profile.  
4. Use **Try it** once real credentials are granted.

Portal “Postman Echo” is a demo only; other APIs need the sales/CS grant above.

## Authentication (Enterprise)

Documented on [API Authentication](https://api-portal.dkv-mobility.com/content/html_widgets/uxlt9.html).

### Token endpoint

```bash
curl -sS -X POST \
  'https://my.dkv-mobility.com/auth/realms/enterprise-api/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials' \
  -d 'client_id=YOUR_CLIENT_ID' \
  -d 'client_secret=YOUR_CLIENT_SECRET' \
  -d 'scope=openid'
```

Response includes `access_token` (typically ~**5 minutes** TTL) and `token_type`.

### API call

```bash
curl -sS \
  'https://api.dkv-mobility.com/ocpi/cpo/2.2.1/locations?limit=1&offset=0' \
  -H "Ocp-Apim-Subscription-Key: YOUR_SUBSCRIPTION_KEY" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H 'Accept: application/json'
```

Some products may use OCPI-style `Authorization: Token …` instead of Bearer; use whatever DKV documents for your subscription.

## Gaston configuration

In `local.properties` (or env vars — see [`ENV_VARS.md`](ENV_VARS.md)):

```properties
DKV_SUBSCRIPTION_KEY=...   # from onboarding e-mail / portal subscription
DKV_AUTHORIZATION=Bearer ...   # optional; short-lived OAuth access token, or OCPI Token …
```

| Property | BuildConfig | Notes |
|----------|-------------|--------|
| `DKV_SUBSCRIPTION_KEY` | yes | Sent as `Ocp-Apim-Subscription-Key` |
| `DKV_AUTHORIZATION` | yes | Sent as `Authorization` if non-blank; no automatic OAuth refresh in the app yet |

Wiring: `androidApp/.../di/MapModule.kt` → `DkvOcpiClient` → `DkvOcpiProvider` (`PoiProvider` named `"dkv"`).

## Implementation notes

- OCPI Locations are not radius-queryable in the base client; the provider caches pages in memory and filters by distance (`DkvOcpiProvider`).
- EVSE status → app availability via shared [`OcpiEvseAvailability`](../shared/src/commonMain/kotlin/fr/geoking/gaston/api/common/OcpiEvseAvailability.kt).
- Unit tests: `shared/.../api/dkv/DkvOcpiClientTest.kt` (mocked HTTP).

## Troubleshooting

| Symptom | Likely cause |
|---------|----------------|
| HTML 404 from `api-portal.dkv-mobility.com/ocpi/...` | Wrong host — use `api.dkv-mobility.com` |
| JSON `{"message":"Access Denied."}` (403) | Missing/invalid subscription, wrong product, or missing Bearer/Token |
| 401 / OCPI status ≠ 1000 | Bad or expired `Authorization` |
| Short / placeholder subscription key | Re-check onboarding e-mail; Azure APIM keys are usually long hex strings |

Support: dedicated DKV contact, or `api-management@dkv-mobility.com`.

## Official links

| Topic | URL |
|-------|-----|
| Portal home | https://api-portal.dkv-mobility.com/ |
| EAPI one-pager | https://api-portal.dkv-mobility.com/eapi-one-page |
| How it works | https://api-portal.dkv-mobility.com/how-to |
| FAQs | https://api-portal.dkv-mobility.com/faqs |
| Customer onboarding | https://api-portal.dkv-mobility.com/content/html_widgets/rr03k.html |
| API authentication | https://api-portal.dkv-mobility.com/content/html_widgets/uxlt9.html |
| Subscriptions | https://api-portal.dkv-mobility.com/content/html_widgets/yzdmm.html |

Also summarized in [`API_KEYS.md`](API_KEYS.md#dkv-mobility-ev-ocpi-via-azure-apim) and listed in [`sources.md`](sources.md).
