package fr.geoking.gaston.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.geoking.gaston.BuildConfig
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.geocoding.GeocodedPlace
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.effectiveProviders
import fr.geoking.gaston.effectiveProvidersAt
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.intent.NavDestination
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.repository.FuelForecastRepository
import fr.geoking.gaston.ui.components.AdMobBanner
import fr.geoking.gaston.ui.dashboard.PhoneDashboardMainContent
import fr.geoking.gaston.ui.dashboard.PhoneDashboardTopBar
import fr.geoking.gaston.ui.dashboard.PhoneDashboardViewModel
import fr.geoking.gaston.ui.dashboard.GastonTheme
import fr.geoking.gaston.ui.map.PoiDetailsFullscreenDialog

@OptIn(ExperimentalMaterial3Api::class)
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
    onOpenMap: (Poi?, Float?) -> Unit,
    onOpenRoutes: (NavDestination?, NavDestination?) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
    onOpenAutoDebug: () -> Unit,
    onOpenFuelForecast: () -> Unit,
    onOpenEmergency: () -> Unit,
    onOpenSettings: (List<SettingsScreenPage>?) -> Unit,
    onRequestLocationPermission: () -> Unit = {},
    selectedSearchLocation: GeocodedPlace? = null,
    onLocationSelected: (GeocodedPlace?) -> Unit = {},
    viewModel: PhoneDashboardViewModel = org.koin.androidx.compose.koinViewModel()
) {
    val context = LocalContext.current
    val settings by settingsManager.settings.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Delegate dynamic lifecycle parameters to the ViewModel reactively
    LaunchedEffect(poiProvider, favoritesRepo, hasLocationPermission, selectedSearchLocation) {
        viewModel.setPoiProvider(poiProvider)
        viewModel.setFavoritesRepo(favoritesRepo)
        viewModel.setLocationPermission(hasLocationPermission)
        viewModel.setSelectedSearchLocation(selectedSearchLocation)
    }

    var poiForDetails by remember { mutableStateOf<Poi?>(null) }

    val energyFilterIds = settings.effectiveMapEnergyFilterIds()
    val providers = remember(settings, uiState.userLat, uiState.userLon) {
        val lat = uiState.userLat
        val lon = uiState.userLon
        if (lat != null && lon != null) {
            settings.effectiveProvidersAt(lat, lon)
        } else {
            settings.effectiveProviders()
        }
    }

    GastonTheme(themeMode = settings.uiThemeMode) {
        Scaffold(
            topBar = {
                Column {
                    PhoneDashboardTopBar(
                        isUpdateInProgress = isUpdateInProgress,
                        onOpenFavorites = onOpenFavorites,
                        onOpenSettings = { onOpenSettings(null) }
                    )
                    if (uiState.isLoadingPois) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        )
                    }
                }
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
                userLat = uiState.userLat,
                userLon = uiState.userLon,
                selectedSearchLocation = selectedSearchLocation,
                settings = settings,
                settingsManager = settingsManager,
                providers = providers,
                energyFilterIds = energyFilterIds,
                isLoadingPois = uiState.isLoadingPois,
                showLoaderByDelay = uiState.showLoaderByDelay,
                nearbyFuelPois = uiState.nearbyFuelPois,
                nearbyElectricPois = uiState.nearbyElectricPois,
                searchError = uiState.searchError,
                mapDepsReady = mapDepsReady,
                fuelForecastRepository = fuelForecastRepository,
                onOpenMap = onOpenMap,
                onOpenRoutes = onOpenRoutes,
                onOpenFuelForecast = onOpenFuelForecast,
                onOpenEmergency = onOpenEmergency,
                onOpenSettings = onOpenSettings,
                onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
                onOpenAutoDebug = onOpenAutoDebug,
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
            isFavorite = poi.id in uiState.favoriteIds,
            onToggleFavorite = if (settings.isLoggedIn && favoritesRepo != null) {
                {
                    viewModel.toggleFavorite(poi)
                }
            } else null,
            onNavigate = {
                val uri = IntentNavigationHelper.getNavigationUri(poi)
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            },
            onShowOnMap = {
                onOpenMap(it, null)
                poiForDetails = null
            },
            onDismiss = { poiForDetails = null }
        )
    }
}
