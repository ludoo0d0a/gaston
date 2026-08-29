# Environment variables and local.properties

The same key name is used in **local.properties** and as an **environment variable**.

## SDK path (required in CI)

The Android Gradle Plugin reads `sdk.dir` from `local.properties` (no env alias). In CI, create the file before running Gradle:

```bash
echo "sdk.dir=$ANDROID_HOME" >> local.properties
```

## Keys (local.properties and env)

Use these names in `local.properties` or set the same name as an env var (e.g. for CI/GitHub Secrets).

**How to obtain each key:** see [`API_KEYS.md`](API_KEYS.md) (registration links, auth headers, and which providers need no key).

| Key | Usage |
|-----|--------|
| `VERSION_CODE` | Optional integer override for versionCode (e.g. `123`). |
| `GOOGLE_MAPS_KEY` | Google Maps API key. **Required for map screen** (tiles); without it the map stays grey. Setup: [`MAPS_API_KEY_SETUP.md`](MAPS_API_KEY_SETUP.md). |
| `GOOGLE_WEB_CLIENT_ID` | Google Sign-In **Web client ID**: copy from Firebase [Authentication → Sign-in method → Google](https://console.firebase.google.com/project/gaston-c8f44/authentication/providers) (`gaston-c8f44`). Details: `docs/GOOGLE_PLAY_MIGRATION.md` §5. |
| `OPENCHARGEMAP_KEY` | Open Charge Map API key (recommended if you enable that provider). |
| `ECO_MOVEMENT_KEY` | Eco-Movement OCPI token (`Authorization: Token …`). Also overridable in Settings. |
| `NOBIL_API_KEY` | NOBIL datadump (Norway/Sweden EV availability). Free CC-BY key from nobil.no. |
| `NREL_AFDC_KEY` | NREL/AFDC Alternative Fuel Stations (US + Canada EV inventory/status). Free key from developer.nrel.gov. |
| `ECONTROL_EV_API_KEY` | Austria E-Control Ladestellenverzeichnis (charge) API key. |
| `ECONTROL_EV_REFERER_DOMAIN` | Registered hostname for E-Control EV API `Referer` (e.g. `geoking.fr`). |
| `EIPA_EXPORT_KEY` | Optional Poland EIPA export key (defaults to public map-reader key). |
| `FUELPRICES_DK_KEY` | Fuelprices.dk API key (Denmark). Also overridable in Settings. |
| `NSW_FUELCHECK_KEY` | NSW FuelCheck consumer key (Australia). Also overridable in Settings. |
| `NSW_FUELCHECK_SECRET` | NSW FuelCheck consumer secret. Also overridable in Settings. |
| `MOBILITEIT_LUXEMBOURG_KEY` | Luxembourg mobiliteit.lu / HAFAS OpenData API key (transit). |
| `TOMTOM_KEY` | TomTom Traffic API key (global traffic fallback). |
| `CHARGY_API_KEY` | Chargy Luxembourg KML feed API key. |
| `GERMANY_TANKERKOENIG_KEY` | Tankerkönig API key (demo: `00000000-0000-0000-0000-000000000002`). |
| `EIA_KEY` | EIA Open Data API key (US petroleum/pri state retail prices). Optional — OSM stations still load without it. |
| `FASTNED_UK_KEY` | Fastned UK OCPI API key (`x-api-key` header). |
| `DKV_SUBSCRIPTION_KEY` | DKV Mobility Azure APIM subscription key (`Ocp-Apim-Subscription-Key`) for `api.dkv-mobility.com`. Guide: [`DKV_OCPI.md`](DKV_OCPI.md). |
| `DKV_AUTHORIZATION` | Optional DKV `Authorization` header (`Bearer …` or OCPI `Token …`) if required by your product. |
| `ROMANIA_PECO_APPLICATION_ID` | Parse application id for Peco Online (Romania). |
| `ROMANIA_PECO_CLIENT_KEY` | Parse client key for Peco Online (Romania). |

Keys are read at **build time** in this order: `local.properties` then environment variables. In CI, set env vars on the step that runs Gradle (e.g. `env:` in the build job).

## Example local.properties

```properties
sdk.dir=/path/to/your/android/sdk
GOOGLE_MAPS_KEY=...
GOOGLE_WEB_CLIENT_ID=...
```

## GitHub Actions example

```yaml
- name: Set up local.properties for SDK
  run: echo "sdk.dir=$ANDROID_HOME" >> local.properties

- name: Build
  env:
    VERSION_CODE: ${{ secrets.VERSION_CODE }}
    GOOGLE_MAPS_KEY: ${{ secrets.GOOGLE_MAPS_KEY }}
    GOOGLE_WEB_CLIENT_ID: ${{ secrets.GOOGLE_WEB_CLIENT_ID }}
  run: ./gradlew :androidApp:assembleFullRelease
```

Create the secrets in **Settings → Secrets and variables → Actions** and add only the keys your build needs.

## GitHub secrets — Play Store deploy only

These are **not** Gradle / `local.properties` keys. They are used by [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) (see [`PLAY_STORE_GUIDE.md`](PLAY_STORE_GUIDE.md)).

| Secret | What it is |
|--------|------------|
| `SERVICE_ACCOUNT_JSON` | The **entire JSON** of a [Google Cloud service account key](https://console.cloud.google.com/iam-admin/serviceaccounts) (type **JSON**) for an account that is invited in [Play Console → Users and permissions](https://play.google.com/console) with release/upload rights. It authenticates the **Google Play Android Developer API** so CI can upload the AAB. **This is unrelated to Firebase** (`google-services.json` and `GOOGLE_WEB_CLIENT_ID` are separate). Create the service account in the **same GCP project that Play Console is linked to** (often `gaston-c8f44` if Firebase and Play share one project). |
| `GOOGLE_SERVICES_JSON` | **Raw JSON contents** of `androidApp/google-services.json` (downloaded from [Firebase → Project settings](https://console.firebase.google.com/project/gaston-c8f44/settings/general)). Paste the file verbatim — GitHub Actions secrets accept multi-line text. The workflow writes it back to `androidApp/google-services.json` before Gradle runs so the Google Services plugin embeds Firebase config (Auth, Firestore, Crashlytics, …) in the AAB. Without it, Google Sign-In fails at runtime with *"Authentication unavailable (Firebase initialization failed)"*. Refresh the secret whenever you re-download the file (e.g. after adding SHA-1 fingerprints). |

Also required for that workflow: `SIGNING_KEY`, `KEY_STORE_PASSWORD`, `ALIAS`, `KEY_PASSWORD` (documented in the same guide).
