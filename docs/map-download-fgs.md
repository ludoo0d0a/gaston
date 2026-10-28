# Offline map download FGS (`dataSync`)

**Service:** `MapDownloadService` (`foregroundServiceType="dataSync"`).

**Why:** Offline Mapsforge / PMTiles packs are large (tens–hundreds of MB). Downloads must continue when Settings Activity or an Android Auto screen is destroyed, and must resume after network drops (`HTTP Range` + `.tmp` / `.download` partial files).

**Permissions:** `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`.

## Play Console

Before uploading a build that includes this service, declare Foreground service permissions:

1. Play Console → **Policy → App content → Foreground service permissions**
2. Type: **Data sync** (`FOREGROUND_SERVICE_DATA_SYNC`)
3. Describe: user starts an offline map download from phone Settings or Android Auto; a low-priority notification shows progress until the file is installed.

Without that declaration, Play may reject the AAB the same way as undeclared `location` FGS (see `park-stop-fgs.md`).

## Behavior

- One download at a time (shared `MapDownloadProgressHub`)
- Resume: partial file kept on failure; next start sends `Range: bytes=N-`
- Call sites still use `MapsforgeMapManager.downloadMap` / `PmtilesMapManager.downloadMap` (they enqueue the service and finalize / activate the file)
