# Android Auto – Template Constraints & Development Notes

Reference: https://developer.android.com/training/cars/apps/library/template-restrictions

---

## Screen stack & template quota (5 steps per task)

**Official reference:** [Template restrictions](https://developer.android.com/training/cars/apps/library/template-restrictions) — see *Template quota*.

The Android Auto / Automotive OS **host** (not the app) enforces a hard cap on how many distinct
templates the driver can traverse in one **task**. In Gaston this is the main constraint on
`ScreenManager.push()` depth: each meaningful template change consumes one of five steps. Exceeding
the budget closes the car session.

### Terminology

| Term | Meaning |
|------|---------|
| **Task** | One user flow from app launch (or intent) until quota reset. Gaston is `category.POI`, so tasks do **not** reset via `NavigationTemplate`. |
| **Template step** | One host-visible template that differs from the previous step (type **or** main content). |
| **Screen stack** | Car App Library `ScreenManager` LIFO stack (`push` / `pop`). Each pushed `Screen` maps to at least one step when its template is shown — unless the push is a **refresh** (see below). |
| **Terminal template** | Allowed **last** (5th) step: `NavigationTemplate`, `PaneTemplate`, `MessageTemplate`, `MediaPlaybackTemplate`, `SignInTemplate`, or `LongMessageTemplate`. |

### The 5-step rule

1. A task allows **at most 5 template steps**.
2. The **5th step must be terminal** (see table above). Non-terminal templates (`ListTemplate`,
   `MapWithContentTemplate`, `PlaceListMapTemplate`, `GridTemplate`, `SearchTemplate`, `TabTemplate`, …)
   cannot be the last step — the host rejects or closes the app.
3. If the quota is exceeded, the host shows an error and **closes the app** (not catchable in
   `safeCarTemplate` — rejection happens after `onGetTemplate()` returns).

### What counts as a step

**Counts (+1 step):**

- `screenManager.push(NewScreen())` when the new screen returns a template with a **different type**
  or **different main content** than the previous step.
- Replacing content in a way the host treats as a new template (e.g. list → detail with a different
  template class).

**Does NOT count (refresh):**

- `invalidate()` on the **same** `Screen` when `onGetTemplate()` returns the **same template type**
  with the **same main content** (e.g. toggling sort, updating row text, loading → loaded on the
  same list structure).
- Re-showing identical template content after a pop (quota is restored — see below).

**Ambiguous — treat as counting unless proven otherwise:**

- Same template **type** but visibly different main content (e.g. `ListTemplate` with a wholly
  different menu). When in doubt, assume +1 step.

### Back navigation & quota restoration

- **`screenManager.pop()`** / **`Action.BACK`**: undoes steps; quota is restored by the number of
  steps popped.
- **Resumed screen contract**: a screen that was popped back to must return the **same template
  type** it returned when it was last visible. Changing type on resume (e.g. list was showing
  `ListTemplate`, now returns `PaneTemplate`) violates host rules.
- Gaston pattern: station detail on native map uses a **pushed** screen so BACK works correctly:

  `NativeMapPoiScreen` → `PlaceListMapStationDetailScreen` (see comment in
  `PlaceListMapStationDetailScreen.kt`). In-place detail inside `PlaceListMapTemplate` breaks BACK
  when list rows are browsable.

### Quota reset (not available to Gaston POI flows)

- **`NavigationTemplate`**: entering navigation resets quota (navigation apps only — Gaston is POI).
- **New launch intent** or **notification content intent**: starts a new task (quota reset).

POI apps must design all flows within 5 steps without relying on navigation reset.

### Terminal templates in Gaston (POI)

Gaston cannot use `NavigationTemplate`. Use these as intentional **leaf** screens:

| Template | Gaston usage |
|----------|----------------|
| `LongMessageTemplate` | Station detail (`PoiDetailScreen`), about/disclaimer/sources (`AutoAboutScreen`, …) |
| `PaneTemplate` | Valid terminal; usable for compact detail panes |
| `MessageTemplate` | Errors, short status (`ErrorScreen`, `safeCarTemplate` fallback) |
| `SignInTemplate` | Not used (no account flow) |

Plan deep flows so the **deepest** screen is terminal. Example valid 5-step POI chain:

```
Map (MapWithContent) → Settings list → Vehicle settings list → Capacity picker → LongMessage/detail
  step 1                  step 2            step 3                 step 4            step 5 ✓
```

Invalid (6 steps — host closes app):

```
Dashboard → My vehicle → Vehicle settings → Gas tank → Consumption → Range picker
  1            2              3                4            5            6 ✗
```

### Mitigations when approaching the limit

1. **Flatten menus** — merge related pickers into one list (`TabTemplate`, section headers, or
   multi-field rows) instead of one screen per field.
2. **Prefer `invalidate()` over `push()`** when updating the current screen (filters applied, sort
   changed, data loaded).
3. **Use `TabTemplate`** for peer views at the same depth (e.g. fuel vs EV dashboard tabs) instead
   of stacking two list screens.
4. **Terminal detail early** — push `LongMessageTemplate` / `PaneTemplate` for read-only detail
   rather than chaining another list underneath.
5. **Replace instead of stack** — for wizard-like flows, pop then push (net zero depth) or reuse one
   `Screen` with internal state instead of N pushed screens.
6. **Audit before adding `push()`** — trace from root: map/dashboard = step 1; count every
   browsable row that calls `screenManager.push`.

High-risk Gaston areas (deep `push` chains):

- `AutoDashboardScreen` → settings / vehicle / map settings → `AutoVehicleSettingsScreen` → per-field
  selection screens (`AutoGasTankCapacitySelectionScreen`, …).
- `AutoMapSettingsScreen` → `AutoGeneralFiltersScreen` → energy / brand / enseigne / services screens.
- `AutoAdvancedFiltersScreen` → IRVE operator / power / connector / amenity pickers.

### Debugging template steps

1. **DHU debug overlay** — `./scripts/run-dhu.sh --adb`, enable *Show template steps* in Android Auto
   developer settings. Overlay shows current step index (e.g. 3/5).
2. **Logcat** — `adb logcat -s CarApp:V` for host rejection messages when quota is exceeded.
3. **Manual trace** — from root, tap the deepest path and watch when the app closes or shows a
   quota error.

### Quick checklist for new screens

- [ ] Count template steps from root to this screen (≤ 5).
- [ ] Deepest screen uses a **terminal** template if it is step 5.
- [ ] Rows that push call `.setBrowsable(true)` (see Row constraints).
- [ ] Same-screen updates use `invalidate()`, not extra pushes.
- [ ] Popped-back screens still return their previous template **type**.

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
   - Template quota exceeded (> 5 steps) → restructure navigation flow; see [Screen stack & template quota](#screen-stack--template-quota-5-steps-per-task)
   - Inner `ListTemplate` in `TabTemplate` with header/title → remove header from nested list

---

## Agent / IDE memory

Cursor rule (auto-loaded when editing `androidApp/.../auto/`): `.cursor/rules/android-auto-constraints.mdc`

Key constraint for navigation work: **5 template steps per task** — detailed semantics, Gaston
examples, and mitigations in [Screen stack & template quota](#screen-stack--template-quota-5-steps-per-task)
above. Official: [template restrictions](https://developer.android.com/training/cars/apps/library/template-restrictions).
