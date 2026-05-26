# Sync Play App Signing → Firebase (Google Sign-In on Play builds)

When you install Gaston from the **Play Store** (internal / closed / production), the APK is signed with Google’s **App signing key certificate**, not your debug keystore. Firebase must know that certificate’s SHA-1 and SHA-256, or **Google Sign-In fails** on Play builds.

This repo provides a local script: `scripts/sync_firebase_play_signing.sh`.

Related: [`GOOGLE_PLAY_MIGRATION.md`](GOOGLE_PLAY_MIGRATION.md) §4–5, [`ENV_VARS.md`](ENV_VARS.md) (`GOOGLE_SERVICES_JSON`, `SERVICE_ACCOUNT_JSON`).

---

## Required local files (`androidApp/`)

All paths are **gitignored** — create them on your machine only.

| File | Role | How to obtain |
|------|------|----------------|
| `androidApp/service-account.json` | Authenticates Firebase Management API | Same JSON as GitHub secret `SERVICE_ACCOUNT_JSON` (Play upload SA), **or** a dedicated SA with Firebase Admin / `firebase.projects.update` on project `gaston-c8f44`. [IAM service accounts](https://console.cloud.google.com/iam-admin/serviceaccounts?project=gaston-c8f44) |
| `androidApp/deployment_cert.der` | Play **App signing key certificate** (DER) | Play Console → **Gaston** → **Protected with Play** → **App signing** → **App signing key certificate** → **Download certificate** |
| `androidApp/google-services.json` | Source for Firebase Android app ID + Gradle Firebase config | [Firebase → Project settings](https://console.firebase.google.com/project/gaston-c8f44/settings/general) → Android app `fr.geoking.gaston` → **Download google-services.json** |

### Check presence

```bash
./scripts/sync_firebase_play_signing.sh --check
```

Example output when something is missing:

```text
Required files (under androidApp/, not committed):
  MISS service-account.json
  OK   deployment_cert.der
  OK   google-services.json
  OK   mobilesdk_app_id → 1:305319734071:android:0a5bbce83d2bd52b2688b2
❌ Fix missing files (see docs/FIREBASE_PLAY_SIGNING_SYNC.md).
```

---

## Firebase Android app ID (`mobilesdk_app_id`)

The script reads it from `google-services.json` (no manual copy in the normal case):

```json
"client_info": {
  "mobilesdk_app_id": "1:305319734071:android:0a5bbce83d2bd52b2688b2",
  "android_client_info": { "package_name": "fr.geoking.gaston" }
}
```

- **Console label:** Firebase → Project settings → Your apps → Android → **App ID** (same value).
- **Override:** `--firebase-android-app-id '1:305319734071:android:…'`

The script selects the client whose `package_name` is `fr.geoking.gaston` (not blindly `client[0]`).

---

## Play App Signing is ON

If Play Console shows **App signing key certificate** and **Upload key certificate**, use fingerprints from **App signing key certificate** (what users install), not the upload key.

Navigation (UI may vary):

- **Protected with Play** → **App signing**  
  or direct: App signing / key management for Gaston.

---

## Run the sync

Prerequisites: `jq`, `openssl`, `curl`.

```bash
./scripts/sync_firebase_play_signing.sh
```

Defaults:

- `--service-account-json` → `androidApp/service-account.json`
- `--cert-der` → `androidApp/deployment_cert.der`
- `--out` → `androidApp/google-services.json`
- App ID from existing `google-services.json` when present

The script:

1. Computes SHA-1 / SHA-256 from `deployment_cert.der`
2. Adds them to Firebase (if not already registered)
3. Downloads an updated `google-services.json` via the Management API

### After a successful run

1. Update GitHub Actions secret **`GOOGLE_SERVICES_JSON`** with the new file contents (verbatim).
2. Re-run **Deploy to Google Play Store** (or wait for the next `main` push).
3. Install from the internal track and test Google Sign-In.

---

## Service account: copy from CI

If you already have `SERVICE_ACCOUNT_JSON` in GitHub and not locally:

```bash
# Paste secret into a file (do not commit)
gh secret get GOOGLE_SERVICES_JSON  # wrong secret name — use your editor / gh api for SERVICE_ACCOUNT_JSON
# Or export from Play Console / GCP and save as:
#   androidApp/service-account.json
```

The Play upload service account must also be allowed to manage Firebase (same GCP project `gaston-c8f44` is typical). If the API returns 403, grant **Firebase Admin** or **Editor** on the project to that service account.

---

## Troubleshooting

| Symptom | Likely cause |
|---------|----------------|
| `DEVELOPER_ERROR` / Sign-In fails on Play only | App signing SHA not in Firebase — run this script |
| Sign-In works in debug, fails from Play | Expected until Play signing SHA is registered |
| `MISS service-account.json` | Copy SA JSON to `androidApp/service-account.json` |
| `Failed to obtain access token` | Invalid SA JSON or clock skew |
| Firebase API 403 | SA lacks Firebase Management permissions |
| Wrong app ID | Regenerate / download `google-services.json` for package `fr.geoking.gaston` |

Debug SHA (local `assembleFullDebug`) is separate; keep both debug and Play signing fingerprints in Firebase if you test both install paths.
