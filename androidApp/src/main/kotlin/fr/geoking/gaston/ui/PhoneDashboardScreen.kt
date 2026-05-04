package fr.geoking.gaston.ui

import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EvStation
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.StationMapFilters
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveProviders
import fr.geoking.gaston.effectiveProvidersAt
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.ui.ColorHelper
import fr.geoking.gaston.ui.SettingsScreenPage
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.api.geocoding.GeocodedPlace
import fr.geoking.gaston.intent.NavDestination
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.shared.network.NetworkService
import fr.geoking.gaston.shared.network.NetworkStatus
import fr.geoking.gaston.shared.network.NetworkType
import fr.geoking.gaston.poi.anyProvidesElectric
import fr.geoking.gaston.poi.anyProvidesFuel
import fr.geoking.gaston.shared.location.approxDistanceKm
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.repository.FuelForecastUiState
import fr.geoking.gaston.ui.components.CheapestStationsCard
import fr.geoking.gaston.ui.components.AdMobBanner
import fr.geoking.gaston.ui.components.FuelForecastChartCard
import fr.geoking.gaston.ui.components.energySelectorItems
import fr.geoking.gaston.ui.map.PoiDetailsFullscreenDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Light theme for Play Store phone surfaces (home, diagnostics, map settings). */
val PlaystoreHomeLightScheme = lightColorScheme(
    // Pastel green + yellow brand
    primary = Color(0xFF3E8E5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFF3E6),
    onPrimaryContainer = Color(0xFF0E3A24),
    secondary = Color(0xFFF2C94C),
    onSecondary = Color(0xFF2A2100),
    secondaryContainer = Color(0xFFFFF2B3),
    onSecondaryContainer = Color(0xFF2A2100),
    tertiary = Color(0xFF7BC96F),
    onTertiary = Color(0xFF0E3A24),
    surface = Color(0xFFFFFBF3),
    onSurface = Color(0xFF0F172A),
    surfaceContainerHighest = Color(0xFFF3EEDB),
    background = Color(0xFFFFFDF5),
    onBackground = Color(0xFF0F172A)
)

/** Dark theme for Play Store phone surfaces (home, diagnostics, map settings). */
val PlaystoreHomeDarkScheme = darkColorScheme(
    primary = Color(0xFF9FE2B3),
    onPrimary = Color(0xFF0B2A17),
    primaryContainer = Color(0xFF1E4D33),
    onPrimaryContainer = Color(0xFFDFF3E6),
    secondary = Color(0xFFF6E27A),
    onSecondary = Color(0xFF2A2100),
    secondaryContainer = Color(0xFF4A3C10),
    onSecondaryContainer = Color(0xFFFFF2B3),
    tertiary = Color(0xFF7BC96F),
    onTertiary = Color(0xFF052012),
    surface = Color(0xFF0F2418),
    onSurface = Color(0xFFF8FAFC),
    surfaceContainerHighest = Color(0xFF14301F),
    background = Color(0xFF0B1A12),
    onBackground = Color(0xFFF2F7F2)
)

@Composable
fun PlaystoreTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) PlaystoreHomeDarkScheme else PlaystoreHomeLightScheme
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun PlaystoreLightTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PlaystoreHomeLightScheme, content = content)
}

enum class QuickActionType { Fuel, EV, Hybrid }

/** Minimum query length before opening remote address/city autocomplete on the phone dashboard. */
private const val PHONE_DEST_AUTOCOMPLETE_MIN_CHARS = 3

private data class DashboardRow(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val type: QuickActionType? = null
)

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun PhoneDashboardScreen(
    settingsManager: SettingsManager,
    poiProvider: PoiProvider?,
    favoritesRepo: FavoritesRepository? = null,
    hasLocationPermission: Boolean,
    mapDepsReady: Boolean,
    fuelForecastRepository: FuelForecastRepository? = null,
    geocodingClient: GeocodingClient? = null,
    isUpdateInProgress: Boolean = false,
    showAds: Boolean = false,
    onOpenMap: (Poi?) -> Unit,
    onOpenRoutes: (NavDestination?) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
    onOpenFuelForecast: () -> Unit,
    onOpenSettings: (List<SettingsScreenPage>?) -> Unit,
    onRequestLocationPermission: () -> Unit = {},
    selectedSearchLocation: GeocodedPlace? = null,
    onLocationSelected: (GeocodedPlace?) -> Unit = {}
) {
    val context = LocalContext.current
    val settings by settingsManager.settings.collectAsState()
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(favoritesRepo) {
        if (favoritesRepo != null) {
            favoriteIds = favoritesRepo.getFavorites().map { it.id }.toSet()
        }
    }

    var nearbyPois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var isLoadingPois by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLon by remember { mutableStateOf<Double?>(null) }
    var poiForDetails by remember { mutableStateOf<Poi?>(null) }
    var fuelForecastState by remember {
        mutableStateOf(
            FuelForecastUiState(fuelId = "gazole", locationKey = "")
        )
    }
    var fuelForecastLoading by remember { mutableStateOf(false) }

    var destQuery by remember { mutableStateOf("") }
    var destSuggestions by remember { mutableStateOf<List<GeocodedPlace>>(emptyList()) }
    var destFocused by remember { mutableStateOf(false) }
    var destFieldHeight by remember { mutableStateOf(0) }

    LaunchedEffect(
        destQuery,
        settings.favoriteLocations,
        settings.routeHistory,
        selectedSearchLocation,
        hasLocationPermission,
        userLat,
        userLon,
        geocodingClient
    ) {
        if (destQuery.isBlank() || destQuery == selectedSearchLocation?.label) {
            destSuggestions = emptyList()
            return@LaunchedEffect
        }
        if (destQuery.length < PHONE_DEST_AUTOCOMPLETE_MIN_CHARS) {
            destSuggestions = emptyList()
            return@LaunchedEffect
        }

        val historyMatches = settings.routeHistory.filter { it.label.contains(destQuery, ignoreCase = true) }
        val favoriteMatches = settings.favoriteLocations.filter { it.label.contains(destQuery, ignoreCase = true) }
        val localSuggestions = (favoriteMatches + historyMatches).distinctBy { it.label }
        destSuggestions = localSuggestions

        val client = geocodingClient ?: return@LaunchedEffect

        delay(300)
        try {
            val biasPair: Pair<Double, Double>? = when {
                !hasLocationPermission -> null
                userLat != null && userLon != null -> userLat!! to userLon!!
                else -> {
                    val loc = withContext(Dispatchers.IO) { LocationHelper.getCurrentLocation(context) }
                    if (loc != null) loc.latitude to loc.longitude else null
                }
            }
            val biasLat = biasPair?.first
            val biasLon = biasPair?.second
            val remote = client.geocode(
                destQuery,
                limit = 8,
                biasLatitude = biasLat,
                biasLongitude = biasLon
            )
            destSuggestions = (favoriteMatches + historyMatches + remote).distinctBy { it.label }
        } catch (_: Exception) {
            // Keep local suggestions only
        }
    }

    val energyFilterIds = settings.effectiveMapEnergyFilterIds()
    val providers = remember(settings, userLat, userLon) {
        if (userLat != null && userLon != null) {
            settings.effectiveProvidersAt(userLat!!, userLon!!)
        } else {
            settings.effectiveProviders()
        }
    }

    // 400ms delay for the loader appearance to prevent "flashing" on fast/cached requests
    var showLoaderByDelay by remember { mutableStateOf(false) }
    LaunchedEffect(isLoadingPois) {
        if (isLoadingPois) {
            delay(400)
            showLoaderByDelay = true
        } else {
            showLoaderByDelay = false
        }
    }

    LaunchedEffect(poiProvider, hasLocationPermission, selectedSearchLocation) {
        if (poiProvider == null) return@LaunchedEffect

        // Use a flow to debounce settings/filter changes (300ms)
        snapshotFlow {
            listOf(
                settings.effectiveMapEnergyFilterIds(),
                settings,
                settings.useVehicleFilter,
                selectedSearchLocation
            )
        }
        .debounce(300)
        .collectLatest { params ->
            @Suppress("UNCHECKED_CAST")
            val currentEnergyIds = params[0] as Set<String>
            val settingsSnapshot = params[1] as fr.geoking.gaston.AppSettings
            val selectedLoc = params[3] as GeocodedPlace?
            isLoadingPois = true
            searchError = null

            val baseLat: Double?
            val baseLon: Double?

            if (selectedLoc != null) {
                baseLat = selectedLoc.latitude
                baseLon = selectedLoc.longitude
            } else if (hasLocationPermission) {
                val loc = LocationHelper.getCurrentLocation(context)
                baseLat = loc?.latitude
                baseLon = loc?.longitude
            } else {
                nearbyPois = emptyList()
                searchError = "Location permission is required to find nearby stations."
                isLoadingPois = false
                return@collectLatest
            }

            if (baseLat != null && baseLon != null) {
                userLat = baseLat
                userLon = baseLon
                val currentProviders = settingsSnapshot.effectiveProvidersAt(baseLat, baseLon)

                try {
                    val results = poiProvider.search(
                        PoiSearchRequest(
                            latitude = baseLat,
                            longitude = baseLon,
                            categories = emptySet(),
                            skipFilters = true
                        )
                    )

                    val filteredResults = StationMapFilters.apply(
                        settings = settingsSnapshot,
                        pois = results,
                        providers = currentProviders,
                        skipWhenOnlyOverpass = true
                    )

                    val fuelIds = currentEnergyIds - "electric"

                    nearbyPois = filteredResults
                        .sortedWith { a, b ->
                            val pricesA = if (fuelIds.isEmpty()) a.fuelPrices else a.fuelPrices?.filter { MapPoiFilter.fuelNameToId(it.fuelName) in fuelIds }
                            val pricesB = if (fuelIds.isEmpty()) b.fuelPrices else b.fuelPrices?.filter { MapPoiFilter.fuelNameToId(it.fuelName) in fuelIds }

                            val priceA = pricesA?.minByOrNull { it.price }?.price ?: Double.MAX_VALUE
                            val priceB = pricesB?.minByOrNull { it.price }?.price ?: Double.MAX_VALUE

                            if (priceA != priceB && (priceA != Double.MAX_VALUE || priceB != Double.MAX_VALUE)) {
                                priceA.compareTo(priceB)
                            } else {
                                val distA = approxDistanceKm(baseLat, baseLon, a.latitude, a.longitude)
                                val distB = approxDistanceKm(baseLat, baseLon, b.latitude, b.longitude)
                                distA.compareTo(distB)
                            }
                        }
                        .take(5)
                } catch (e: Exception) {
                    android.util.Log.e("PhoneDashboardScreen", "Failed to fetch nearby POIs", e)
                    searchError = "Unable to fetch nearby stations. Please check your connection."
                    nearbyPois = emptyList()
                }
            } else {
                searchError = "Unable to determine your location."
                nearbyPois = emptyList()
            }
            isLoadingPois = false
        }
    }

    LaunchedEffect(userLat, userLon, energyFilterIds, hasLocationPermission, fuelForecastRepository) {
        val repo = fuelForecastRepository ?: return@LaunchedEffect
        if (!hasLocationPermission && userLat == null) {
            fuelForecastState = FuelForecastUiState(
                fuelId = "gazole",
                locationKey = "",
                errorMessage = "Location needed for local price forecast."
            )
            return@LaunchedEffect
        }
        val locLatLon: Pair<Double, Double> = when {
            userLat != null && userLon != null -> Pair(userLat!!, userLon!!)
            else -> {
                val loc = withContext(Dispatchers.IO) { LocationHelper.getCurrentLocation(context) }
                if (loc == null) {
                    fuelForecastState = FuelForecastUiState(
                        fuelId = "gazole",
                        locationKey = "",
                        errorMessage = "Unable to read location for forecast."
                    )
                    return@LaunchedEffect
                }
                Pair(loc.latitude, loc.longitude)
            }
        }
        val (la, lo) = locLatLon
        fuelForecastLoading = true
        try {
            fuelForecastState = repo.refreshAndBuildUiState(la, lo, energyFilterIds)
        } catch (e: Exception) {
            android.util.Log.e("PhoneDashboardScreen", "Fuel forecast refresh failed", e)
            fuelForecastState = FuelForecastUiState(
                fuelId = energyFilterIds.firstOrNull { it != "electric" } ?: "gazole",
                locationKey = repo.locationKey(la, lo),
                errorMessage = "Could not refresh forecast."
            )
        } finally {
            fuelForecastLoading = false
        }
    }

    val quickActions = listOf(
        DashboardRow(
            title = "Fuel",
            subtitle = "Gas stations",
            icon = Icons.Default.LocalGasStation,
            type = QuickActionType.Fuel,
            onClick = {
                val isSelected = !settings.useVehicleFilter && settings.selectedPoiProviders == setOf(PoiProviderType.DataGouv)
                if (isSelected) {
                    settingsManager.setUseVehicleFilter(true)
                } else {
                    settingsManager.setUseVehicleFilter(false)
                    settingsManager.setPoiProviderTypes(setOf(PoiProviderType.DataGouv))
                    // Preserve fuel filters but ensure 'electric' is removed for Fuel-only mode
                    settingsManager.setMapEnergyTypes(settings.selectedMapEnergyTypes - "electric")
                }
            }
        ),
        DashboardRow(
            title = "EV",
            subtitle = "Charging",
            icon = Icons.Default.EvStation,
            type = QuickActionType.EV,
            onClick = {
                val isSelected = !settings.useVehicleFilter && settings.selectedPoiProviders == setOf(PoiProviderType.DataGouvElec)
                if (isSelected) {
                    settingsManager.setUseVehicleFilter(true)
                } else {
                    settingsManager.setUseVehicleFilter(false)
                    settingsManager.setPoiProviderTypes(setOf(PoiProviderType.DataGouvElec))
                    settingsManager.setMapEnergyTypes(setOf("electric"))
                }
            }
        ),
        DashboardRow(
            title = "Hybrid",
            subtitle = "Both",
            icon = Icons.Default.Map,
            type = QuickActionType.Hybrid,
            onClick = {
                val isSelected = !settings.useVehicleFilter && settings.selectedPoiProviders == setOf(PoiProviderType.Hybrid)
                if (isSelected) {
                    settingsManager.setUseVehicleFilter(true)
                } else {
                    settingsManager.setUseVehicleFilter(false)
                    settingsManager.setPoiProviderTypes(setOf(PoiProviderType.Hybrid))
                    // Preserve existing fuel filters; 'electric' will be injected by effective filters if needed
                }
            }
        )
    )

    val otherActions = listOf(
        DashboardRow(
            title = "My car settings",
            subtitle = if (settings.vehicleBrand.isNotEmpty()) "${settings.vehicleBrand} ${settings.vehicleModel}" else "Configure your vehicle",
            icon = Icons.Default.DirectionsCar,
            onClick = { onOpenSettings(listOf(SettingsScreenPage.VehicleConfig)) }
        ),
        DashboardRow(
            title = "Network & location",
            subtitle = "Diagnostics",
            icon = Icons.Default.SignalCellular4Bar,
            onClick = onOpenNetworkDiagnostics
        ),
        DashboardRow(
            title = "About",
            subtitle = "App info",
            icon = Icons.Default.Info,
            onClick = { onOpenSettings(listOf(SettingsScreenPage.About)) }
        ),
    )

    PlaystoreTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Gaston")
                            if (isUpdateInProgress) {
                                Spacer(Modifier.width(12.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Update in progress",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenFavorites) {
                            Icon(Icons.Default.Star, contentDescription = "Favorites")
                        }
                        IconButton(onClick = { onOpenSettings(null) }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                if (showAds) {
                    AdMobBanner(
                        adUnitId = BuildConfig.ADMOB_BANNER_ID,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 0. Where to? Search bar
                item {
                    Box {
                        OutlinedTextField(
                            value = destQuery,
                            onValueChange = { destQuery = it },
                            placeholder = { Text("Where to?") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { destFocused = it.isFocused }
                                .onSizeChanged { destFieldHeight = it.height },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                            singleLine = true,
                            leadingIcon = {
                                IconButton(onClick = {
                                    onOpenRoutes(
                                        selectedSearchLocation?.let {
                                            NavDestination(
                                                address = it.label,
                                                latitude = it.latitude,
                                                longitude = it.longitude
                                            )
                                        }
                                    )
                                }) {
                                    Icon(
                                        Icons.Default.Directions,
                                        contentDescription = "Open routes",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            trailingIcon = {
                                if (destQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        destQuery = ""
                                        onLocationSelected(null)
                                    }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear"
                                        )
                                    }
                                }
                            },
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent,
                            )
                        )

                        if (destFocused && destSuggestions.isNotEmpty()) {
                            Popup(
                                onDismissRequest = { destFocused = false },
                                offset = IntOffset(0, destFieldHeight),
                                properties = PopupProperties(focusable = false)
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 0.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(8.dp),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                ) {
                                    LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                                        items(destSuggestions) { suggestion ->
                                            val isHistory = settings.routeHistory.any {
                                                it.label == suggestion.label && it.latitude == suggestion.latitude && it.longitude == suggestion.longitude
                                            }
                                            val isFavorite = settings.favoriteLocations.any {
                                                it.label == suggestion.label && it.latitude == suggestion.latitude && it.longitude == suggestion.longitude
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        destQuery = suggestion.label
                                                        destFocused = false
                                                        onLocationSelected(suggestion)
                                                    }
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    when {
                                                        isFavorite -> Icons.Default.Star
                                                        isHistory -> Icons.Default.History
                                                        else -> Icons.Default.Place
                                                    },
                                                    contentDescription = null,
                                                    tint = if (isFavorite) Color(0xFFFACC15) else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.6f
                                                    ),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Text(
                                                    text = suggestion.label,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Ad banner (Play Store distribution only).
                if (BuildConfig.IS_PLAYSTORE_DISTRIBUTION) {
                    item {
                        AdMobBanner(
                            adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Energy mode: Fuel / EV / Hybrid
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        quickActions.forEach { action ->
                            val isSelected = remember(settings, action.type) {
                                !settings.useVehicleFilter && when (action.type) {
                                    QuickActionType.Fuel -> settings.selectedPoiProviders == setOf(PoiProviderType.DataGouv)
                                    QuickActionType.EV -> settings.selectedPoiProviders == setOf(PoiProviderType.DataGouvElec)
                                    QuickActionType.Hybrid -> settings.selectedPoiProviders == setOf(PoiProviderType.Hybrid)
                                    null -> false
                                }
                            }
                            Card(
                                onClick = action.onClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = action.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = action.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Energy selector (colored chips)
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        energySelectorItems(
                            settings = settings,
                            settingsManager = settingsManager,
                            providers = providers
                        )
                    }
                }

                // Nearby cheapest (loader or card)
                item {
                    if (isLoadingPois && showLoaderByDelay) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Nearby cheapest",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
                                )
                                Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.height(8.dp))
                                        Text("Searching nearby...", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    } else {
                        val cardModifier = if (!hasLocationPermission) {
                            Modifier
                                .fillMaxWidth()
                                .clickable { onRequestLocationPermission() }
                        } else {
                            Modifier.fillMaxWidth()
                        }
                        CheapestStationsCard(
                            stations = nearbyPois,
                            userLatitude = userLat,
                            userLongitude = userLon,
                            selectedEnergyIds = energyFilterIds,
                            onClick = { poiForDetails = it },
                            onMapClick = { onOpenMap(null) },
                            modifier = cardModifier,
                            emptyMessage = searchError,
                            title = if (selectedSearchLocation != null) "Cheapest near ${selectedSearchLocation.label}" else "Nearby cheapest"
                        )
                    }
                }

                // Parking + Route
                item {
                    val isParkingSelected = !settings.useVehicleFilter &&
                        settings.selectedPoiProviders == setOf(PoiProviderType.Overpass) &&
                        settings.selectedOverpassAmenityTypes == setOf("parking")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            onClick = {
                                val isSelected = !settings.useVehicleFilter &&
                                    settings.selectedPoiProviders == setOf(PoiProviderType.Overpass) &&
                                    settings.selectedOverpassAmenityTypes == setOf("parking")
                                if (isSelected) {
                                    settingsManager.setUseVehicleFilter(true)
                                } else {
                                    settingsManager.setUseVehicleFilter(false)
                                    settingsManager.setPoiProviderTypes(setOf(PoiProviderType.Overpass))
                                    settingsManager.setOverpassAmenityTypes(setOf("parking"))
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(96.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isParkingSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = if (isParkingSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalParking,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Column(verticalArrangement = Arrangement.Center) {
                                    Text(
                                        text = "Parking",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Nearby lots",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Card(
                            onClick = { onOpenRoutes(null) },
                            enabled = mapDepsReady,
                            modifier = Modifier
                                .weight(1f)
                                .height(96.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Directions,
                                    contentDescription = null,
                                    tint = if (mapDepsReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                    modifier = Modifier.size(36.dp)
                                )
                                Column(verticalArrangement = Arrangement.Center) {
                                    Text(
                                        text = "Route",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (mapDepsReady) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                    Text(
                                        text = "Plan a journey",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (mapDepsReady) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2b. Fuel price estimation entry point (moved after quick actions row)
                if (fuelForecastRepository != null) {
                    item {
                        Card(
                            onClick = onOpenFuelForecast,
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            ListItem(
                                headlineContent = { Text("Price estimation") },
                                supportingContent = { Text("Local estimate from market + nearby pumps") },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.LocalGasStation,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                trailingContent = {
                                    if (fuelForecastLoading && fuelForecastState.historyPoints.isEmpty()) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    } else {
                                        val price = fuelForecastState.historyPoints.lastOrNull()?.priceEurPerL
                                        Text(
                                            text = if (price != null) "€%.3f".format(price) else "—",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }

                // 3. Other Actions
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        otherActions.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                pair.forEach { action ->
                                    Card(
                                        onClick = action.onClick,
                                        enabled = action.enabled,
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                    ) {
                                        ListItem(
                                            headlineContent = { Text(action.title, style = MaterialTheme.typography.titleSmall) },
                                            supportingContent = { Text(action.subtitle, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            leadingContent = {
                                                Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                            },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                        )
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    poiForDetails?.let { poi ->
        PoiDetailsFullscreenDialog(
            poi = poi,
            isFavorite = poi.id in favoriteIds,
            onToggleFavorite = if (settings.isLoggedIn && favoritesRepo != null) {
                {
                    scope.launch {
                        favoritesRepo.toggleFavorite(poi)
                        favoriteIds = favoritesRepo.getFavorites().map { it.id }.toSet()
                    }
                }
            } else null,
            onNavigate = {
                val uri = IntentNavigationHelper.getNavigationUri(poi)
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            },
            onShowOnMap = {
                onOpenMap(it)
                poiForDetails = null
            },
            onDismiss = { poiForDetails = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneNetworkLocationScreen(
    networkService: NetworkService,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val networkStatus by networkService.status.collectAsState()
    var refreshTick by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var address by remember { mutableStateOf<String?>(null) }
    var latLng by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    LaunchedEffect(refreshTick) {
        loading = true
        address = null
        latLng = null
        val location = withContext(Dispatchers.IO) {
            LocationHelper.getCurrentLocation(context)
        }
        if (location != null) {
            latLng = location.latitude to location.longitude
            address = withContext(Dispatchers.IO) {
                geocodeAddress(context, location.latitude, location.longitude)
            }
        } else {
            address = "Location not available"
        }
        loading = false
    }

    PlaystoreTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Network & location") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { refreshTick++ }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Network: ${if (networkStatus.isConnected) "Connected" else "Disconnected"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Type: ${networkStatus.networkType.toReadableString()} · Operator: ${networkStatus.operatorName ?: "Unknown"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Country: ${networkStatus.countryName ?: networkStatus.countryCode ?: "Unknown"} · Roaming: ${if (networkStatus.isRoaming) "Yes" else "No"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Current location",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
                when {
                    loading -> Text("Loading coordinates…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    latLng != null -> {
                        Text(
                            "Lat: ${String.format(Locale.US, "%.6f", latLng!!.first)}, Lon: ${String.format(Locale.US, "%.6f", latLng!!.second)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            address ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    else -> Text(address ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private suspend fun geocodeAddress(context: android.content.Context, lat: Double, lon: Double): String? {
    val geocoder = Geocoder(context, Locale.getDefault())
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCoroutine { continuation ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resume(addresses.firstOrNull()?.let { formatAddress(it) })
                    }
                    override fun onError(errorMessage: String?) {
                        continuation.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()?.let { formatAddress(it) }
            }
        }
    } catch (_: Exception) {
        "Geocoding error"
    }
}

private fun formatAddress(address: Address): String {
    val sb = StringBuilder()
    for (i in 0..address.maxAddressLineIndex) {
        sb.append(address.getAddressLine(i))
        if (i < address.maxAddressLineIndex) sb.append(", ")
    }
    return sb.toString()
}

private fun NetworkType.toReadableString(): String = when (this) {
    NetworkType.WIFI -> "WiFi"
    NetworkType.FIVE_G -> "5G"
    NetworkType.FOUR_G -> "4G"
    NetworkType.THREE_G -> "3G"
    NetworkType.TWO_G -> "2G"
    NetworkType.EDGE -> "Edge"
    NetworkType.GPRS -> "GPRS"
    NetworkType.UNKNOWN -> "Unknown"
    NetworkType.NONE -> "None"
}
