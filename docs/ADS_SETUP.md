## Ad banner (AdMob) setup

This project shows an **AdMob banner** on the **phone home dashboard** (`PhoneDashboardScreen`) for the **`playstore`** flavor only.

### What’s implemented

- **SDK**: Google Mobile Ads SDK (`com.google.android.gms:play-services-ads`)
- **Init**: `MobileAds.initialize(...)` in `GastonApplication` (only when `BuildConfig.IS_PLAYSTORE_DISTRIBUTION == true`)
- **UI**: Compose wrapper around `AdView` in `androidApp/src/main/kotlin/fr/geoking/gaston/ui/components/AdMobBanner.kt`
- **Placement**: Top of the dashboard list in `PhoneDashboardScreen`

### Required configuration

AdMob requires:

- An **AdMob App ID** (goes in `AndroidManifest.xml` as `com.google.android.gms.ads.APPLICATION_ID`)
- A **banner Ad Unit ID** (used by the dashboard banner)

This repo reads both from `local.properties` (or CI env vars) so secrets aren’t committed.

### Add keys locally

Add these to your (uncommitted) `local.properties`:

```properties
# AdMob
ADMOB_APP_ID=ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy
ADMOB_BANNER_UNIT_ID=ca-app-pub-xxxxxxxxxxxxxxxx/zzzzzzzzzz
```

### CI / release configuration

Set these environment variables on your build job that runs Gradle:

- `ADMOB_APP_ID`
- `ADMOB_BANNER_UNIT_ID`

### Test IDs (safe defaults)

If you don’t set anything, the app uses Google’s **official test IDs**:

- App ID: `ca-app-pub-3940256099942544~3347511713`
- Banner unit ID: `ca-app-pub-3940256099942544/6300978111`

These are fine for local development and CI builds.

### Play Console / policy notes

- Ensure the app’s [Data safety] declarations match your ad usage.
- If you use mediation, update Proguard/R8 rules as required by your adapters.
- If you have a consent flow (EEA/UK), integrate UMP before serving personalized ads.

