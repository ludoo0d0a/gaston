# Google Maps API Key Setup

Gaston uses **Google Maps** for the map screen (mobile and Android Auto). You need a Google Maps API key for the map to work.

For POI / fuel / EV data source keys, see [`API_KEYS.md`](API_KEYS.md).

## Quick Setup

Add to `local.properties` in the project root (same key name as env var):

```properties
GOOGLE_MAPS_KEY=YOUR_GOOGLE_MAPS_API_KEY
```

## Step-by-Step Guide

### 1. Open Google Cloud Console

- Go to [https://console.cloud.google.com/](https://console.cloud.google.com/)

### 2. Create or Select a Project

- Use an existing project or create a new one from the project dropdown.

### 3. Enable Maps SDK for Android

- Go to **APIs & Services** → **Library**
- Search for **Maps SDK for Android**
- Click **Enable**

Direct link: [Enable Maps SDK for Android](https://console.cloud.google.com/apis/library/maps-android-backend.googleapis.com)

### 4. Create an API Key

- Go to **APIs & Services** → **Credentials**
- Click **Create credentials** → **API key**
- Copy the generated key
- Add it to `local.properties` as `GOOGLE_MAPS_KEY=YOUR_KEY`

Direct link: [Create API Key](https://console.cloud.google.com/apis/credentials)

### 5. Restrict the Key (Recommended for Production)

- Edit the API key in Credentials
- Under **Application restrictions**, select **Android apps**
- Add your app:
  - Package name: `fr.geoking.gaston`
  - SHA-1 certificate fingerprint (see below)
- Under **API restrictions**, restrict to **Maps SDK for Android**

### 6. Get Your SHA-1 Fingerprint

For debug builds, run:

```bash
cd androidApp && ./gradlew signingReport
```

Or in Android Studio: **Gradle** → **androidApp** → **Tasks** → **android** → **signingReport**.

---

## Reference

| Item | Value |
|------|-------|
| Property in `local.properties` / env | `GOOGLE_MAPS_KEY` |
| App package name | `fr.geoking.gaston` |
| Google Cloud Console | https://console.cloud.google.com/ |
| Enable Maps SDK for Android | https://console.cloud.google.com/apis/library/maps-android-backend.googleapis.com |
| Create API Key | https://console.cloud.google.com/apis/credentials |

---

## Troubleshooting

- **Map shows blank/gray**: API key missing or invalid. Check `local.properties` (key `GOOGLE_MAPS_KEY`) and that Maps SDK for Android is enabled.
- **Map works in debug but not release**: Add your release keystore SHA-1 to the API key restrictions.
- **Billing**: Google Maps requires a billing account, but includes a free monthly credit (as of 2024: $200/month).
