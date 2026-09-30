# AAC data notes (Level A)

## France fixed speed-control CSV (data.gouv.fr)

- Dataset: [Liste des radars fixes en France](https://www.data.gouv.fr/fr/datasets/liste-des-radars-fixes-en-france/)
- Resolver: `FranceRadarsCsvResolver` calls the data.gouv dataset API and picks the newest `csv` resource (`last_modified`). Fallback: hardcoded `FranceRadarsClient.DEFAULT_CSV_URL`.
- Versioning: disk meta stores resource id / last_modified string alongside CSV body (`AndroidTextFileCache`, TTL 24 h).
- Records → **extended** `DangerZone` via `toDangerZone()` (never alert pins).

## OSM merge (Overpass) — direction enrichment

- **Ancre alertes** = toujours data.gouv (`DangerZoneRepository`).
- **Enrichissement** = `RadarOsmEnricher` + `OverpassClient.querySpeedCamerasInBbox` :
  - match spatial ≤ **40 m**
  - relation `type=enforcement` (`from`→`to`) → `DirectionConfidence.High`
  - `direction=forward|backward` + way géométrie → High
  - `direction=both` → High bidirectionnel
  - `direction=` degrés / cardinaux → **Low** (pas de filtre d’alerte)
- `DangerZoneEvaluator` filtre le sens véhicule seulement si **High** et non bidirectionnel (±45°).
- Pas d’API OSM `api/0.6` (écriture).

## Carte amenity `speed_camera`

- Overpass `highway=speed_camera` + FranceRadars markers.
- `RadarPoiMerger` déduplique : si OSM ≤ 40 m d’un pin FranceRadars, on garde FranceRadars.

## Non-radar zones

- `StaticNonRadarDangerZones`: sample accident-prone / vigilance corridors so alerts are **not** 100 % radar-derived.
- Production open-data path (not wired yet): BAAC / ONISR accidentalité on data.gouv, local « points noirs », collective vigilance zones.
- **No** community police-control feed (L. 130-11 occultation process not implemented).

## Mix policy

`DangerZoneRepository.zonesNear` merges FranceRadars zones (optionally OSM-direction-enriched) + static non-radar samples within the vehicle radius.
