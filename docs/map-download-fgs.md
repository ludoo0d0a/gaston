# Offline map download FGS (`dataSync`)

**Status:** FGS stripped from shipping builds (2026-10-31) so Play API uploads are not blocked. Declaration is still required **before** restoring `FOREGROUND_SERVICE_DATA_SYNC`.

**Why Play rejected CI:** the AAB included `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC`. Commit of the Play edit then failed with:

> You must let us know whether your app uses any Foreground Service permissions.

That form is Console-only (not the upload API). Type **dataSync** needs a use-case description **and** a video of the user starting the download.

**Until FGS is restored:** `MapDownloadService` runs as a normal `startService` from Settings / Android Auto. Leaving the app mid-download can kill the process; `.tmp` / `.download` files still resume.

---

## Play Console — declare dataSync

Gaston Play IDs live in `scripts/project.manifest.json` (`play.developerId` / `play.appId`).

| Step | Link |
|------|------|
| App dashboard | [app-dashboard](https://play.google.com/console/u/0/developers/8648842673731499425/app/4974244435243198358/app-dashboard) |
| **App content** (start here) | [app-content/overview](https://play.google.com/console/u/0/developers/8648842673731499425/app/4974244435243198358/app-content/overview) |
| Foreground service permissions | Open App content → **Foreground service permissions** (Policy). Direct: [foreground-service](https://play.google.com/console/u/0/developers/8648842673731499425/app/4974244435243198358/app-content/policy/foreground-services) |
| Internal testing (need a build on a track first) | [internal-testing](https://play.google.com/console/u/0/developers/8648842673731499425/app/4974244435243198358/tracks/internal-testing) |
| Publishing overview | [publishing](https://play.google.com/console/u/0/developers/8648842673731499425/app/4974244435243198358/publishing) |
| Store listing (not this form) | [main-store-listing](https://play.google.com/console/u/0/developers/8648842673731499425/app/4974244435243198358/main-store-listing) |

Policy / video rules: [Understanding foreground service and full-screen intent requirements](https://support.google.com/googleplay/android-developer/answer/13392821) — for `TYPE_DATA_SYNC` / **Network transfer: Upload or download**, the video must **show a user initiating an upload or download**.

### Form values (copy-paste)

1. Does the app use foreground services? **Yes**
2. Type: **Data sync** (`FOREGROUND_SERVICE_DATA_SYNC`)
3. Use case: **Network transfer: Upload or download** (user-initiated offline map pack)
4. Video URL: host [`docs/playstore/fgs-data-sync-demo.mp4`](playstore/fgs-data-sync-demo.mp4) (see below), then paste the public/unlisted URL.

**Describe the feature** (EN):

> Gaston lets the user download an offline Mapsforge or PMTiles map pack from phone Settings → Offline maps by region (or from Android Auto). Tapping Download starts a user-visible transfer of a large regional file (tens–hundreds of MB) with HTTP Range resume. A low-priority notification titled “Offline map download” shows progress until the pack is installed and set active. The work is not an automatic background sync.

**Pourquoi la tâche doit démarrer tout de suite / ne peut pas être mise en pause** (FR, listing par défaut `fr-FR`):

> L’utilisateur lance lui-même le téléchargement d’une carte hors-ligne (Mapsforge / PMTiles) depuis Paramètres → Cartes hors ligne par région, ou depuis Android Auto. Le fichier est volumineux ; s’il est reporté ou interrompu, le pack n’est pas installé et la navigation hors-ligne / Android Auto reste indisponible. Une notification persistante « Téléchargement de carte hors-ligne » montre la progression. Les fichiers partiels (`.tmp`) permettent une reprise, mais le service doit rester vivant pendant le transfert.

**User impact if deferred / interrupted:**

> If the system delays or kills the service, the pack is only partial. The user would have to return to Settings and tap Download again (resume from the partial file). Offline map / Android Auto for that region would stay unavailable until the file finishes.

---

## Demo video

**File:** [`docs/playstore/fgs-data-sync-demo.mp4`](playstore/fgs-data-sync-demo.mp4) (~17 s, 9:16, ~130 KB). Walkthrough of the real in-app copy:

1. Settings → **Offline maps by region** → Mapsforge row **Île-de-France (Paris)** → **Download**
2. **Map Download Warning** confirm
3. Ongoing notification **Offline map download** / `Downloading ile-de-france.map…`
4. Shade: **ile-de-france.map downloaded successfully** + row **Installed** / Active map set

Regenerate:

```bash
python3 docs/playstore/generate_fgs_data_sync_demo.py
```

### Publish the URL (Console will not accept a git path)

Play asks for a **link**, not an AAB attachment.

1. [Google Drive](https://drive.google.com) → upload `fgs-data-sync-demo.mp4` → Share → **Anyone with the link** → Viewer → copy URL  
   or [YouTube Studio](https://studio.youtube.com) → Upload → visibility **Unlisted** → copy watch URL.
2. Paste that URL into the Foreground service form → Save.
3. Wait until App content shows the declaration as complete before restoring FGS in the manifest (otherwise CI upload fails again).

A **device screenrecord** of the same taps on a `playstore` build is stronger than the generated walkthrough. After FGS is back:

```bash
adb shell screenrecord --time-limit 30 /sdcard/fgs-data-sync.mp4
# Settings → Offline maps by region → Download Île-de-France → pull shade
adb pull /sdcard/fgs-data-sync.mp4 docs/playstore/fgs-data-sync-demo.mp4
```

---

## Restore FGS in code (after Console accepts)

| Piece | Role |
|-------|------|
| Manifest `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` (drop `tools:node="remove"`) | Permissions |
| `android:foregroundServiceType="dataSync"` on `MapDownloadService` | Manifest type |
| `ContextCompat.startForegroundService(...)` in `downloadToTemp` | Start path |
| `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_DATA_SYNC)` in `onStartCommand` | FGS timeout |
| `stopForeground(STOP_FOREGROUND_REMOVE)` when the download finishes | Tear-down |

Do **not** enable FGS only on the `full` flavor — Play ships `playstoreRelease`.

```bash
./gradlew :androidApp:assemblePlaystoreRelease
# Merged playstore manifest must contain FOREGROUND_SERVICE_DATA_SYNC only after Console is done.
```

## Related

- [FGS / full-screen intent (Play Help)](https://support.google.com/googleplay/android-developer/answer/13392821)
- [Device and Network Abuse — FGS](https://support.google.com/googleplay/android-developer/answer/9888170)
- `docs/park-stop-fgs.md` — location FGS also deferred
- Arthur: `doc/playstore/music-download-fgs.md`
