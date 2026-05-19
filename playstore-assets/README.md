# Play Store Assets — Gaston

Assets for publishing **Gaston** on Google Play.

---

## Store Listing Copy

### App name
```
Gaston
```

### Short description (80 chars max)
```
Fuel & EV stations finder with prices — for Android & Android Auto.
```

### Long description (4 000 chars max)
```
Never run dry again. Gaston is your co-pilot for every road trip, everyday commute, or cross-country journey.

FIND THE CHEAPEST FUEL NEARBY
• Real-time fuel prices for SP95, SP95-E10, SP98, Diesel, Diesel+, GPL and more
• Compare prices across dozens of brands — Shell, TotalEnergies, Leclerc, Auchan, Intermarché and more
• Sort by distance or price to always get the best deal

CHARGE YOUR EV WITH CONFIDENCE
• Locate IRVE charging points (public French network + major operators)
• Filter by connector type: CCS2, CHAdeMO, Type 2, Tesla
• Filter by minimum power: 22 kW AC to 350 kW DC and beyond
• See real-time availability and pricing per kWh where provided

PLAN SMARTER ROUTES
• Find stations along your route, not just around you
• Combine fuel and charging stops in a single search
• Supports route waypoints for long-distance travel

REST STOPS & SERVICES
• Discover toilets, rest areas, picnic spots, camper-van services and parking on your route
• Great for families, truckers, cyclists and road-trippers alike

ANDROID AUTO — EYES ON THE ROAD
• Fully designed for safe in-car use with Android Auto templates
• Big readable cards with station name, distance and price at a glance
• One tap to open navigation in your preferred maps app
• No distractions — only the information you need while driving

FILTERS THAT WORK FOR YOU
• Filter by energy type, brand, connector, minimum power, services and open/closed status
• Filters persist across sessions — set once, drive always

Gaston uses open and public data sources (data availability and freshness vary by country and provider).

Perfect for drivers who want fuel and charging information at a glance — on their phone and in the car.
```

---

## Asset Manifest

### Required (Google Play)

| File | Dimensions | Purpose |
|------|-----------|---------|
| `icon-512.png` | 512 × 512 px | High-resolution app icon (required) |
| `feature-graphic-1024x500.png` | 1024 × 500 px | Store listing banner (required) |

### Phone Screenshots (min. 2, max. 8)

| File | Dimensions | Screen |
|------|-----------|--------|
| `screenshot-1-map.png` | 1080 × 1920 px | Map view — nearest stations with prices |
| `screenshot-2-fuel-prices.png` | 1080 × 1920 px | Station detail — full fuel price list |
| `screenshot-3-ev-charging.png` | 1080 × 1920 px | EV detail — connectors, power, availability |
| `screenshot-4-filters.png` | 1080 × 1920 px | Filters panel — energy, connectors, services |
| `screenshot-5-android-auto.png` | 1080 × 1920 px | Android Auto list view |

Format: PNG · 9:16 · 320–3 840 px per side · max 8 MB each.

---

## How to Upload

1. Open [Google Play Console](https://play.google.com/console)
2. Select your app → **Store presence** → **Main store listing**
3. **App icon** → upload `icon-512.png`
4. **Feature graphic** → upload `feature-graphic-1024x500.png`
5. **Phone screenshots** → upload `screenshot-1-map.png` through `screenshot-5-android-auto.png`

---

## Android Launcher Icons

PNG launcher icons are generated automatically alongside these assets into:

```
androidApp/src/main/res/
  mipmap-mdpi/       ic_launcher.png (48×48)   + ic_launcher_round.png
  mipmap-hdpi/       ic_launcher.png (72×72)   + ic_launcher_round.png
  mipmap-xhdpi/      ic_launcher.png (96×96)   + ic_launcher_round.png
  mipmap-xxhdpi/     ic_launcher.png (144×144) + ic_launcher_round.png
  mipmap-xxxhdpi/    ic_launcher.png (192×192) + ic_launcher_round.png
  mipmap-anydpi-v26/ ic_launcher.xml (adaptive — vector)
```

To regenerate everything after a brand change:
```bash
python3 scripts/gen_assets.py          # icon + feature graphic
./scripts/regenerate_screenshots.sh    # phone screenshots from real Compose UI
```

---

## Brand

| Token | Value | Usage |
|-------|-------|-------|
| Background | `#2D1B4E` | Dark purple — icon bg, screen bg |
| Accent violet | `#7C4DFF` | CTAs, rings, highlights |
| Accent lavender | `#B388FF` | Secondary labels, rings |
| Accent mist | `#D1C4E9` | Tertiary text, rings |
| Sphere | `#6B4EAA` | Icon central sphere |
| Gold | `#FFD228` | Fuel prices |
| EV green | `#40C482` | Electric / charging |
