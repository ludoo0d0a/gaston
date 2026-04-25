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
| `GOOGLE_WEB_CLIENT_ID` | Google Sign-In “Web client ID” (only needed if enabling Google auth). |
| `OPENCHARGEMAP_KEY` | Optional Open Charge Map API key (if you enable that provider). |
| `MOBILITEIT_LUXEMBOURG_KEY` | Optional API key for Luxembourg mobiliteit.lu (if used). |
| `TOMTOM_KEY` | Optional TomTom key (only if a TomTom provider is enabled). |

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
