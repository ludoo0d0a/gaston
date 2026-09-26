# AAC data notes (Level A)

## France fixed speed-control CSV (data.gouv.fr)

- Dataset: [Liste des radars fixes en France](https://www.data.gouv.fr/fr/datasets/liste-des-radars-fixes-en-france/)
- Resolver: `FranceRadarsCsvResolver` calls the data.gouv dataset API and picks the newest `csv` resource (`last_modified`). Fallback: hardcoded `FranceRadarsClient.DEFAULT_CSV_URL`.
- Versioning: disk meta stores resource id / last_modified string alongside CSV body (`AndroidTextFileCache`, TTL 24 h).
- Records → **extended** `DangerZone` via `toDangerZone()` (never alert pins).

## OSM `speed_camera`

- Disabled for FR map/alerts (`OverpassProvider` + `AacMapPolicy.isLikelyFrance`). Outside FR, labels use « Zone … » wording.

## Non-radar zones

- `StaticNonRadarDangerZones`: sample accident-prone / vigilance corridors so alerts are **not** 100 % radar-derived.
- Production open-data path (not wired yet): BAAC / ONISR accidentalité on data.gouv, local « points noirs », collective vigilance zones.
- **No** community police-control feed (L. 130-11 occultation process not implemented).

## Mix policy

`DangerZoneRepository.zonesNear` merges FranceRadars zones + static non-radar samples within the vehicle radius.
