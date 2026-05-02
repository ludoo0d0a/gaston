# Environment variables and local.properties

The same key name is used in **local.properties** and as an **environment variable**.

## SDK path (required in CI)

The Android Gradle Plugin reads `sdk.dir` from `local.properties` (no env alias). In CI, create the file before running Gradle:

```bash
echo "sdk.dir=$ANDROID_HOME" >> local.properties
```

## Keys (local.properties and env)

Use these names in `local.properties` or set the same name as an env var (e.g. for CI/GitHub Secrets).

| Key | Usage |
|-----|--------|
| `VERSION_CODE` | Optional integer override for versionCode (e.g. `123`). |
| `GOOGLE_MAPS_KEY` | Google Maps API key. **Required for map screen** (tiles); without it the map stays grey. |
| `GOOGLE_WEB_CLIENT_ID` | Google Sign-In **Web client ID**: copy from Firebase [Authentication → Sign-in method → Google](https://console.firebase.google.com/project/gaston-c8f44/authentication/providers) (`gaston-c8f44`). Details: `docs/GOOGLE_PLAY_MIGRATION.md` §5. |
| `OPENCHARGEMAP_KEY` | Optional Open Charge Map API key (if you enable that provider). |
| `MOBILITEIT_LUXEMBOURG_KEY` | Optional API key for Luxembourg mobiliteit.lu (if used). |
| `TOMTOM_KEY` | Optional TomTom key (only if a TomTom provider is enabled). |
| `CHARGY_API_KEY` | Optional API key for Luxembourg Chargy (if used). Defaults to public key. |
| `GERMANY_TANKERKOENIG_KEY` | Optional API key for Germany Tankerkoenig. Defaults to demo key. |

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

These are **not** Gradle / `local.properties` keys. They are used by [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) (see [`PLAY_STORE_GUIDE.md`](../PLAY_STORE_GUIDE.md)).

| Secret | What it is |
|--------|------------|
| `SERVICE_ACCOUNT_JSON` | The **entire JSON** of a [Google Cloud service account key](https://console.cloud.google.com/iam-admin/serviceaccounts) (type **JSON**) for an account that is invited in [Play Console → Users and permissions](https://play.google.com/console) with release/upload rights. It authenticates the **Google Play Android Developer API** so CI can upload the AAB. **This is unrelated to Firebase** (`google-services.json` and `GOOGLE_WEB_CLIENT_ID` are separate). Create the service account in the **same GCP project that Play Console is linked to** (often `gaston-c8f44` if Firebase and Play share one project). |

Also required for that workflow: `SIGNING_KEY`, `KEY_STORE_PASSWORD`, `ALIAS`, `KEY_PASSWORD` (documented in the same guide).
