# Park-stop location FGS (deferred)

**Status:** removed from shipping builds (2026-10-10) so Play API uploads are not blocked by an undeclared Foreground Service permission.

**Why:** Commit `6f00aa11` added `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` and `ParkStopMonitorService`. CI built the AAB, then Play rejected the edit with:

> You must let us know whether your app uses any Foreground Service permissions.

That declaration lives in Play Console (**Policy → App content → Foreground service permissions**) and cannot be completed via the upload API alone. Location FGS also needs a use-case description and a short demo video.

**Current behavior (without FGS):** park save suggestion still runs on:

1. Android Auto / projection disconnect (`CarConnection` in `AaPostSessionParkSuggester`)
2. Gaston car session destroy (`CarAppSession.onDestroy` → `startIfEligible`)

In-projection stop detection while another car app is foreground is **not** available until FGS is restored.

## Restore checklist

1. **Play Console first**
   - Upload once manually to Internal testing if the App content form is missing, then complete **Foreground service permissions**.
   - Type: **location** (`FOREGROUND_SERVICE_LOCATION`).
   - Describe: while Android Auto is connected, Gaston polls GPS speed to detect a stop and offer saving the parked-car location (user-visible notification / HUN + TTS).
   - Attach a short video showing AA connected → stop → Save suggestion / HUN.
   - Wait until the declaration is accepted before relying on CI uploads.

2. **Re-apply code** (canonical source: commit `6f00aa11`)

   ```bash
   git show 6f00aa11 -- \
     androidApp/src/main/AndroidManifest.xml \
     androidApp/src/main/kotlin/fr/geoking/gaston/parked/ParkStopMonitorService.kt \
     androidApp/src/main/kotlin/fr/geoking/gaston/parked/CarModeParkReceiver.kt \
     androidApp/src/main/kotlin/fr/geoking/gaston/parked/AaPostSessionParkSuggester.kt \
     androidApp/src/main/res/values/strings.xml \
     androidApp/src/main/res/values-fr/strings.xml
   ```

   Pieces to bring back:

   | Piece | Role |
   |-------|------|
   | Manifest `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` | Required permissions |
   | `ParkStopMonitorService` (`foregroundServiceType="location"`) | Keep process alive + start GPS stop polling |
   | `CarModeParkReceiver` (`ENTER_CAR_MODE` / `EXIT_CAR_MODE`) | Cold-start FGS when entering car mode |
   | `AaPostSessionParkSuggester` projection path | `ParkStopMonitorService.start` on projection; `attachFromService` → `InSessionStopTracker` polling |
   | Strings `park_stop_monitor_*` (en + fr) | Ongoing FGS notification copy |

3. **Do not** reintroduce FGS only on the `full` flavor if the goal is Play — Play ships `playstoreRelease`. Declare in Console, then enable in **main** (both flavors).

4. **Verify**

   ```bash
   ./gradlew :androidApp:assemblePlaystoreRelease
   # Merged playstore manifest must contain FOREGROUND_SERVICE_LOCATION only after Console declaration is done.
   ```

## Related

- [Understanding foreground service and full-screen intent requirements](https://support.google.com/googleplay/android-developer/answer/13392821)
- `InSessionStopTracker` (+ unit tests) remains in tree for the in-projection path when FGS returns.
