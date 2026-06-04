package fr.geoking.gaston.ui.map.maplibre

import fr.geoking.gaston.R
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.CacheManager
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.StationMapFilters
import fr.geoking.gaston.ThemeMode
import fr.geoking.gaston.MapTheme
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.api.belib.matchAvailabilityToPois
import fr.geoking.gaston.api.traffic.TrafficProviderFactory
import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.community.isCommunityPoiId
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.effectiveProvidersAt
import fr.geoking.gaston.ui.dashboard.GastonTheme
import fr.geoking.gaston.ui.dashboard.PhoneDashboardDestinationSearch
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiMerger
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.shared.location.approxDistanceKm
import fr.geoking.gaston.shared.diagnostics.DiagnosticStore
import fr.geoking.gaston.shared.network.NetworkException
import fr.geoking.gaston.ui.SettingsScreen
import fr.geoking.gaston.ui.SettingsScreenPage
import fr.geoking.gaston.ui.components.MapScaffold
import fr.geoking.gaston.premium.BillingManager
import fr.geoking.gaston.ui.components.PremiumPaywallPopup
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import fr.geoking.gaston.ui.map.MarkerStyle
import fr.geoking.gaston.ui.map.PoiDetailCard
import fr.geoking.gaston.ui.map.PoiDetailsFullscreenDialog
import fr.geoking.gaston.ui.map.AddPoiSheet
import fr.geoking.gaston.ui.map.DebugLogOverlay
import fr.geoking.gaston.ui.map.MapCameraSample
import fr.geoking.gaston.ui.map.MapErrorBanner
import fr.geoking.gaston.ui.map.rememberErrorClipboardCopyHandler
import fr.geoking.gaston.ui.map.rememberMapDataState
import fr.geoking.gaston.ui.map.PoiOverlayHost
import fr.geoking.gaston.ui.anim.AnimationPalette
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import fr.geoking.gaston.api.routex.radiusKmFromMapViewport

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun VectorMapScreen(
    poiProvider: PoiProvider,
    availabilityProviderFactory: BorneAvailabilityProviderFactory?,
    trafficProviderFactory: TrafficProviderFactory? = null,
    settingsManager: SettingsManager,
    authManager: fr.geoking.gaston.feature.auth.GoogleAuthManager?,
    diagnostics: DiagnosticStore,
    palette: AnimationPalette,
    onBack: () -> Unit,
    onPlanRoute: (() -> Unit)? = null,
    geocodingClient: GeocodingClient? = null,
    communityRepo: CommunityPoiRepository? = null,
    favoritesRepo: FavoritesRepository? = null,
    initialSelectedPoi: Poi? = null,
    initialCenter: com.google.android.gms.maps.model.LatLng? = null,
    initialZoom: Float? = null,
    showAds: Boolean = false
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val settings by settingsManager.settings.collectAsState()
    val errorLog by diagnostics.errorLog.collectAsState()

    var mapSizePx by remember { mutableStateOf(IntSize.Zero) }
    var selectedPoi by remember { mutableStateOf<Poi?>(initialSelectedPoi) }
    var showMapSettings by remember { mutableStateOf(false) }
    var initialSettingsPage by remember { mutableStateOf(SettingsScreenPage.MapConfig) }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var showAddressSearch by remember { mutableStateOf(false) }
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var fullErrorMessageToShow by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasLocationPermission = isGranted
        }
    )

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var cameraPosition by remember { mutableStateOf<CameraPosition?>(null) }
    var showAddPoiSheet by remember { mutableStateOf(false) }
    var addPoiLinkedOfficialId by remember { mutableStateOf<String?>(null) }
    var addPoiInitialName by remember { mutableStateOf("") }
    var addPoiInitialAddress by remember { mutableStateOf("") }
    var addPoiInitialLat by remember { mutableStateOf<Double?>(null) }
    var addPoiInitialLng by remember { mutableStateOf<Double?>(null) }
    var addPoiExistingCommunityId by remember { mutableStateOf<String?>(null) }

    val defaultLat = initialSelectedPoi?.latitude ?: initialCenter?.latitude ?: settings.lastKnownLat ?: 48.8566
    val defaultLng = initialSelectedPoi?.longitude ?: initialCenter?.longitude ?: settings.lastKnownLon ?: 2.3522
    val defaultZoom = (initialZoom?.toDouble()) ?: if (initialSelectedPoi != null || initialCenter != null) 15.0 else 12.0

    LaunchedEffect(favoritesRepo) {
        if (favoritesRepo != null) {
            favoriteIds = favoritesRepo.getFavorites().map { it.id }.toSet()
        }
    }

    DisposableEffect(mapLibreMap) {
        val map = mapLibreMap
        if (map == null) {
            cameraPosition = null
            onDispose { }
        } else {
            cameraPosition = map.cameraPosition
            val idleListener = MapLibreMap.OnCameraIdleListener {
                cameraPosition = map.cameraPosition
            }
            map.addOnCameraIdleListener(idleListener)
            onDispose { map.removeOnCameraIdleListener(idleListener) }
        }
    }

    val currentTarget = cameraPosition?.target ?: LatLng(defaultLat, defaultLng)
    val effectiveProviders = remember(settings, currentTarget.latitude, currentTarget.longitude) {
        settings.effectiveProvidersAt(currentTarget.latitude, currentTarget.longitude)
    }

    fun currentMapCameraSample(): MapCameraSample {
        val pos = mapLibreMap?.cameraPosition ?: cameraPosition
        val target = pos?.target ?: LatLng(defaultLat, defaultLng)
        val zoom = (pos?.zoom ?: defaultZoom).toFloat()
        return MapCameraSample(target.latitude, target.longitude, zoom)
    }

    val cameraFlow = remember {
        snapshotFlow { cameraPosition }
            .filterNotNull()
            .map { pos ->
                val target = pos.target ?: LatLng(defaultLat, defaultLng)
                MapCameraSample(target.latitude, target.longitude, pos.zoom.toFloat())
            }
            .distinctUntilChanged()
            .debounce(350)
    }

    val (mapData, mapActions) = rememberMapDataState(
        context = context,
        poiProvider = poiProvider,
        availabilityProviderFactory = availabilityProviderFactory,
        trafficProviderFactory = trafficProviderFactory,
        settingsManager = settingsManager,
        diagnostics = diagnostics,
        effectiveProvidersLabel = effectiveProviders.toString(),
        initialSelectedPoi = initialSelectedPoi,
        cameraFlow = cameraFlow,
        mapWidthPx = mapSizePx.width,
        mapHeightPx = mapSizePx.height,
        isLocationPermissionGranted = hasLocationPermission,
        requestLocationPermission = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
    )

    val poisInView = remember(mapData.cachedPois, currentTarget, cameraPosition?.zoom, mapSizePx, settings, effectiveProviders) {
        StationMapFilters.apply(
            settings = settings,
            pois = mapData.cachedPois,
            providers = effectiveProviders,
            skipWhenOnlyOverpass = true
        )
    }

    if (showMapSettings) {
        SettingsScreen(
            settingsManager = settingsManager,
            authManager = authManager,
            errorLog = errorLog,
            onDismiss = { showMapSettings = false },
            initialScreenStack = listOf(initialSettingsPage),
            onClearErrorLog = { diagnostics.clearErrors() }
        )
        return
    }

    GastonTheme(themeMode = settings.uiThemeMode) {
        MapScaffold(
            title = stringResource(R.string.map_title_gas_stations_beta),
            settingsManager = settingsManager,
            mapCenterLatitude = currentTarget.latitude,
            mapCenterLongitude = currentTarget.longitude,
            onBack = onBack,
            onRefresh = {
                mapActions.refresh(true, currentMapCameraSample())
            },
            onLocateMe = {
                scope.launch {
                    val (lat, lon) = LocationHelper.getInitialLocation(context, settingsManager)
                    mapLibreMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(lat, lon),
                            12.0
                        )
                    )
                }
            },
            onShowSettings = {
                initialSettingsPage = SettingsScreenPage.MapConfig
                showMapSettings = true
            },
            onShowSources = {
                initialSettingsPage = SettingsScreenPage.Sources
                showMapSettings = true
            },
            onPlanRoute = onPlanRoute,
            onLocatePlace = { showAddressSearch = !showAddressSearch },
            showFavoritesOnly = showFavoritesOnly,
            onShowFavoritesOnlyChange = { showFavoritesOnly = it },
            favoritesFilterEnabled = settings.isLoggedIn && favoritesRepo != null,
            isLoading = mapData.isLoading,
            palette = palette,
            showAds = showAds
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                mapData.mapErrorMessage?.let { msg ->
                    val onCopy = rememberErrorClipboardCopyHandler(msg)
                    MapErrorBanner(
                        message = msg,
                        onCopy = onCopy,
                        onViewFullError = { fullErrorMessageToShow = msg },
                        onIgnore = mapActions.clearError,
                        onRetry = { mapActions.retry(currentMapCameraSample()) }
                    )
                }

                fullErrorMessageToShow?.let { msg ->
                    AlertDialog(
                        onDismissRequest = { fullErrorMessageToShow = null },
                        title = { Text(stringResource(R.string.route_error)) },
                        text = {
                            Text(
                                text = msg,
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { fullErrorMessageToShow = null }) {
                                Text(stringResource(R.string.action_ok))
                            }
                        }
                    )
                }

                if (settings.isLoggedIn && (communityRepo != null || favoritesRepo != null)) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        if (communityRepo != null) {
                            item {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        addPoiInitialLat = currentTarget.latitude
                                        addPoiInitialLng = currentTarget.longitude
                                        addPoiLinkedOfficialId = null
                                        addPoiExistingCommunityId = null
                                        addPoiInitialName = ""
                                        addPoiInitialAddress = ""
                                        showAddPoiSheet = true
                                    },
                                    label = { Text(stringResource(R.string.action_add_poi)) }
                                )
                            }
                        }
                    }
                }

                if (showAddressSearch) {
                    PhoneDashboardDestinationSearch(
                        geocodingClient = geocodingClient,
                        hasLocationPermission = hasLocationPermission,
                        userLat = currentTarget.latitude,
                        userLon = currentTarget.longitude,
                        selectedSearchLocation = null,
                        settings = settings,
                        onLocationSelected = { loc ->
                            if (loc != null) {
                                scope.launch {
                                    mapLibreMap?.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(loc.latitude, loc.longitude),
                                            15.0
                                        )
                                    )
                                }
                                showAddressSearch = false
                            }
                        },
                        onOpenRoutes = { _, _ -> },
                        onToggleFavorite = { settingsManager.toggleFavoriteLocation(it) }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { mapSizePx = it }
                ) {
                    val configuration = LocalConfiguration.current
                    val mapPaddingBottom = if (selectedPoi != null) (configuration.screenHeightDp * 0.4f).dp else 0.dp

                    LibreMap(
                        modifier = Modifier.fillMaxSize(),
                        styleUrl = run {
                            val isDarkMode = when (settings.mapThemeMode) {
                                ThemeMode.Dark -> true
                                ThemeMode.Light -> false
                                ThemeMode.System -> isSystemInDarkTheme()
                            }
                            // If user selected theme matches current dark mode, use it.
                            // If they selected a dark theme but we are in light mode (or vice versa),
                            // fall back to defaults for that mode.
                            if (settings.mapTheme.isDark == isDarkMode) {
                                settings.mapTheme.styleUrl
                            } else {
                                if (isDarkMode) MapTheme.Dark.styleUrl else MapTheme.Voyager.styleUrl
                            }
                        },
                        initialCameraPosition = LatLng(defaultLat, defaultLng) to defaultZoom,
                        contentPaddingBottom = mapPaddingBottom,
                        onMapReady = { mapLibreMap = it },
                        poisInView = if (showFavoritesOnly && favoriteIds.isNotEmpty()) poisInView.filter { it.id in favoriteIds } else poisInView,
                        selectedPoiId = selectedPoi?.id,
                        availabilityByPoiId = mapData.availabilityByPoiId,
                        onPoiClick = { poi ->
                            selectedPoi = poi
                        },
                        effectiveEnergyTypes = settings.effectiveMapEnergyFilterIds(),
                        effectivePowerLevels = settings.effectiveIrvePowerLevels()
                    )

                    if (settings.debugLoggingEnabled) {
                        val detectedCountries = remember(currentTarget) {
                            fr.geoking.gaston.countryDisplayLabelAtMapPosition(
                                currentTarget.latitude,
                                currentTarget.longitude
                            )
                        }
                        DebugLogOverlay(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 16.dp)
                                .zIndex(2f),
                            detectedCountries = detectedCountries
                        )
                    }
                }
            }
        }

        PoiOverlayHost(
            context = context,
            settingsManager = settingsManager,
            settings = settings,
            availabilityByPoiId = mapData.availabilityByPoiId,
            favoritesRepo = favoritesRepo,
            favoriteIds = favoriteIds,
            setFavoriteIds = { favoriteIds = it },
            communityRepo = communityRepo,
            selectedPoi = selectedPoi,
            onSelectedPoiChange = { selectedPoi = it },
            poisForOverlay = if (showFavoritesOnly && favoriteIds.isNotEmpty()) {
                poisInView.filter { it.id in favoriteIds }
            } else {
                poisInView
            },
            onCenterMapOnPoi = { poi ->
                scope.launch {
                    mapLibreMap?.animateCamera(
                        CameraUpdateFactory.newLatLng(
                            LatLng(poi.latitude, poi.longitude)
                        )
                    )
                }
            },
            onInvalidate = { mapActions.invalidate() },
            initialSelectedPoi = initialSelectedPoi
        )
    }
}
