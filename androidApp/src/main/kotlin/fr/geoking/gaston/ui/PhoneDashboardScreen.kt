package fr.geoking.gaston.ui

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.StationMapFilters
import fr.geoking.gaston.api.geocoding.GeocodedPlace
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.effectiveProviders
import fr.geoking.gaston.effectiveProvidersAt
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.intent.NavDestination
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.repository.FuelForecastUiState
import fr.geoking.gaston.shared.location.approxDistanceKm
import fr.geoking.gaston.ui.components.AdMobBanner
import fr.geoking.gaston.ui.dashboard.PhoneDashboardMainContent
import fr.geoking.gaston.ui.dashboard.PhoneDashboardTopBar
import fr.geoking.gaston.ui.dashboard.GastonTheme
import fr.geoking.gaston.ui.map.PoiDetailsFullscreenDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()

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

    val energyFilterIds = settings.effectiveMapEnergyFilterIds()
    val providers = remember(settings, userLat, userLon) {
        if (userLat != null && userLon != null) {
            settings.effectiveProvidersAt(userLat!!, userLon!!)
        } else {
            settings.effectiveProviders()
        }
    }

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

    GastonTheme(themeMode = settings.uiThemeMode) {
        Scaffold(
            topBar = {
                PhoneDashboardTopBar(
                    isUpdateInProgress = isUpdateInProgress,
                    onOpenFavorites = onOpenFavorites,
                    onOpenSettings = { onOpenSettings(null) }
                )
            },
            bottomBar = {
                if (showAds) {
                    AdMobBanner(
                        adUnitId = BuildConfig.ADMOB_BANNER_ID,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        ) { padding ->
            PhoneDashboardMainContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                geocodingClient = geocodingClient,
                hasLocationPermission = hasLocationPermission,
                userLat = userLat,
                userLon = userLon,
                selectedSearchLocation = selectedSearchLocation,
                settings = settings,
                settingsManager = settingsManager,
                providers = providers,
                energyFilterIds = energyFilterIds,
                isLoadingPois = isLoadingPois,
                showLoaderByDelay = showLoaderByDelay,
                nearbyPois = nearbyPois,
                searchError = searchError,
                mapDepsReady = mapDepsReady,
                fuelForecastRepository = fuelForecastRepository,
                fuelForecastState = fuelForecastState,
                fuelForecastLoading = fuelForecastLoading,
                onOpenMap = onOpenMap,
                onOpenRoutes = onOpenRoutes,
                onOpenFuelForecast = onOpenFuelForecast,
                onOpenSettings = onOpenSettings,
                onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
                onRequestLocationPermission = onRequestLocationPermission,
                onLocationSelected = onLocationSelected,
                onPoiSelected = { poiForDetails = it }
            )
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
