# AAC architecture (Level A)

Driving assistant for France (R. 413-15 spirit) — **not** NF 469 certification.

## Flow

```
data.gouv CSV (resolved URL) + StaticNonRadarDangerZones
        │
        ▼
 FranceRadarsClient (memory + disk TTL)
        │
        ▼
 DangerZoneRepository.zonesNear(vehicle)   ← local cache around vehicle
        │
        ▼
 DangerZoneAlertManager (GPS loop ~2 s)
        │
        ├── DangerZoneEvaluator (in-zone + ahead bearing)
        ├── RadarAudioNotifier (TTS: zone de danger + VMA)
        └── DangerZoneHudBanner (VMA + entrée zone)
```

## Key modules

| Concern | Module |
|---------|--------|
| Domain | `shared/.../aac/DangerZone.kt` |
| FR CSV | `FranceRadarsClient` + `FranceRadarsCsvResolver` |
| Mix | `DangerZoneRepository` + `StaticNonRadarDangerZones` |
| Alerts | `DangerZoneAlertManager` |
| Map pins FR | Disabled (`FranceRadarsProvider` empty; Overpass radar filtered in FR) |
| Kill / Play | `BuildConfig.AAC_ALERTS_AVAILABLE` + settings default OFF |

## Requirements ↔ modules

| Exigence | Implémentation |
|----------|----------------|
| Pas de localisation précise contrôle | Zones étendues ; pas de pin FR alertes |
| Vocabulaire zone / VMA | `DangerZoneAlertCopy`, strings `aac_*` |
| Distances réseau | `DangerZoneDistances` (4/2/0.3 km) |
| Zones hors radar | `StaticNonRadarDangerZones` (+ doc BAAC) |
| Perf | Cache local zones ; pas de `poiProvider.search` chaque tick |

See also: [`RADARS_NF469.md`](RADARS_NF469.md), [`AAC_DATA.md`](AAC_DATA.md), [`RADARS_NF469_TODO_NIVEAU_A.md`](RADARS_NF469_TODO_NIVEAU_A.md).
