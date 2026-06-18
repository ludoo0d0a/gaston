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

### Categories & allowed templates

A car app declares exactly one category in its `CarAppService` intent filter. The category determines
which templates the host accepts.

| Category | Templates allowed |
|---|---|
| `androidx.car.app.category.POI` (Gaston) | `PlaceListMapTemplate`, `MapWithContentTemplate`, `ListTemplate`, `GridTemplate`, `PaneTemplate`, `MessageTemplate`, `LongMessageTemplate`, `SearchTemplate`, `SignInTemplate`, `TabTemplate`. **No** `NavigationTemplate`. |
| `androidx.car.app.category.NAVIGATION` | All of the above **plus** `NavigationTemplate`, `RoutePreviewNavigationTemplate`, `MapWithContentTemplate` with turn-by-turn, `Alert`/`showAlert()`. Requires `NAVIGATION_TEMPLATES` permission. |
| `androidx.car.app.category.PARKING` / `CHARGING` | **Deprecated** since Car App Library 1.3 — use `POI` instead. |

POI-app consequences (enforced in Gaston):
- No `NavigationTemplate` and no `AppManager.showAlert()` — use phone HUN notifications (below).
- `PlaceListMapTemplate` / `MapWithContentTemplate` require the `MAP_TEMPLATES` permission.

### Per-template minimum car API level (`@RequiresCarApi`)

| Template / API | Min car API level |
|---|---|
| `ListTemplate`, `GridTemplate`, `MessageTemplate`, `PaneTemplate`, `PlaceListMapTemplate`, `SearchTemplate` | 1 |
| `LongMessageTemplate`, `SignInTemplate`, `ParkedOnlyOnClickListener` | 2 |
| `TabTemplate`, `ConstraintManager.getContentLimit` | 6 |
| `MapWithContentTemplate`, `setMapController`, surface pan/zoom controls | 7 |

Gaston targets `minCarApiLevel 7`, so all of the above are available. Guard anything newer with
`carContext.carAppApiLevel`.

### Heads-up notifications (HUN)

Connectivity/border alerts use **phone notifications** mirrored to the car, not `AppManager.showAlert()`:

- `Alert` / `showAlert()` only works inside `NavigationTemplate` (navigation apps). POI apps must use HUN via notifications.
- Extend with `CarAppExtender`, `setImportance(IMPORTANCE_HIGH)` (projected Auto), and `setChannelId` (Automotive OS).
- Post with `CarNotificationManager.notify()`, not `NotificationManager.notify()`.
- Declare `POST_NOTIFICATIONS` and request runtime permission on API 33+ (phone host; car still needs the notification posted from the device).
- For `CATEGORY_MESSAGE` on Automotive OS, use `MessagingStyle` plus a mark-as-read action (`SEMANTIC_ACTION_MARK_AS_READ`, no UI).

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

## ConstraintManager content limits

The host caps the number of items per template. **Never hardcode** these — query at runtime via
`carContext.getCarService(ConstraintManager::class.java).getContentLimit(type)` (requires car API 6).
The values below are the **minimum** guaranteed by the library (`integers.xml`); a host may allow more.

| Constant | Value | Applies to | Minimum |
|---|---|---|---|
| `CONTENT_LIMIT_TYPE_LIST` | 0 | `ListTemplate`, generic uniform lists | **6** |
| `CONTENT_LIMIT_TYPE_GRID` | 1 | `GridTemplate` | **6** |
| `CONTENT_LIMIT_TYPE_PLACE_LIST` | 2 | `PlaceListMapTemplate` POI rows | **6** |
| `CONTENT_LIMIT_TYPE_ROUTE_LIST` | 3 | `RoutePreviewNavigationTemplate` rows | **3** |
| `CONTENT_LIMIT_TYPE_PANE` | 4 | `PaneTemplate` rows | **4** |

Always wrap the lookup in `try { … } catch (_: Exception) { <minimum> }` (the service is missing on
older hosts) and add the most relevant items first, since the list may be truncated.

---

## GridTemplate

- Items: cap at `CONTENT_LIMIT_TYPE_GRID` (min 6).
- Each `GridItem` requires a title and an image (`setImage`); image type `IMAGE_TYPE_LARGE` or
  `IMAGE_TYPE_ICON`. No `Place`/`Metadata` markers (grid is not a map list).
- `setLoading(true)` and the item list are mutually exclusive.
- Header + ActionStrip follow the standard 2-action rule.

---

## PaneTemplate

- Rows: cap at `CONTENT_LIMIT_TYPE_PANE` (min 4); rows are **not** browsable (no screen push).
- Pane actions: **at most 2** via `Pane.Builder().addAction(...)`; one may be primary
  (`Action.FLAG_PRIMARY`). Actions need a title or icon.
- A row may carry an image (`IMAGE_TYPE_LARGE` allowed here since panes have no Place metadata).
- Valid as the **last step** of a task (one of the allowed terminal templates).

---

## SearchTemplate

- Provide a `SearchTemplate.SearchCallback` (`onSearchTextChanged`, `onSearchSubmitted`).
- Header action: `Action.BACK` or `Action.APP_ICON` only.
- Results list follows the `CONTENT_LIMIT_TYPE_LIST` cap.
- `setLoading(true)` and the item list are mutually exclusive.
- Reference: `AutoPoiSearchScreen.kt`.

---

## SignInTemplate

- Requires car API 2; valid as a terminal template.
- Sign-in methods (`InputSignInMethod`, `PinSignInMethod`, `ProviderSignInMethod`,
  `QRCodeSignInMethod` — QR needs car API 4).
- Body text and an instruction/footer are plain `String` only.
- Not used by Gaston today (no account flow) — listed for completeness.

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

## MessageTemplate vs LongMessageTemplate

| Template | Use for | Body limit |
|----------|---------|------------|
| `MessageTemplate` | Short status, errors, confirmations | **~500 chars** — always `.take(500)` |
| `LongMessageTemplate` | Station detail, about text, scrollable content | **~5000 chars** — `.take(5000)` |

Additional rules:

- Use **plain `String`** for message bodies. Avoid `SpannableString` and style spans — strict host
  validators may reject styled text and crash the session.
- `LongMessageTemplate` uses `setTitle()` + `setHeaderAction(Action.BACK)` + `addAction()` for
  actions. Do not use `Header.addEndHeaderAction()` on this template type.
- `LongMessageTemplate` body actions added via `addAction()` **must** wrap their click listener in
  `ParkedOnlyOnClickListener.create { … }`. A plain `setOnClickListener` throws
  `IllegalArgumentException` when the template is built, which the host surfaces as a rejected
  screen (see `PoiDetailScreen.kt`).
- `MessageTemplate` supports at most **2** `addAction()` calls and an optional icon.

Reference implementations: `AutoAboutScreen.kt` (long scrollable text, no actions), `PoiDetailScreen.kt` (`LongMessageTemplate` with `ParkedOnlyOnClickListener` actions), `ErrorScreen.kt` (short status).

---

## TabTemplate

- Nested `ListTemplate` inside `TabContents` must **not** set a header or title on the inner list
  (see `AutoTabTemplateScreen.kt`).

---

## Gaston map screen layout

`CustomMapPoiScreen` / `MapLibrePoiScreen` split controls across templates:

- **ActionStrip** (top-right): minimal — settings icon, optional API-errors icon (max 1–2 actions).
- **Nested ListTemplate Header** end actions: zoom, recenter, compass/orientation (icon + title where needed).

Do not put all map controls on the ActionStrip; strict hosts reject overloaded strips.

---

## Screen lifecycle & threading

- `onGetTemplate()` runs on the **main thread** and must return quickly — never block on I/O.
  Kick off async work (network, location) from `init`/`onStart` via `lifecycleScope`, keep state in
  fields, and call `invalidate()` to re-render when data arrives (see `AutoPoiSearchScreen.kt`).
- `invalidate()` requests a fresh `onGetTemplate()`; returning the **same template type with the same
  main content** is treated as a refresh and does not consume template quota.
- Register `SurfaceCallback` in `onStart`, stop the renderer in `onStop` (map screens).
- A `Screen` must always return a non-null `Template`; throwing escapes to the host unless wrapped by
  `safeCarTemplate`.

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
   - Long text in `MessageTemplate` → use `LongMessageTemplate` and cap at 5000 chars
   - `SpannableString` / styled spans in template body → use plain `String`
   - `IMAGE_TYPE_LARGE` + Place metadata on the same Row → use `IMAGE_TYPE_SMALL`
   - ActionStrip with more than 2 actions on a non-map template (or more than 4 on a map template) → trim actions
   - ActionStrip with a title-only action (no icon) on a map template → always add an icon
   - More than 1 label button (title+icon or title-only) in the same ActionStrip → keep at most 1
   - More than 6 rows in a ListTemplate → use `ConstraintManager`
   - Navigation rows missing `setIsBrowsable(true)` → add it to all screen-pushing rows
   - Template quota exceeded (> 5 steps) → restructure navigation flow
   - Inner `ListTemplate` in `TabTemplate` with header/title → remove header from nested list

---

## Agent / IDE memory

Cursor rule (auto-loaded when editing `androidApp/.../auto/`): `.cursor/rules/android-auto-constraints.mdc`
