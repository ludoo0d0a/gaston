## Gaston – Google Play migration & setup

This doc is a checklist to move **Gaston** to a new Google Play / Google Cloud / Firebase setup without leaking secrets into git.

### 1) App identity

- **Package / applicationId**: `fr.geoking.gaston`
- **Android namespace**: `fr.geoking.gaston`
- **KMP shared namespace**: `fr.geoking.gaston.shared`

If you change the package again later, you must also update:
- Play Console app entry (a new package means a new Play app)
- OAuth client IDs, API key restrictions, SHA-1/SHA-256 fingerprints
- Firebase app registration (Android app)

### 2) Signing (Play App Signing)

- **Do not commit keystores** (`*.jks`, `*.keystore`). This repo ignores them via `.gitignore`.
- Preferred setup:
  - Enable **Play App Signing**
  - Upload an **upload key** (kept offline / in a secret manager)

**Local build signing**
- Use `local.properties` to point to your keystore path and passwords (local only).
- Or use environment variables in CI (GitHub Actions secrets).

### 3) Google Maps SDK for Android

Gaston uses the Maps SDK and requires an API key.

Checklist:
- In **Google Cloud Console**:
  - Enable **Maps SDK for Android**
  - Create an **API key**
  - Restrict it to Android apps:
    - Package name: `fr.geoking.gaston`
    - Add SHA-1 + SHA-256 fingerprints for:
      - debug keystore (development)
      - upload/release signing key (release)

Where to put it:
- **Local**: `local.properties` (NOT committed)
  - `GOOGLE_MAPS_KEY=...`
- **CI**: set `GOOGLE_MAPS_KEY` in GitHub Actions secrets.

### 4) Firebase (Auth / Firestore)

This project uses Firebase Auth + Firestore for account/settings sync.

Checklist:
- Create / select a **Firebase project**
- Add an **Android app** with:
  - Package: `fr.geoking.gaston`
- Download `google-services.json`
  - Place it at `androidApp/google-services.json`
  - **Do not commit it** (ignored by `.gitignore`)
- In Firebase console:
  - Enable **Authentication** providers you need
  - Create Firestore DB (if used) and set rules

### 5) Google Sign-In / Credential Manager (Web client ID)

Gaston expects a server client id string in BuildConfig:
- `GOOGLE_WEB_CLIENT_ID`

Checklist:
- In Google Cloud Console:
  - Configure OAuth consent screen
  - Create an OAuth **Web application** client
  - Copy the **Web client ID**

Where to put it:
- **Local**: `local.properties` (NOT committed)
  - `GOOGLE_WEB_CLIENT_ID=...`
- **CI**: GitHub Actions secret `GOOGLE_WEB_CLIENT_ID`

### 6) Secrets policy (repo hygiene)

Never commit:
- `local.properties`
- `androidApp/google-services.json`
- any keystore files
- any base64-encoded keystore dumps

This repo’s `.gitignore` already covers these.

### 7) Release checklist

- Bump versionCode/versionName in CI or `local.properties` if you use local overrides.
- Build:
  - `./gradlew :androidApp:bundlePlaystoreRelease`
- Upload the AAB in Play Console.
- Verify Android Auto category and templates comply with the declared category.

