# Android Auto map modes — requirements, implementation, trade-offs

Summary of Android Auto (AA) map modes.  
For AA template constraints (5-step quota, terminal templates), see [`android-auto.md`](android-auto.md).  
For MapLibre EGL / GL migration background, see [`maplibre_android_auto_audit.md`](maplibre_android_auto_audit.md).

**Custom is frozen** — do not modify `CustomMapPoiScreen` / `AutoSurfaceRenderer`. It remains the parity baseline (rotation + POIs).

---

## 1. Goals

| Requirement | Detail |
|-------------|--------|
| **AA-compatible map** | Zoom, POIs on map, rotatable map, readable street labels despite rotation |
| **Keep all modes until final tests** | No mode removal while A/B continues |
| **Custom frozen** | Reference shell — rotation + POIs |
| **Feature parity** | Other modes aim to match Custom: zoom +/-, stations list, map settings, cheapest filter |
| **Manual offline only** | Never auto-download map files on AA; user configures paths on phone |
| **OpenFreeMap vector** | Preferred fluid look via OpenFreeMap style → PBF |

Phone (`MapEngine`) and AA (`CarMapMode`) settings remain separate.

---

## 2. Mode matrix (`CarMapMode`)

| `CarMapMode` | AA screen | Renderer / backend | Network | Notes |
|-------------|-----------|-------------------|---------|-------|
| **Native** (Google) | `NativeMapPoiScreen` | Host `PlaceListMapTemplate` | Online | Host zoom only |
| **Custom** | `CustomMapPoiScreen` | Raster XYZ → Canvas | Online | **Frozen** reference |
| **MapLibre** | `MapLibrePoiScreen` | `CarMapLibreRenderer` MapSnapshotter → Canvas | Online OpenFreeMap | Snapshot path (audit Option 3) |
| **MapTiler** | `MapLibrePoiScreen` | Same snapshot renderer, MapTiler style | Online | Needs `MAPTILER_KEY` |
| **Protomaps** | `MapLibrePoiScreen` | Same snapshot; **path gate only** | Online OpenFreeMap when path OK | Local `.pmtiles` not fed into MapLibre yet |
| **Mapbox** | `MapLibrePoiScreen` | `CarMapboxRenderer` | — | Experimental stub (gray basemap) |
| **MapLibrePresentation** | `MapLibrePoiScreen` | `CarMapLibrePresentationRenderer` → `MapLibreAaGlHost` | Online OpenFreeMap | VirtualDisplay + MapView + GeoJSON POIs |
| **MapLibreEgl** | `MapLibrePoiScreen` | `CarMapLibreEglRenderer` → `MapLibreAaGlHost` | Online OpenFreeMap | Fluid GL experiment (primary vector bet) |
| **Mapsforge** | `mapsforge.MapsforgePoiScreen` | `CarMapsforgeRenderer` | Offline `.map` | Restored in factory |

Central dispatch: [`AutoMapScreenFactory.kt`](../androidApp/src/main/kotlin/fr/geoking/gaston/auto/AutoMapScreenFactory.kt).

### Shared canvas / GL shell

MapLibre-family modes (except Mapsforge/Native/Custom) use [`MapLibrePoiScreen`](../androidApp/src/main/kotlin/fr/geoking/gaston/auto/MapLibrePoiScreen.kt) + [`CanvasMapModeConfig`](../androidApp/src/main/kotlin/fr/geoking/gaston/auto/CanvasMapModeConfig.kt).

GL modes share [`MapLibreAaGlHost`](../androidApp/src/main/kotlin/fr/geoking/gaston/auto/maplibre/MapLibreAaGlHost.kt) (VirtualDisplay + MapView + [`MapLibreSharedHelper`](../androidApp/src/main/kotlin/fr/geoking/gaston/ui/map/maplibre/MapLibreSharedHelper.kt) POI layers).

---

## 3. Product posture until final tests

| Use | Mode |
|-----|------|
| Daily driving | **Custom** (unchanged) |
| Fluid OpenFreeMap vector experiment | **MapLibreEgl**, **MapLibrePresentation** |
| Fallback vector-ish | Snapshot **MapLibre** |
| Offline | **Mapsforge** |
| Host | **Native** |

---

## 4. Known limitations

### Snapshot MapLibre / MapTiler / Protomaps

- Pan/zoom less fluid than Custom or GL modes.
- Protomaps: presence of `offlinePmtilesPath` only unlocks the mode; basemap is still **online OpenFreeMap**, not local PMTiles bytes.

### MapLibreEgl / Presentation (GL)

- MapLibre `MapView` owns EGL via VirtualDisplay (cannot also `lockCanvas` HUD on the same surface).
- `CarEglSurfaceRenderer` is scaffolding for a future direct NativeMap bind; primary path is VirtualDisplay.
- VirtualDisplay may be fragile on some hosts — keep snapshot MapLibre as fallback.

### Mapbox

- Basemap stub; experimental only.

### Mapsforge

- Active under `auto/mapsforge/`. Dead `MapsforgeAaRenderer` in `maplibre/` left for later cleanup.

### Custom

- **Do not modify.** Parity baseline for rotation + POIs.

---

## 5. DHU / manual parity checklist (vs Custom)

For each mode × north-up / heading-up:

- [ ] Map appears (not black / crash)
- [ ] Zoom +/-
- [ ] POI markers visible
- [ ] POI tap → station detail (stack ≤ 5)
- [ ] Recenter / compass if exposed
- [ ] Settings → mode picker → back
- [ ] Offline banner when Protomaps/Mapsforge file missing

---

## 6. Key files

| File | Role |
|------|------|
| `SettingsManager.kt` | `CarMapMode` enum |
| `AutoMapScreenFactory.kt` | Mode → screen |
| `CanvasMapModeConfig.kt` | Renderer factories |
| `MapLibrePoiScreen.kt` | Shared list + surface shell |
| `CarMapLibreRenderer.kt` | Snapshot → Canvas |
| `CarMapLibreEglRenderer.kt` / `CarMapLibrePresentationRenderer.kt` | GL host wrappers |
| `MapLibreAaGlHost.kt` | VirtualDisplay + MapView + layers |
| `CarEglSurfaceRenderer.kt` | EGL helper (fallback / future NativeMap) |
| `CustomMapPoiScreen.kt` | **Frozen** |
| `mapsforge/MapsforgePoiScreen.kt` | Offline Mapsforge |

---

## 7. Design choices retained

| Choice | Why |
|--------|-----|
| Custom untouched | Only stable rotation+POI reference |
| New `MapLibreEgl` vs replacing snapshot | Keep A/B until final tests |
| OpenFreeMap style URL | Same PBF/style as phone MapLibre |
| No DIY Canvas Bezier roads | OpenFreeMap styling stays in MapLibre |
