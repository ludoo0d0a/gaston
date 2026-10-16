# RoadMatcher — identifier la route du véhicule

> Mis à jour 2026-10-16.  
> Statut : **plan / options** (non implémenté).  
> Contexte : améliorer parking, trafic, et entrée en zone de danger radar quand la route est courbe (bearing GPS insuffisant).

---

## Problème

Plusieurs features ont besoin de savoir **sur quelle voie OSM le véhicule est**, pas seulement un bearing ou un disque autour du GPS :

| Cas d’usage | Besoin | Limite actuelle |
|-------------|--------|-----------------|
| **Parking** | Distinguer « encore sur la chaussée » vs « hors réseau / amenity=parking » pour crédibiliser la suggestion post-AA | `AaPostSessionParkSuggester` : last lat/lon + session AA, pas de contexte route |
| **Trafic** | Filtrer / prioriser les events sur *ma* voie (ref, corridor) | Providers géo (TIPI, CITA, TomTom…) sans snap utilisateur |
| **Radar / zone de danger** | Alerter si on est sur le même corridor que le radar ; route courbe → bearing constant faux | Secteur + bearing véhicule + `monitored_bearing` OSM (`DangerZoneTriangle`, `DangerZoneEvaluator`) |

Le bearing reste utile pour le **sens** de circulation ; il ne remplace pas l’**identité de route** ni la géométrie locale (tangent sur polyline).

---

## État du code (baseline)

| Brique | Rôle | Limite |
|--------|------|--------|
| `OsmRoadClassifier` | Overpass `way(around:40m)["highway"]` → classe Motorway / ExtraUrban / Urban | Pas d’`osm_way_id`, pas de géométrie, pas de vrai map-match (voisinage de tags) |
| `RadarOsmEnricher` | Match ancre → `speed_camera` OSM ; bearing via enforcement / way | Bearing du radar, pas « way du véhicule » |
| `OsrmRoutingClient` | `/route/v1` (itinéraire A→B) | Pas de `/match` ni `/nearest` |
| Mapsforge | Rendu tuiles offline | Pas un graphe routier pour matching |
| Docs NF469 | Classification voie / map-matching | En partie reporté / écart accepté |

Références utiles : `docs/AAC_DATA.md`, `docs/RADARS_NF469_TODO_NIVEAU_A.md`, `docs/park-stop-fgs.md`.

---

## Cible : service partagé `RoadMatcher`

Un seul résultat consommé par parking / trafic / radar :

```text
GPS trace (lat, lon, bearing, speed, t)
        ↓
   RoadMatch {
     osmWayId, highway, name/ref,
     snappedLatLon,
     roadBearingAtSnap,      // tangent locale (courbes OK)
     distanceToRoadM,
     confidence,
     onCarriageway | inParking | offNetwork
   }
```

### Consommateurs

1. **Radar** — alerte si way véhicule ∈ ways proches du radar (ou dist à la polyline radar &lt; seuil) ; bearing = filtre de sens secondaire.
2. **Parking** — `inParking` / `distanceToRoadM` grand + vitesse ~0 → boost candidat ; encore sur `highway=*` → frein.
3. **Trafic** — croiser event ↔ `osmWayId` / `ref` / corridor de *cette* way.

Ordre de branchement suggéré : **radar → parking → trafic**.

---

## Options de source (vecteur / match)

| Option | « Quelle route ? » | Courbes | Offline | Complexité | Verdict |
|--------|--------------------|---------|---------|------------|---------|
| **OSRM `/match/v1`** (trace GPS) | Excellent (way + snap + bearing local) | Oui | Non (ou self-host) | Moyenne | **Meilleur fit** pour les 3 cas |
| **OSRM `/nearest/v1`** | Point → edge | Non (1 point) | Non | Faible | Bon fallback léger |
| **Overpass + `out geom`** (étendre `queryHighwayWaysAround`) + projection point→segment | Correct localement | Oui si projection polyline | Non | Moyenne | Déjà dans le stack ; MVP sans OSRM match |
| **Mapsforge `.map`** | Possible en théorie | Oui | Oui | Élevée | **Mauvais ROI** (API rendu) |
| Bearing GPS seul | Non (pas d’identité de route) | Non | Oui | — | Insuffisant |
| OSRM `/route` seul | Non (A→B ≠ « où suis-je ») | — | Non | — | Ne remplace pas match |

**Note sémantique :** OSRM dit où *le véhicule* roule, pas quel sens le radar *surveille*. Pour le sens contrôlé, garder OSM enforcement / `direction=forward|backward` (`RadarOsmEnricher`).

---

## Phases d’implémentation proposées

### MVP (Overpass geom)

- Étendre Overpass : géométrie ways autour du GPS + projection lat/lon → segment le plus proche.
- Exposer `RoadMatch` (way id, dist, tangent, confidence simple).
- Radar : critère « même way / dist polyline » en plus du secteur actuel.
- Parking : heuristique on-road vs off-road / parking nearby.

### Qualité (OSRM match)

- Client OSRM `/match/v1` sur une fenêtre de N points GPS (≈ 5–30 s).
- Cache court + throttle (démo `router.project-osrm.org` rate-limitée → self-host ou backend GeoKing si prod).
- Hystérésis : ne pas flipper de way à chaque fix GPS.

### Hors scope / peu rentable

- Extraire le graphe routier depuis Mapsforge.
- Remplacer le bearing radar OSM par OSRM seul.
- Trois heuristiques séparées au lieu d’un `RoadMatcher` partagé.

---

## API indicative (non figée)

```kotlin
data class RoadMatch(
    val osmWayId: Long?,
    val highway: String?,
    val nameOrRef: String?,
    val snappedLatitude: Double,
    val snappedLongitude: Double,
    val roadBearingDegrees: Double?, // tangent at snap
    val distanceToRoadMeters: Double,
    val confidence: Float, // 0f..1f
    val placement: RoadPlacement, // OnCarriageway, InParking, OffNetwork
    val source: RoadMatchSource, // OsrmMatch, OsrmNearest, OverpassGeom, …
)

interface RoadMatcher {
    suspend fun match(fix: List<GpsSample>): RoadMatch?
    suspend fun matchPoint(latitude: Double, longitude: Double, bearingDegrees: Double? = null): RoadMatch?
}
```

Points d’accroche naturels :

- `DangerZoneAlertManager` / `DangerZoneEvaluator` (filtre evenement)
- `AaPostSessionParkSuggester` / futurs stop detectors
- Couche trafic (filtre events après fetch provider)

---

## Décisions ouvertes

- [ ] MVP Overpass-only d’abord, ou OSRM match dès le départ ?
- [ ] Self-host OSRM / proxy GeoKing vs démo publique (quotas) ?
- [ ] Faut-il persister `osm_way_id` sur les `DangerZone` enrichies pour croisement rapide ?
- [ ] Offline : acceptable de dégrader (bearing + secteur) sans Mapsforge matching ?

---

## Liens

- Enrichissement direction radar : `shared/.../aac/RadarOsmEnricher.kt`, `OsmSpeedCameraDirection.kt`
- Classification voie (actuel) : `shared/.../aac/OsmRoadClassifier.kt`
- Géométrie alerte : `shared/.../aac/DangerZoneTriangle.kt`, `DangerZone.kt`
- OSRM route : `shared/.../api/routing/OsrmRoutingClient.kt`
- Parking post-session : `androidApp/.../parked/AaPostSessionParkSuggester.kt`
