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
import androidx.compose.runtime.derivedStateOf
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
import fr.geoking.gaston.PoiProviderSelectionMode
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.StationMapFilters
import fr.geoking.gaston.VehicleType
import fr.geoking.gaston.api.geocoding.GeocodedPlace
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.effectiveProviders
import fr.geoking.gaston.effectiveProvidersAt
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.intent.NavDestination
import fr.geoking.gaston.poi.EnergyFilterMode
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.PoiProviderType
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Inputs that change WHAT is fetched (which providers, which categories).
 *
 * Note: `mapEnergyMode`, `selectedMapEnergyTypes`, `mapPowerLevels`,
 * `vehicleEnergy`, `fuelCard`, `selectedMapConnectorTypes`, etc. are
 * deliberately excluded. They only affect post-fetch filtering and are
 * applied locally in `derivedStateOf` below, so toggling them re-renders
 * the "Nearest stations" widget instantly from the cached raw POIs.
 */
private data class FetchKey(
    val selectedLocation: GeocodedPlace?,
    val providerMode: PoiProviderSelectionMode,
    val effectiveProviders: Set<PoiProviderType>,
    val amenityFetchUnion: Set<String>,
    val useVehicleFilter: Boolean,
    val vehicleType: VehicleType,
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
    onOpenRoutes: (NavDestination?, NavDestination?) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
    onOpenFuelForecast: () -> Unit,
    onOpenEmergency: () -> Unit,
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

    // Raw POIs from the last successful fetch (unfiltered, untrimmed).
    // Kept across energy/fuel filter toggles so we can re-derive the displayed
    // list locally without hitting the network or the SelectorPoiProvider cache.
    var rawNearbyPois by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var isLoadingPois by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var userLat by remember { mutableStateOf<Double?>(settings.lastKnownLat) }
    var userLon by remember { mutableStateOf<Double?>(settings.lastKnownLon) }
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

    // Fetch raw POIs. The trigger keys only include inputs that change WHAT
    // is fetched from the network / underlying cache (location, provider mode,
    // and category-affecting settings). Filter-only settings (energy mode,
    // fuel-type chips, power chips, fuel card, vehicle energy, etc.) are NOT
    // keys here so toggling them doesn't re-fetch — `nearbyPois` is derived
    // locally below from `rawNearbyPois`.
    LaunchedEffect(poiProvider, hasLocationPermission, selectedSearchLocation) {
        if (poiProvider == null) return@LaunchedEffect

        snapshotFlow {
            val loc = selectedSearchLocation
            val lat = loc?.latitude ?: userLat
            val lon = loc?.longitude ?: userLon
            val effectiveProviders = if (lat != null && lon != null) {
                settings.effectiveProvidersAt(lat, lon)
            } else {
                settings.effectiveProviders()
            }
            FetchKey(
                selectedLocation = selectedSearchLocation,
                providerMode = settings.poiProviderSelectionMode,
                effectiveProviders = effectiveProviders,
                amenityFetchUnion = settings.selectedOverpassAmenityTypes + settings.cacheWarmAmenityTypes,
                useVehicleFilter = settings.useVehicleFilter,
                vehicleType = settings.vehicleType,
            )
        }
            .distinctUntilChanged()
            .debounce(300)
            .collectLatest { key ->
                val selectedLoc = key.selectedLocation
                val showLoadingIndicator = rawNearbyPois.isEmpty()
                if (showLoadingIndicator) {
                    isLoadingPois = true
                }
                searchError = null

                val baseLat: Double?
                val baseLon: Double?

                if (selectedLoc != null) {
                    baseLat = selectedLoc.latitude
                    baseLon = selectedLoc.longitude
                } else if (hasLocationPermission || (userLat != null && userLon != null)) {
                    // If we have a cached location, we use it immediately.
                    // If we have permission, we also trigger a fresh location update in the background.
                    if (hasLocationPermission) {
                        scope.launch {
                            val freshLoc = LocationHelper.getCurrentLocation(context)
                            if (freshLoc != null && (freshLoc.latitude != userLat || freshLoc.longitude != userLon)) {
                                userLat = freshLoc.latitude
                                userLon = freshLoc.longitude
                                settingsManager.saveLastKnownLocation(freshLoc.latitude, freshLoc.longitude)
                            }
                        }
                    }
                    baseLat = selectedLoc?.latitude ?: userLat
                    baseLon = selectedLoc?.longitude ?: userLon
                } else {
                    rawNearbyPois = emptyList()
                    searchError = "Location permission is required to find nearby stations."
                    isLoadingPois = false
                    return@collectLatest
                }

                if (baseLat != null && baseLon != null) {
                    userLat = baseLat
                    userLon = baseLon

                    try {
                        poiProvider.searchFlow(
                            PoiSearchRequest(
                                latitude = baseLat,
                                longitude = baseLon,
                                categories = setOf(fr.geoking.gaston.poi.PoiCategory.Gas, fr.geoking.gaston.poi.PoiCategory.Irve),
                                skipFilters = true
                            )
                        ).collect { result ->
                            rawNearbyPois = if (rawNearbyPois.isEmpty()) {
                                result.pois
                            } else {
                                fr.geoking.gaston.poi.PoiMerger.mergeInto(rawNearbyPois, result.pois)
                            }
                            isLoadingPois = false
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PhoneDashboardScreen", "Failed to fetch nearby POIs", e)
                        if (rawNearbyPois.isEmpty()) {
                            searchError = "Unable to fetch nearby stations. Please check your connection."
                        }
                    }
                } else {
                    searchError = "Unable to determine your location."
                    rawNearbyPois = emptyList()
                }
                isLoadingPois = false
            }
    }

    // Re-derive the visible list locally on every settings change (energy
    // mode, fuel type, power level, brand, etc.). Synchronous, no network,
    // no debounce — toggling Fuel/EV or fuel chips updates the widget
    // instantly using the already-cached `rawNearbyPois`.
    val nearbyFuelPois by remember {
        derivedStateOf {
            val baseLat = userLat
            val baseLon = userLon
            val rawPois = rawNearbyPois
            if (baseLat == null || baseLon == null || rawPois.isEmpty()) {
                return@derivedStateOf emptyList<Poi>()
            }
            val currentProviders = settings.effectiveProvidersAt(baseLat, baseLon)

            // When in "My Vehicle" mode for a hybrid, we still use the vehicle gas types
            // but we MUST force the energy mode to Fuel for THIS specific list.
            val fuelSettings = if (settings.useVehicleFilter && settings.vehicleEnergy == "hybrid") {
                settings.copy(useVehicleFilter = false, mapEnergyMode = EnergyFilterMode.Fuel, selectedMapEnergyTypes = settings.vehicleGasTypes)
            } else {
                settings
            }
            val fuelIds = fuelSettings.effectiveMapEnergyFilterIds() - "electric"

            StationMapFilters.apply(
                settings = fuelSettings,
                pois = rawPois,
                providers = currentProviders,
                skipWhenOnlyOverpass = true
            )
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
        }
    }

    val nearbyElectricPois by remember {
        derivedStateOf {
            val baseLat = userLat
            val baseLon = userLon
            val rawPois = rawNearbyPois
            if (baseLat == null || baseLon == null || rawPois.isEmpty()) {
                return@derivedStateOf emptyList<Poi>()
            }
            val currentProviders = settings.effectiveProvidersAt(baseLat, baseLon)

            // Force Electric mode for this list
            val electricSettings = if (settings.useVehicleFilter && settings.vehicleEnergy == "hybrid") {
                settings.copy(useVehicleFilter = false, mapEnergyMode = EnergyFilterMode.Electric)
            } else {
                settings
            }

            StationMapFilters.apply(
                settings = electricSettings,
                pois = rawPois,
                providers = currentProviders,
                skipWhenOnlyOverpass = true
            )
                .sortedBy { approxDistanceKm(baseLat, baseLon, it.latitude, it.longitude) }
                .take(5)
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
                    .padding(padding),
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
                nearbyFuelPois = nearbyFuelPois,
                nearbyElectricPois = nearbyElectricPois,
                searchError = searchError,
                mapDepsReady = mapDepsReady,
                fuelForecastRepository = fuelForecastRepository,
                fuelForecastState = fuelForecastState,
                fuelForecastLoading = fuelForecastLoading,
                onOpenMap = onOpenMap,
                onOpenRoutes = onOpenRoutes,
                onOpenFuelForecast = onOpenFuelForecast,
                onOpenEmergency = onOpenEmergency,
                onOpenSettings = onOpenSettings,
                onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
                onRequestLocationPermission = onRequestLocationPermission,
                onLocationSelected = onLocationSelected,
                onToggleFavorite = { settingsManager.toggleFavoriteLocation(it) },
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
