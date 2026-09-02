# Android Auto map modes — requirements, implementation, trade-offs

Summary of the six Android Auto (AA) map modes work (commit `2e56a28` on `main`).  
For AA template constraints (5-step quota, terminal templates), see [`android-auto.md`](android-auto.md).  
For MapLibre EGL migration background, see [`maplibre_android_auto_audit.md`](maplibre_android_auto_audit.md).

---

## 1. Original goals

The request evolved from “4 tentative tile backends” to **six explicit AA map modes**, with these non-negotiables:

| Requirement | Detail |
|-------------|--------|
| **AA-compatible map** | Zoom, POIs on map, rotatable map, readable street labels despite rotation |
| **Six modes** | Google, MapLibre, Custom raster, MapTiler, Protomaps, Mapsforge |
| **Custom frozen** | `CustomMapPoiScreen` is the reference shell — do not refactor or rename it |
| **Feature parity** | All *other* modes must match Custom: zoom +/-, stations list, map settings, cheapest filter |
| **Manual offline only** | Never auto-download map files on AA; user configures paths on phone |
| **Canvas feedback** | Always show mode + zoom on canvas; banner when offline file/key is missing |
| **Clear mode toggling** | Labeled picker (not opaque cycle); each mode visually distinct in HUD |
| **AA constraints** | Respect 5-step template quota; driver-safe templates only |

Technologies mentioned in discovery but **not** shipped as separate AA modes:

- **OpenMapTiles** — covered indirectly via MapLibre + OpenFreeMap / MapTiler vector styles, not a dedicated enum value.
- **Unified `MapMode` enum** — deferred; phone (`MapEngine`) and AA (`CarMapMode`) settings remain separate.

---

## 2. What was implemented

### 2.1 Mode matrix

| `CarMapMode` | AA screen | Renderer / backend | Network | Offline setup |
|--------------|-----------|-------------------|---------|---------------|
| **Native** (Google) | `NativeMapPoiScreen` | Host `PlaceListMapTemplate` | Online | None |
| **Custom** | `CustomMapPoiScreen` | Raster XYZ tiles → Canvas | Online | None (frozen) |
| **MapLibre** | `MapLibrePoiScreen` | `CarMapLibreRenderer` + OpenFreeMap vector | Online | — |
| **MapTiler** | `MapLibrePoiScreen` | Same renderer, MapTiler style URL | Online | `MAPTILER_KEY` in `local.properties` |
| **Protomaps** | `MapLibrePoiScreen` | Same renderer, local PMTiles | Offline | `offlinePmtilesPath` (manual) |
| **Mapsforge** | `mapsforge.MapsforgePoiScreen` | `CarMapsforgeRenderer` | Offline | `MapsforgeMapManager` / `.map` file |

Central dispatch: [`AutoMapScreenFactory.kt`](../androidApp/src/main/kotlin/fr/geoking/gaston/auto/AutoMapScreenFactory.kt).

### 2.2 Shared canvas shell (non-Custom, non-Google)

MapLibre / MapTiler / Protomaps share one screen class with per-mode config:

- [`CanvasMapModeConfig.kt`](../androidApp/src/main/kotlin/fr/geoking/gaston/auto/CanvasMapModeConfig.kt) — style URL resolver, HUD label, offline flag, renderer factory
- [`AaMapSurfaceRenderer.kt`](../androidApp/src/main/kotlin/fr/geoking/gaston/auto/AaMapSurfaceRenderer.kt) — renderer interface
- [`MapLibrePoiScreen.kt`](../androidApp/src/main/kotlin/fr/geoking/gaston/auto/MapLibrePoiScreen.kt) — `MapWithContentTemplate`, `MapController` zoom, stations list, settings, compass/recenter in header

### 2.3 Canvas HUD & offline UX

[`AutoMapOverlayHelper.kt`](../androidApp/src/main/kotlin/fr/geoking/gaston/auto/AutoMapOverlayHelper.kt):

- **`drawMapInfoStrip`** — top-left chip: `{mode} · Z {zoom}` (always on for canvas modes)
- **`drawOfflineUnavailableBanner`** — centered message when Protomaps/Mapsforge file is missing

Availability logic: [`OfflineMapAvailability.kt`](../androidApp/src/main/kotlin/fr/geoking/gaston/auto/OfflineMapAvailability.kt).

### 2.4 Settings & sync

| Surface | What changed |
|---------|--------------|
| **AA** | `AutoMapModePickerScreen` — 6 labeled rows; entry from `AutoMapSettingsScreen` |
| **Phone** | `SettingsScreen` → Map config: AA mode cards, `offlinePmtilesPath`, `offlineMapsforgePath`; existing Mapsforge download UI for phone engine |
| **Persistence** | `SettingsManager`: `CarMapMode` extended; new path fields |
| **Firestore** | `FirestoreSettingsSync` syncs offline paths |
| **Build** | `MAPTILER_KEY` → `BuildConfig.MAPTILER_KEY` |

### 2.5 Feature parity vs Custom (reference)

| Feature | Custom | Google | MapLibre / MapTiler / Protomaps | Mapsforge |
|---------|--------|--------|----------------------------------|-----------|
| Zoom +/- (`MapController`) | Yes | Host-only* | Yes | Yes |
| Stations list + detail | Yes | Yes | Yes | Yes |
| Settings → `AutoMapSettingsScreen` | Yes | Yes (fixed) | Yes | Yes |
| Cheapest filter | Yes | Yes | Yes | Yes |
| Compass / recenter | Yes | Limited | Yes (header actions) | Yes |
| Canvas HUD (mode + zoom) | Debug only | No | Yes | Via Mapsforge renderer |
| Offline banner | N/A | N/A | Protomaps when file missing | When no `.map` |

\*Google AA uses `PlaceListMapTemplate`; zoom is controlled by the **host**, not `MapController`. Documented limitation, not fixable without leaving the host map.

---

## 3. Pros

1. **Explicit modes** — Drivers and developers can see which backend is active (picker labels + HUD), fixing the old “Mapsforge looked like Custom” confusion.
2. **Custom preserved** — Reference implementation untouched; parity work targets other screens only.
3. **Shared MapLibre shell** — MapLibre, MapTiler, and Protomaps reuse one screen + renderer; only style resolution differs → less duplication.
4. **Factory dispatch** — Dashboards and navigation push one entry point (`AutoMapScreenFactory`); mode changes don’t require editing three dashboard files.
5. **Offline policy enforced** — No AA auto-download; missing files show an on-canvas banner instead of silent failure.
6. **Phone + AA settings aligned** — Mode and offline paths configurable on phone; Firestore sync for multi-device users.
7. **Mapsforge reuse** — AA Mapsforge mode uses the fuller `mapsforge/` package (`MapsforgePoiScreen`, `CarMapsforgeRenderer`, `MapsforgeMapManager`) rather than a parallel minimal implementation.
8. **Driver-safe discovery** — Mode picker is a `ListTemplate` step under settings (within 5-step budget when accessed from map settings).

---

## 4. Cons & known limitations

### 4.1 MapLibre rendering (all vector canvas modes)

Current pipeline is still **snapshot / tile-based Canvas drawing**, not native EGL (see audit doc). Implications:

- Pan/zoom may feel less fluid than Custom raster or host Google map.
- Street labels depend on snapshot quality and refresh rate; rotation may lag or look softer than a live GL map.
- Memory and CPU higher than a true `eglSwapBuffers` path, though improved vs the old `TextureView.bitmap` approach.

### 4.2 Dual Mapsforge implementations

- **Active:** `auto/mapsforge/` (`MapsforgePoiScreen`, `CarMapsforgeRenderer`) — used by factory.
- **Unused:** `maplibre/MapsforgeAaRenderer.kt` and `CanvasMapModeConfig.mapsforge()` — dead code from an earlier approach; should be removed or wired intentionally.

### 4.3 Split settings model

- Phone map engine (`MapEngine`: Google, MapLibre, Custom, Mapsforge) ≠ AA map mode (`CarMapMode`: six values).
- User can run Mapsforge on phone and MapLibre on AA (or vice versa). Powerful but potentially confusing without UI copy explaining the split.

### 4.4 Offline path ergonomics

- Protomaps and AA Mapsforge expect **absolute file paths** typed in phone settings (or Mapsforge manager for phone engine).
- No in-AA file picker; no automatic validation beyond existence/readability.
- Large `.map` / PMTiles files must be sideloaded manually.

### 4.5 MapTiler / API keys

- Requires `MAPTILER_KEY` at build time; empty key → style URL fails (banner/HUD still show mode, map may be blank).
- Not a runtime toggle; key rotation needs rebuild.

### 4.6 Google mode ceiling

- No app-controlled zoom on host map; no canvas HUD.
- Feature parity is structural (list, settings, filters), not pixel-level identical to Custom.

### 4.7 AA template quota

- Mode picker adds one stack step when opened from map settings.
- Deep settings flows (mode → theme → provider → …) still need careful stack design (see `android-auto.md`).

### 4.8 Label readability when rotated

- Heading-up rotation is supported on canvas modes, but **true “always upright street names”** needs vector GL text placement (MapLibre native) or Mapsforge label layer — not fully solved with raster snapshots.

---

## 5. Suggestions for future work

### High impact

| # | Suggestion | Rationale |
|---|------------|-----------|
| 1 | **EGL native MapLibre** (Option 2 in audit) | 60 FPS vector, correct labels at any bearing, lower memory; unlocks MapLibre/MapTiler/Protomaps quality |
| 2 | **Remove dead code** | Delete `MapsforgeAaRenderer` + `CanvasMapModeConfig.mapsforge()` OR document and test if kept for experiments |
| 3 | **Unify offline Mapsforge paths** | Align `offlineMapsforgePath` (AA manual path) with `MapsforgeMapManager` active file so phone download → AA works without duplicate config |
| 4 | **Protomaps UX** | File picker on phone, size warning, sample style JSON checked into repo; validate PMTiles on save |

### Medium impact

| # | Suggestion | Rationale |
|---|------------|-----------|
| 5 | **Runtime MapTiler key** | Optional settings field (like other API keys) instead of build-time only |
| 6 | **OpenMapTiles as explicit style preset** | If needed: MapLibre mode + `MapTheme` entry pointing at OpenMapTiles style URL (no new enum) |
| 7 | **HUD on Custom** | Optional: same `drawMapInfoStrip` on Custom for consistency (only if user wants — Custom was frozen) |
| 8 | **DHU test matrix** | Maestro or manual checklist: all 6 modes × light/dark × offline missing/present |
| 9 | **Consolidate `CarMapMode` / `MapEngine`** | Single `MapMode` with platform caps if product wants one knob for phone + AA |

### AA constraints & product

| # | Suggestion | Rationale |
|---|------------|-----------|
| 10 | **Quota map for settings** | Document max depth: Map → Settings → Mode picker → back (2 steps) — safe |
| 11 | **Default mode guidance** | Recommend Custom or Google for production; label MapLibre/MapTiler/Protomaps/Mapsforge as experimental in strings |
| 12 | **Graceful degradation** | If MapTiler key missing or Protomaps path invalid, auto-fallback to MapLibre with toast (opt-in setting) |
| 13 | **16 KB page size** | Any native MapLibre EGL work must stay lint-clean (`lintFullDebug`); avoid new unmanaged `.so` without alignment check |

### Documentation & ops

| # | Suggestion | Rationale |
|---|------------|-----------|
| 14 | **`docs/API_KEYS.md`** | Document `MAPTILER_KEY` alongside `GOOGLE_MAPS_KEY` |
| 15 | **Link from `android-auto.md`** | Short “Map modes” section pointing here |
| 16 | **Per-mode troubleshooting** | One subsection each: blank map, slow pan, wrong labels, offline banner |

---

## 6. Key files (quick reference)

```
SettingsManager.kt              CarMapMode enum, offline path fields
AutoMapScreenFactory.kt         Mode → Screen dispatch
AutoMapModePickerScreen.kt      AA 6-row picker
AutoMapModeLabels.kt            AA display strings (CarContext)
CanvasMapModeConfig.kt          Per-mode MapLibre shell config
MapLibrePoiScreen.kt            Shared canvas map screen (open class)
CarMapLibreRenderer.kt          MapLibre snapshot renderer + HUD
mapsforge/MapsforgePoiScreen.kt AA Mapsforge screen (active)
AutoMapOverlayHelper.kt         HUD strip + offline banner
OfflineMapAvailability.kt       File presence checks
AutoMapStyleUrl.kt              OpenFreeMap, MapTiler, Protomaps resolvers
SettingsScreen.kt               Phone AA mode + offline paths
FirestoreSettingsSync.kt        Cloud sync for paths
```

---

## 7. Verification checklist

```bash
./gradlew :androidApp:assembleFullDebug
./gradlew :androidApp:compileFullDebugKotlin
./gradlew :androidApp:testFullDebugUnitTest --tests "fr.geoking.gaston.auto.*"
```

Manual (DHU or car):

1. Each mode selectable from AA map settings → picker shows 6 distinct labels.
2. Canvas modes show mode + zoom chip; Protomaps/Mapsforge without file show centered banner.
3. Zoom +/-, stations list, settings, cheapest filter work on MapLibre and Mapsforge.
4. Custom behavior unchanged from pre-change baseline.
5. `git diff CustomMapPoiScreen.kt` empty after any follow-up work.

---

## 8. Decision log

| Decision | Choice | Why |
|----------|--------|-----|
| Custom mode | Frozen | User requirement; stable reference |
| Mapsforge AA | Remote `MapsforgePoiScreen` | Fuller implementation already on `main` |
| MapLibre variants | One screen + config | Minimize duplication |
| Offline downloads | Manual only | User requirement; avoids AA bandwidth/storage surprises |
| `MapMode` unification | Not done | Scope control; `CarMapMode` extended instead |
| Google zoom | Host-only | AA API limit for `PlaceListMapTemplate` |
