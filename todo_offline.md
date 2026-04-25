# Offline vector maps & routing — ideas backlog

Notes from design discussion: **offline basemap**, **vector data**, **routable** navigation, and how this could relate to Julius.

---

## Goals (when pursuing offline)

- Show a **map without network** (or with minimal network) for privacy, tunnels, and poor coverage.
- Optionally support **offline turn-by-turn or route preview** (polyline + instructions), not only “map tiles in cache.”
- Keep a path to **coexist** with current **Google Maps** + network `RoutingClient` (OSRM/GraphHopper APIs).

---

## Core concept: two different things

| Piece | Role |
|--------|------|
| **Vector map** | Geometry + styling for drawing: roads, areas, labels, icons. |
| **Routable data** | Graph for costing, turns, restrictions, and generating instructions. |

**Mapsforge** and similar libraries **render** vector maps; they **do not** replace a routing engine. Few open stacks ship **one file** that is both a full map renderer and a full router—most apps use **paired** data or a **vendor SDK** that bundles both.

---

## Mapsforge (offline vector map)

- **What it does:** Load local `.map` files (OSM-derived vector maps), render offline (pan/zoom, layers, markers). Example map sources: [OpenAndroMaps](https://www.openandromaps.org/) and custom extracts.
- **What it does not do:** Compute routes or turn-by-turn directions.
- **Pattern:** Router returns a **polyline + steps**; app **draws** the polyline on the Mapsforge map and uses steps for voice/UI.

---

## Routing engines (offline-capable)

Pair the renderer with one of:

- **GraphHopper** — Prebuilt **graph per region**; can run on-device when the graph is bundled or downloaded. Often paired with Mapsforge or **MapLibre** + offline tiles.
- **BRouter** — Offline via **segment files** + profiles; common in the OSM Android ecosystem; map display stays a separate layer.
- **OSRM / Valhalla** — Powerful; more typical server-side; full in-app offline bundles are heavier and less common.

**Julius today:** `shared/.../api/routing/RoutingClient.kt` documents OSRM/GraphHopper-style **HTTP** APIs—offline would be a **new implementation** (embedded engine + local data), not a drop-in for public demo endpoints.

---

## Provider / stack options (short list)

1. **GraphHopper offline graph + separate vector base**  
   Routable: yes (regional graph). Vector map: Mapsforge `.map`, or MapLibre + MBTiles/PMTiles, or raster tiles.

2. **Mapsforge `.map` only**  
   Strong offline **display**; routing requires **BRouter**, **GraphHopper**, or another engine.

3. **BRouter + Mapsforge (or MapLibre)**  
   Offline routing + offline vector (two artifact families); flexible, OSM-aligned.

4. **OsmAnd / Organic Maps–style binaries (e.g. `.obf` / `.mwm`)**  
   Often combine map + routing in **vendor-specific** formats; integration usually means their stack or significant reverse-engineering—not a small generic plugin.

5. **Commercial SDKs (Mapbox Navigation, HERE, TomTom, …)**  
   Offline regions + routing in one product; licensing and cost tradeoffs.

6. **MapLibre + vector tiles + external router**  
   Vector: yes if tiles are shipped or cached. Offline routing only if **GraphHopper/Valhalla** graph (or similar) is on-device.

---

## “Render engine” shape (architecture sketch)

A minimal offline map engine could expose:

- **Load** asset: `.map` (Mapsforge) or tile archive (MapLibre/MBTiles/PMTiles).
- **View lifecycle:** `MapView` / MapLibre `MapView` / Compose `AndroidView` wrapper.
- **Camera:** center, zoom, bearing (aligns with Android Auto surface-map ideas in root `TODO.md`).
- **Overlays:** POIs, **route polyline**, user location.
- **Styling:** Mapsforge theme XML vs MapLibre style JSON.

Routing stays **orthogonal:** `RouteResult` → draw polyline + use instructions for TTS/UI.

---

## Julius codebase context

- **Mobile map UI:** `androidApp/.../ui/MapScreen.kt` uses **Google Maps Compose** (`GoogleMap`).
- **Routing abstraction:** `RoutingClient` in shared module; implementations can target network APIs today.
- **Android Auto:** POI map and future pan/zoom/north-up work tracked in `TODO.md` (surface + `MapTemplate`); an offline renderer would need the same **surface drawing** path if it should run in-car.

Adding offline maps implies a **second map backend** (or a strategy pattern): settings like “Map: Google | Offline (Mapsforge | MapLibre)” and optional “Routing: Online | Offline (GraphHopper | BRouter).”

---

## Practical recommendation (open + offline vector + offline routes)

- **Display:** **Mapsforge** (simple `.map` on disk) **or** **MapLibre** + offline vector tiles.  
- **Routing:** **GraphHopper** (mobile graph) **or** **BRouter** (lighter, different model).  
- **Unify** behind interfaces: e.g. `MapRenderer` / viewport + existing `RoutingClient` (or `OfflineRoutingClient`) so Google and offline can coexist.

---

## Implementation todos (backlog — not scheduled)

- [ ] Decide **display stack**: Mapsforge vs MapLibre (tile pipeline, app size, Compose integration effort).
- [ ] Decide **offline router**: GraphHopper vs BRouter (graph size, update story, profile quality).
- [ ] Define **data delivery**: bundled regions vs download manager; storage path; version/update policy.
- [ ] Add **settings**: enable offline map; pick region; fallback to Google when file missing.
- [ ] Implement **`RoutingClient`** (or sibling) backed by embedded GraphHopper/BRouter for offline route requests.
- [ ] Implement **Compose-friendly map host** + polyline/POI overlay API aligned with current map features where possible.
- [ ] **Android Auto:** if offline map in-car is required, tie into surface-based map plan in `TODO.md` (custom renderer on `Surface`, not only host-rendered template).
- [ ] **Legal:** verify licenses for map files, router data, and any third-party style assets.

---

## References (external)

- [OpenAndroMaps](https://www.openandromaps.org/) — Mapsforge `.map` builds  
- Mapsforge project — vector rendering for Android/Java  
- GraphHopper — routing + offline graph documentation  
- BRouter — offline routing segments and profiles  

---

## Related internal doc

- Root **`TODO.md`** — Android Auto surface map, zoom, north-up / heading-up (orthogonal but relevant if the same renderer should run on Auto `Surface`).
