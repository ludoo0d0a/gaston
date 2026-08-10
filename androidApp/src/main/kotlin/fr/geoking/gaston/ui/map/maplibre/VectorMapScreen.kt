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
import fr.geoking.gaston.filterPoisByViewport
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
import android.widget.Toast
import fr.geoking.gaston.ui.components.SearchMode
import fr.geoking.gaston.ui.components.rememberSearchMode
import fr.geoking.gaston.ui.components.SearchModeSelector
import fr.geoking.gaston.ui.components.SearchCategorySelector
import androidx.compose.foundation.background
import fr.geoking.gaston.ui.components.MapScaffold
import fr.geoking.gaston.premium.BillingManager
import fr.geoking.gaston.ui.components.PremiumPaywallPopup
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import fr.geoking.gaston.ui.map.MarkerStyle
import fr.geoking.gaston.ui.map.MapBaseViewControl
import fr.geoking.gaston.ui.map.maplibre.resolvePhoneMapLibreStyle
import fr.geoking.gaston.ui.map.PoiDetailCard
import fr.geoking.gaston.ui.map.PoiDetailsFullscreenDialog
import fr.geoking.gaston.ui.map.AddPoiSheet
import fr.geoking.gaston.ui.map.DebugLogOverlay
import fr.geoking.gaston.ui.components.MapOverlayWidgets
import fr.geoking.gaston.ui.map.MapCameraSample
import fr.geoking.gaston.ui.map.MapErrorBanner
import fr.geoking.gaston.ui.map.rememberErrorClipboardCopyHandler
import fr.geoking.gaston.ui.map.rememberMapDataState
import fr.geoking.gaston.ui.map.PoiOverlayHost
import fr.geoking.gaston.ui.map.STATION_OVERLAY_CARD_HEIGHT
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
    var isCheapestFilterActive by remember { mutableStateOf(false) }
    var poiSortOrder by remember { mutableStateOf(fr.geoking.gaston.ui.map.PoiSortOrder.Distance) }
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
            val moveListener = MapLibreMap.OnCameraMoveListener {
                cameraPosition = map.cameraPosition
            }
            map.addOnCameraIdleListener(idleListener)
            map.addOnCameraMoveListener(moveListener)
            onDispose {
                map.removeOnCameraIdleListener(idleListener)
                map.removeOnCameraMoveListener(moveListener)
            }
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
        val filteredByFilters = StationMapFilters.apply(
            settings = settings,
            pois = mapData.cachedPois,
            providers = effectiveProviders,
            skipWhenOnlyOverpass = true
        )

        val currentZoom = (cameraPosition?.zoom ?: defaultZoom).toFloat()
        filterPoisByViewport(
            pois = filteredByFilters,
            lat = currentTarget.latitude,
            lon = currentTarget.longitude,
            zoom = currentZoom,
            widthPx = mapSizePx.width,
            heightPx = mapSizePx.height
        )
    }

    val basePois = remember(poisInView, showFavoritesOnly, favoriteIds) {
        if (showFavoritesOnly && favoriteIds.isNotEmpty()) {
            poisInView.filter { it.id in favoriteIds }
        } else {
            poisInView
        }
    }

    val isLuxembourg = remember(currentTarget) {
        fr.geoking.gaston.countryCodesAtMapPosition(
            currentTarget.latitude,
            currentTarget.longitude
        ).contains("LU")
    }

    /**
     * The list of POIs currently displayed on the map and in the bottom sheet.
     * When [isCheapestFilterActive] is true, we filter this list to only include the top 5 cheapest stations
     * (including ties) among those currently in the viewport.
     */
    val filteredPois = remember(basePois, isCheapestFilterActive, settings, isLuxembourg) {
        if (isCheapestFilterActive) {
            val fuelIds = settings.effectiveMapEnergyFilterIds() - "electric"
            fr.geoking.gaston.poi.MapPoiFilter.filterCheapest(basePois, fuelIds, isLuxembourg)
        } else {
            basePois
        }
    }

    val cheapestStationsToast = stringResource(R.string.cheapest_stations_toast, filteredPois.size)
    LaunchedEffect(isCheapestFilterActive) {
        if (isCheapestFilterActive) {
            Toast.makeText(context, cheapestStationsToast, Toast.LENGTH_SHORT).show()
        }
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
            showAds = showAds,
            floatingActionButton = {
                val currentSearchMode = rememberSearchMode(settings)
                if (currentSearchMode == SearchMode.Fuel && (isCheapestFilterActive || basePois.any { !it.fuelPrices.isNullOrEmpty() })) {
                    FloatingActionButton(
                        onClick = {
                            if (isCheapestFilterActive) {
                                isCheapestFilterActive = false
                                poiSortOrder = fr.geoking.gaston.ui.map.PoiSortOrder.Distance
                                selectedPoi = null
                            } else {
                                isCheapestFilterActive = true
                                poiSortOrder = fr.geoking.gaston.ui.map.PoiSortOrder.Price
                            }
                        },
                        containerColor = if (isCheapestFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isCheapestFilterActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_cheapest_price),
                            contentDescription = stringResource(R.string.action_show_cheapest_list)
                        )
                    }
                }
            }
        ) { padding ->
            val currentSearchMode = rememberSearchMode(settings)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchModeSelector(
                        currentMode = currentSearchMode,
                        settingsManager = settingsManager
                    )
                    SearchCategorySelector(
                        currentMode = currentSearchMode,
                        settings = settings,
                        settingsManager = settingsManager,
                        onOpenSettings = { pages ->
                            initialSettingsPage = pages?.firstOrNull() ?: SettingsScreenPage.MapConfig
                            showMapSettings = true
                        }
                    )

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
                }

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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size ->
                            if (mapSizePx != size) mapSizePx = size
                        }
                ) {
                    val mapPaddingBottom = if (selectedPoi != null) STATION_OVERLAY_CARD_HEIGHT else 0.dp
                    val mapLibreStyle = remember(settings.mapBaseView, settings.mapTheme) {
                        resolvePhoneMapLibreStyle(settings, preferDark = false)
                    }

                    LibreMap(
                        modifier = Modifier.fillMaxSize(),
                        styleUrl = mapLibreStyle.styleUrl ?: MapTheme.Voyager.styleUrl,
                        styleJson = mapLibreStyle.styleJson,
                        initialCameraPosition = LatLng(defaultLat, defaultLng) to defaultZoom,
                        contentPaddingBottom = mapPaddingBottom,
                        onMapReady = { mapLibreMap = it },
                        poisInView = filteredPois,
                        selectedPoiId = selectedPoi?.id,
                        availabilityByPoiId = mapData.availabilityByPoiId,
                        onPoiClick = { poi ->
                            if (poi != null && selectedPoi == null && !isCheapestFilterActive) {
                                poiSortOrder = fr.geoking.gaston.ui.map.PoiSortOrder.Distance
                            }
                            selectedPoi = poi
                        },
                        effectiveEnergyTypes = settings.effectiveMapEnergyFilterIds(),
                        effectivePowerLevels = settings.effectiveIrvePowerLevels()
                    )

                    MapBaseViewControl(
                        current = settings.mapBaseView,
                        onSelect = { settingsManager.setMapBaseView(it) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                            .zIndex(1f)
                    )

                    // Map overlay scale widget (placed at the bottom-left, shifts up if bottom sheet is shown)
                    MapOverlayWidgets(
                        bearing = (cameraPosition?.bearing ?: 0.0).toFloat(),
                        zoom = (cameraPosition?.zoom ?: defaultZoom).toFloat(),
                        latitude = currentTarget.latitude,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = mapPaddingBottom + 16.dp)
                            .zIndex(1f)
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
                                .padding(top = 72.dp, end = 16.dp)
                                .zIndex(2f),
                            detectedCountries = detectedCountries,
                            onRefresh = {
                                mapActions.refresh(true, currentMapCameraSample())
                            }
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
            onSelectedPoiChange = {
                selectedPoi = it
                if (it == null && !isCheapestFilterActive) {
                    poiSortOrder = fr.geoking.gaston.ui.map.PoiSortOrder.Distance
                }
            },
            sortOrder = poiSortOrder,
            poisForOverlay = filteredPois,
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
