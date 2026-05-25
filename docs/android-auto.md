# Android Auto – Template Constraints & Development Notes

Reference: https://developer.android.com/training/cars/apps/library/template-restrictions

---

## Template quota

The host limits each **task** to at most **5 template steps**.  
The last template must be one of: `NavigationTemplate`, `PaneTemplate`, `MessageTemplate`,
`MediaPlaybackTemplate`, `SignInTemplate`, or `LongMessageTemplate`.

- A **template refresh** (same type + same main content) does NOT count against the quota.
- Popping back to a previous screen resets the quota by the number of steps undone, but the
  resumed screen must return the **same template type** it last returned.
- `NavigationTemplate` triggers a quota **reset** when reached, starting a new task.

---

## App category: POI

Gaston is declared as `category.POI` in `AndroidManifest.xml`.  
Required permissions:

```xml
<uses-permission android:name="androidx.car.app.MAP_TEMPLATES" />
<uses-permission android:name="androidx.car.app.ACCESS_SURFACE" />
```

`MAP_TEMPLATES` is mandatory to use `MapWithContentTemplate` or `PlaceListMapTemplate`.  
`ACCESS_SURFACE` is mandatory to draw on the car surface via `SurfaceCallback`.

Minimum API level: **7** (`minCarApiLevel` meta-data in manifest).

### Heads-up notifications (HUN)

Connectivity/border alerts use **phone notifications** mirrored to the car, not `AppManager.showAlert()`:

- `Alert` / `showAlert()` only works inside `NavigationTemplate` (navigation apps). POI apps must use HUN via notifications.
- Extend with `CarAppExtender` and set `setImportance(IMPORTANCE_HIGH)` for a car-screen HUN.
- Post with `CarNotificationManager.notify()`, not `NotificationManager.notify()`.
- Declare `POST_NOTIFICATIONS` and request runtime permission on API 33+ (phone host; car still needs the notification posted from the device).
- Use a category eligible for HUN (`CATEGORY_MESSAGE`, `CATEGORY_CALL`, or `CATEGORY_NAVIGATION` on Automotive OS).

Reference: [CarAppExtender](https://developer.android.com/reference/androidx/car/app/notification/CarAppExtender), [navigation alerts vs HUN](https://developer.android.com/training/cars/apps/navigation#display-in-context-navigation-alerts).

---

## MapWithContentTemplate

Navigation-package template (`androidx.car.app.navigation.model`) available to POI apps with
`MAP_TEMPLATES` permission.

### Allowed content templates

`ListTemplate` | `GridTemplate` | `PaneTemplate` | `MessageTemplate`

### ActionStrip

See also the full ActionStrip section below.

### SurfaceCallback

Register the callback in `Screen.onStart()` via
`carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)`.  
Unregister (stop the renderer) in `Screen.onStop()`.

---

## ListTemplate (standalone or inside MapWithContentTemplate)

- **Max rows**: read from `ConstraintManager.getContentLimit(CONTENT_LIMIT_TYPE_LIST)`.  
  Default is **6**; lower on some vehicles. Never hardcode the limit.

  ```kotlin
  val listLimit = try {
      carContext.getCarService(ConstraintManager::class.java)
          .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
  } catch (_: Exception) { 6 }
  ```

- **Header**: must call `setStartHeaderAction(Action.BACK)` (or `Action.APP_ICON` for root).
- **End header actions**: supported (max 2), but keep to 1 for compatibility with strict hosts.
- Loading state (`setLoading(true)`) and item list are **mutually exclusive**: set one or the other.

---

## Row constraints

### IMAGE_TYPE_LARGE + Metadata.setPlace() — FORBIDDEN

The Car App Library throws `IllegalStateException` at build time if a `Row` has **both**:
- `setImage(..., Row.IMAGE_TYPE_LARGE)`, and
- `setMetadata(Metadata.Builder().setPlace(...).build())`

**Fix**: use `Row.IMAGE_TYPE_SMALL` when the row also carries place metadata.  
`IMAGE_TYPE_SMALL` is safe for all template types and still displays the station icon.

### setIsBrowsable

Rows that **push a new screen** must call `.setIsBrowsable(true)`.  
This renders the chevron disclosure indicator and satisfies strict host validators.  
Action/toggle rows (Zoom In/Out, Sort toggle) should NOT be browsable.

### addText limit

Each `Row` accepts **at most 2** `addText()` calls. Exceeding this throws `IllegalStateException`.

### DistanceSpan

`PlaceListMapTemplate` and `PlaceListNavigationTemplate` rows require at least one text span of
type `DistanceSpan` on non-browsable rows. Include it even on browsable rows for safety.

---

## PlaceListMapTemplate

- **Anchor**: set an anchor `Place` for the user's current location marker.
- **Rows**: each row should carry `Metadata.Builder().setPlace(...)` so the host can pin markers on
  the map.
- **Images**: use `IMAGE_TYPE_SMALL` — `IMAGE_TYPE_LARGE` is rejected when Place metadata is set.
- **Max rows**: same `ConstraintManager.CONTENT_LIMIT_TYPE_LIST` limit as `ListTemplate`.

---

## ActionStrip

Reference: https://developer.android.com/design/ui/cars/guides/components/action-strip

The action strip is a row of icon/label buttons rendered at the top-right of the template.

### Limits

| Template type | Max actions |
|---|---|
| Most templates (List, Grid, Pane, Message, SignIn…) | **2** |
| Map-based templates (MapWithContentTemplate, PlaceListMapTemplate, NavigationTemplate…) | **4** |

### Additional constraints

- **At most 1 label button** (action with a text label) per template. All other actions must be icon-only.
- `Action.BACK` and `Action.APP_ICON` are **not** allowed in an `ActionStrip` — they belong in `Header.setStartHeaderAction()` only.
- Each action must have either a title or an icon (or both); a completely empty action is rejected.
- Actions with only a title (no icon) are rejected on map templates by strict host validators — always add an icon on map-based templates.

### Visibility

- On map-based templates the action strip disappears after **10 seconds** of inactivity.
- It reappears on any user interaction with the screen.
- Individual actions can be flagged as **persistent** to prevent them from hiding:
  ```kotlin
  Action.Builder()
      .setFlags(Action.FLAG_IS_PERSISTENT)
      // ...
      .build()
  ```
- On small-screen vehicles the Car App Library may hide the action strip after 10 s even with rotary focus.

### Guidance

- Use for **secondary/tertiary** actions only; primary actions belong in a FAB or a row button.
- Do not combine an action strip and a floating action button on the same template.
- Button order is controlled by the app (order of `addAction()` calls).

---

## safeCarTemplate wrapper

`CarTemplateSafe.kt` wraps every `onGetTemplate()` call:

- Catches any `Exception` thrown during template build (e.g. `IllegalStateException` from the
  library's constraint validators).
- Logs the full stack trace to **Logcat** at ERROR level (tag = screen class name).
- Persists the error to **DiagnosticStore** (accessible via Settings → Diagnostics on the phone).
- Returns a `MessageTemplate` with the screen name, template type, error class + message, and top
  app-package stack frames — so the head unit shows a readable debug overlay instead of crashing.

> Host-side rejections (after `onGetTemplate()` returns) are NOT catchable in this wrapper.
> For those, check Android Auto logcat with `adb logcat -s CarApp`.

---

## Debugging checklist

1. Run DHU via `./scripts/run-dhu.sh --adb` and enable **debug overlay** in Android Auto developer settings to see the template step counter.
2. Watch logcat: `adb logcat -s CarApp:V CustomMapPoiScreen:V NativeMapPoiScreen:V`.
3. When a `MessageTemplate` error overlay appears on the head unit, the DiagnosticStore on the phone holds the full stack trace.
4. Common rejection causes:
   - `IMAGE_TYPE_LARGE` + Place metadata on the same Row → use `IMAGE_TYPE_SMALL`
   - ActionStrip with more than 2 actions on a non-map template (or more than 4 on a map template) → trim actions
   - ActionStrip with a title-only action (no icon) on a map template → always add an icon
   - More than 1 label button (title+icon or title-only) in the same ActionStrip → keep at most 1
   - More than 6 rows in a ListTemplate → use `ConstraintManager`
   - Navigation rows missing `setIsBrowsable(true)` → add it to all screen-pushing rows
   - Template quota exceeded (> 5 steps) → restructure navigation flow
