# Android Architecture Guide: Flow, State, and ViewModel Best Practices

This document outlines the architectural patterns and standards used in Gaston to achieve clean, decoupled, and highly maintainable Android applications. These patterns resolve common issues such as high class complexity, state synchronization cycles, memory leaks, and oversized Composables.

---

## 1. Core Architecture Pattern: MVVM with UDF
We adhere strictly to the **Model-View-ViewModel (MVVM)** pattern powered by **Unidirectional Data Flow (UDF)**.

```
       +---------------------------------------------+
       |                  ViewModel                  |
       +---------------------+-----------------------+
                             |
                    UI State | (StateFlow / Read-Only)
                             v
       +---------------------+-----------------------+
       |               Jetpack Compose               |
       |                (Screen / UI)                |
       +---------------------+-----------------------+
                             |
              User Actions   | (UI Events / Callbacks)
                             v
       +---------------------+-----------------------+
       |                  ViewModel                  |
       +---------------------------------------------+
```

### Unidirectional Data Flow (UDF) Rules:
1. **State Flows Down**: The UI layer must only consume state. It never mutates state directly.
2. **Events Flow Up**: User interactions (button clicks, text input, chip toggles) are propagated up to the ViewModel as function calls or UI events.
3. **Immutable State**: The UI State is represented by a single, immutable data class.

---

## 2. Separate UI State & ViewModel Responsibilities

To keep classes small and readable, we divide responsibilities cleanly between the UI (Composables) and the ViewModel.

| Responsibility | Handled By | Why? |
| :--- | :--- | :--- |
| **Render UI Elements** | Composable Screen | Focuses strictly on layout, themes, colors, and layout modifiers. |
| **Ephemeral UI State** | Composable Screen | Dialog open/close toggles, animations, text field focus. These don't survive process death and don't need business logic. |
| **Business / Data Fetching** | ViewModel | Ensures fetching, networking, and database operations survive screen rotations and Composable recompositions. |
| **Location Polling / Services** | ViewModel | Keeps background processes, permissions, and lifecycle operations safely decoupled from layout renderers. |
| **Local List Derivations** | ViewModel (`StateFlow`) | Calculating sorting, filtering, and distance derivations reactively so Compose receives pre-computed lists instantly. |

---

## 3. Designing a Clean UI State

Always represent the screen's state with a single, immutable data class representing the final presentation state of the UI.

```kotlin
data class PhoneDashboardUiState(
    val favoriteIds: Set<String> = emptySet(),
    val rawNearbyPois: List<Poi> = emptyList(),
    val isLoadingPois: Boolean = false,
    val searchError: String? = null,
    val userLat: Double? = null,
    val userLon: Double? = null,
    val fuelForecastState: FuelForecastUiState = FuelForecastUiState(fuelId = "gazole", locationKey = ""),
    val fuelForecastLoading: Boolean = false,
    val nearbyFuelPois: List<Poi> = emptyList(),
    val nearbyElectricPois: List<Poi> = emptyList(),
    val showLoaderByDelay: Boolean = false
)
```

---

## 4. Advanced Reactive Coroutine Flow Patterns

A major source of complexity is managing multiple state inputs that trigger network updates or local list transformations. We solve this by modeling inputs as `Flow` streams and using reactive operators to combine them.

### Preventing standard Kotlin `combine` limits (>5 parameters)
The Kotlin Coroutines `combine` operator has specific overloads up to 5 parameters. For more parameters, combining them directly results in untyped arrays. We solve this by combining **sub-states in smaller batches** and joining them:

```kotlin
val uiState: StateFlow<PhoneDashboardUiState> = combine(
    combine(favoriteIds, rawNearbyPois, isLoadingPois, searchError) { favs, raw, loading, err ->
        Pair(Pair(favs, raw), Pair(loading, err))
    },
    combine(userLat, userLon, showLoaderByDelay) { lat, lon, delay ->
        Pair(Pair(lat, lon), delay)
    },
    combine(fuelForecastState, fuelForecastLoading, nearbyFuelPois, nearbyElectricPois) { forecast, forecastLoading, fuelPois, electricPois ->
        Pair(Pair(forecast, forecastLoading), Pair(fuelPois, electricPois))
    }
) { part1, part2, part3 ->
    PhoneDashboardUiState(
        favoriteIds = part1.first.first,
        rawNearbyPois = part1.first.second,
        isLoadingPois = part1.second.first,
        searchError = part1.second.second,
        userLat = part2.first.first,
        userLon = part2.first.second,
        showLoaderByDelay = part2.second,
        fuelForecastState = part3.first.first,
        fuelForecastLoading = part3.first.second,
        nearbyFuelPois = part3.second.first,
        nearbyElectricPois = part3.second.second
    )
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PhoneDashboardUiState())
```

### Avoiding Memory Leaks and Redundant Computations
- **`stateIn` / `sharedIn`**: Always convert cold flows to hot StateFlows in the ViewModel.
- **`SharingStarted.WhileSubscribed(5000)`**: Keeps upstream flows active for exactly 5 seconds after the last UI collector detaches (handling configuration changes like screen rotations without re-triggering expensive database or network requests).
- **`distinctUntilChanged()`** and **`debounce()`**: Prevents redundant execution when multiple quick configurations are changed simultaneously (e.g., fast user typing or slider adjustments).

---

## 5. Prevents Cycles and Loops

Circular dependencies occur when Composable events cause a state change that in turn triggers a new side effect, creating an infinite loop.

### Core Safeguards against cycles:
1. **Never trigger network/fetch operations directly from Compose `LaunchedEffect` dependencies that are mutated during the operation itself.**
2. **ViewModel manages Trigger Keys**: Use a dedicated, immutable class (like `FetchKey`) containing strictly the inputs that affect *what* is fetched. Debounce and filter this key stream using `.distinctUntilChanged()` inside the `viewModelScope`.
3. **No UI State Writeback**: Never mutate the UI State to propagate local Compose modifications. Let the Composable handle its transient state (like text selection and field inputs), and push final results to the ViewModel.

---

## 6. Dependency Injection with Koin

When your screens have dynamic dependencies (e.g. map engines or optional feature sets loaded on demand), declare them as optional or delegable flows inside the ViewModel.

### ViewModel Definition
```kotlin
class PhoneDashboardViewModel(
    private val settingsManager: SettingsManager,
    private val fuelForecastRepository: FuelForecastRepository?,
    private val context: Context
) : ViewModel() {
    private val poiProviderFlow = MutableStateFlow<PoiProvider?>(null)

    fun setPoiProvider(provider: PoiProvider?) {
        poiProviderFlow.value = provider
    }
}
```

### Koin Registration (`AppModule.kt`)
```kotlin
viewModel {
    PhoneDashboardViewModel(
        settingsManager = get(),
        fuelForecastRepository = getOrNull(),
        context = androidContext()
    )
}
```

### Compose Retrieval (`PhoneDashboardScreen.kt`)
```kotlin
@Composable
fun PhoneDashboardScreen(
    poiProvider: PoiProvider?,
    viewModel: PhoneDashboardViewModel = org.koin.androidx.compose.koinViewModel()
) {
    // Pass dynamic context elements to the ViewModel reactively
    LaunchedEffect(poiProvider) {
        viewModel.setPoiProvider(poiProvider)
    }
}
```

---

## 7. Golden Rules for Class Size and Maintenance

To prevent files from exceeding 300–400 lines:
1. **Extract Complex Sub-layouts**: Move large UI sub-trees to separate files in the same feature folder (e.g. `PhoneDashboardMainContent.kt`, `PhoneDashboardTopBar.kt`).
2. **Use ViewModel as a Clean API**: Composables must only read properties from `uiState` and invoke simple methods on the ViewModel (e.g. `viewModel.toggleFavorite(poi)`).
3. **No Complex Logic in UI**: If you see `map`, `filter`, `sortedWith`, `approxDistanceKm`, or network try-catches in your Composables, refactor them immediately into derived properties in the ViewModel.
