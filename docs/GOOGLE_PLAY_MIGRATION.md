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

### 4) Firebase (Auth / Firestore / Crashlytics)

This project uses Firebase Auth + Firestore for account/settings sync, and Crashlytics (+ Analytics breadcrumbs) for crash reporting.

**Firebase project ID (Gaston production):** `gaston-c8f44`

Use these links (sign in with a Google account that has access to the project):

| Area | URL |
|------|-----|
| Project overview | https://console.firebase.google.com/project/gaston-c8f44/overview |
| Project settings (apps, SHA keys, `google-services.json`) | https://console.firebase.google.com/project/gaston-c8f44/settings/general |
| **`GOOGLE_WEB_CLIENT_ID`** — Authentication → Sign-in method → **Google** (Web client ID) | https://console.firebase.google.com/project/gaston-c8f44/authentication/providers |
| Authentication → Users (verify sign-ins) | https://console.firebase.google.com/project/gaston-c8f44/authentication/users |
| Crashlytics | https://console.firebase.google.com/project/gaston-c8f44/crashlytics |
| Linked Google Cloud → OAuth credentials (Web client ID) | https://console.cloud.google.com/apis/credentials?project=gaston-c8f44 |

Checklist:
- Open the **Firebase project** above (or create one and align the ID in this doc if it changes).
- Add an **Android app** with:
  - Package: `fr.geoking.gaston`
- Register **SHA-1** and **SHA-256** fingerprints for debug and release signing keys on the Android app in Project settings.
- For **Play Store installs** (Play App Signing ON), register the **App signing key certificate** SHA-1/SHA-256 — see [`FIREBASE_PLAY_SIGNING_SYNC.md`](FIREBASE_PLAY_SIGNING_SYNC.md) and `./scripts/sync_firebase_play_signing.sh --check`.
- Download `google-services.json`
  - Place it at `androidApp/google-services.json`
  - **Do not commit it** (ignored by `.gitignore`)
- In Firebase console:
  - Enable **Authentication** providers you need (at minimum **Google** for account sign-in in the app)
  - Create Firestore DB (if used) and set rules
  - Open **Crashlytics** once (builds the dashboard); optionally link **Google Analytics** for Crashlytics breadcrumbs

### 5) Google Sign-In / Credential Manager (Web client ID)

Gaston expects a server client id string in BuildConfig:
- `GOOGLE_WEB_CLIENT_ID`

**Find `GOOGLE_WEB_CLIENT_ID` in Firebase Console (preferred)**

1. Open **Authentication → Sign-in method** for project `gaston-c8f44`:  
   https://console.firebase.google.com/project/gaston-c8f44/authentication/providers  
2. Click **Google** in the providers list (enable it first if it is off; saving creates the OAuth clients if needed).
3. On the Google provider screen, copy the **Web client ID** (sometimes labeled **Web SDK configuration**). It looks like `….apps.googleusercontent.com`.  
   That string is your `GOOGLE_WEB_CLIENT_ID`.

If the field is not visible or you manage clients manually, use the linked GCP **Credentials** page in the table above and copy the **Client ID** of the OAuth **Web client** type.

Where to put it:
- **Local**: `local.properties` (NOT committed)
  - `GOOGLE_WEB_CLIENT_ID=...`
- **CI**: GitHub Actions secret `GOOGLE_WEB_CLIENT_ID`

### 6) Secrets policy (repo hygiene)

Never commit:
- `local.properties`
- `androidApp/google-services.json`
- `androidApp/service-account.json`
- `androidApp/deployment_cert.der`
- any keystore files
- any base64-encoded keystore dumps

This repo’s `.gitignore` already covers these.

### 7) Release checklist

- Bump versionCode/versionName in CI or `local.properties` if you use local overrides.
- Build:
  - `./gradlew :androidApp:bundleRelease`
- Upload the AAB in Play Console.
- Verify Android Auto category and templates comply with the declared category.

