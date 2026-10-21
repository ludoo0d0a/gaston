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
| **Custom** | `CustomMapPoiScreen` | Raster XYZ → Canvas | Online | **Frozen** reference; also the automatic fallback |
| **MapLibreMapSnapshotter** | `MapLibrePoiScreen` | `CarMapLibreRenderer`: `MapSnapshotter` → Canvas (never EGL) | Online OpenFreeMap | Safest MapLibre path |
| **MapLibreVirtualDisplay** | `MapLibrePoiScreen` | `CarMapLibreVirtualDisplayRenderer` → `MapLibreAaGlHost` (VirtualDisplay + `MapView` + GeoJSON POIs) | Online OpenFreeMap | Fluid GL, upright labels; fragile on some hosts |
| **MapLibreEglDirect** | — | Not implemented | — | Hidden from picker/cycle (`isSelectable=false`); maps to VirtualDisplay |
| **Mapbox** | `MapLibrePoiScreen` | `CarMapboxRenderer` | — | Experimental stub, hidden |
| **Mapsforge** | `mapsforge.MapsforgePoiScreen` | `CarMapsforgeRenderer` | Offline `.map` | Vector, offline |

MapTiler, Protomaps, Presentation and EGL modes were removed/merged; stored preferences are migrated by `CarMapMode.fromStored`.
Central dispatch: [`AutoMapScreenFactory.kt`](../androidApp/src/main/kotlin/fr/geoking/gaston/auto/map/AutoMapScreenFactory.kt).

### Surface ownership and mode swap

A `Surface` accepts one producer (Canvas **or** EGL). `AaSurfaceSession` caches the last Surface/visible area; `swapMapMode` makes the current `MapSurfaceOwner` release it before the next screen re-attaches from the cache, so no zoom is needed to force the first frame. `MapLibrePoiScreen` releases on `onDestroy`. If a MapLibre renderer fails for good (5 snapshot errors, or VirtualDisplay attach failure) the screen falls back to **Custom**.

---

## 3. Product posture

| Use | Mode |
|-----|------|
| Safe default MapLibre | **MapLibreMapSnapshotter** |
| Fluid vector experiment | **MapLibreVirtualDisplay** |
| Offline vector | **Mapsforge** |
| Fallback | **Custom** |
| Host | **Native** |

---

## 4. Known limitations

- Snapshotter: pan/zoom less fluid than GL; labels rotate with the bitmap unless bearing is passed to the snapshotter (planned).
- VirtualDisplay: `MapView` owns EGL, so no Canvas HUD on that Surface; may be fragile on some hosts.
- EglDirect: not implemented (`CarEglSurfaceRenderer` is scaffolding only).
- Offline MapLibre (local PMTiles) is not wired; `offlinePmtilesPath` is reserved for it.

---

## 5. DHU / manual parity checklist (vs Custom)

For each mode × north-up / heading-up:

- [ ] Map appears (not black / crash)
- [ ] Zoom +/-
- [ ] POI markers visible
- [ ] POI tap → station detail (stack ≤ 5)
- [ ] Recenter / compass if exposed
- [ ] Settings → mode picker → back
- [ ] Offline banner when Mapsforge file missing

---

## 6. Key files

| File | Role |
|------|------|
| `SettingsManager.kt` | `CarMapMode` enum |
| `AutoMapScreenFactory.kt` | Mode → screen |
| `CanvasMapModeConfig.kt` | Renderer factories |
| `MapLibrePoiScreen.kt` | Shared list + surface shell |
| `CarMapLibreRenderer.kt` | Snapshot → Canvas |
| `CarMapLibreVirtualDisplayRenderer.kt` | GL host wrapper |
| `MapLibreAaGlHost.kt` | VirtualDisplay + MapView + layers |
| `CarEglSurfaceRenderer.kt` | EGL helper (fallback / future NativeMap) |
| `CustomMapPoiScreen.kt` | **Frozen** |
| `mapsforge/MapsforgePoiScreen.kt` | Offline Mapsforge |

---

## 7. Design choices retained

| Choice | Why |
|--------|-----|
| Custom untouched | Only stable rotation+POI reference |
| Separate VirtualDisplay mode vs replacing snapshot | Keep A/B until final tests |
| OpenFreeMap style URL | Same PBF/style as phone MapLibre |
| No DIY Canvas Bezier roads | OpenFreeMap styling stays in MapLibre |
