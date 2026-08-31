package fr.geoking.gaston.ui.map

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.maps.model.LatLng
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.StationMapFilters
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.api.traffic.TrafficProviderFactory
import fr.geoking.gaston.auto.AutoMapCamera
import fr.geoking.gaston.auto.AutoMapHeading
import fr.geoking.gaston.auto.AutoSurfaceRenderer
import fr.geoking.gaston.auto.MapOrientationMode
import fr.geoking.gaston.auto.mapsforge.CarMapsforgeRenderer
import fr.geoking.gaston.auto.mapsforge.MapsforgeMapManager
import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.effectiveProvidersAt
import fr.geoking.gaston.filterPoisByViewport
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.shared.diagnostics.DiagnosticStore
import fr.geoking.gaston.ui.components.MapLoadingOverlay
import fr.geoking.gaston.ui.components.SearchCategorySelector
import fr.geoking.gaston.ui.components.SearchMode
import fr.geoking.gaston.ui.components.SearchModeSelector
import fr.geoking.gaston.ui.components.rememberSearchMode
import fr.geoking.gaston.ui.SettingsScreen
import fr.geoking.gaston.ui.SettingsScreenPage
import fr.geoking.gaston.ui.anim.AnimationPalette
import fr.geoking.gaston.ui.components.MapOverlayWidgets
import fr.geoking.gaston.ui.components.MapScaffold
import fr.geoking.gaston.ui.dashboard.GastonTheme
import fr.geoking.gaston.ui.dashboard.PhoneDashboardDestinationSearch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun SurfaceCustomMapScreen(
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
    initialCenter: LatLng? = null,
    initialZoom: Float? = null,
    showAds: Boolean = false
) {
    val context = LocalContext.current
    val settings by settingsManager.settings.collectAsState()
    val errorLog by diagnostics.errorLog.collectAsState()
    var showMapSettings by remember { mutableStateOf(false) }
    var initialSettingsPage by remember { mutableStateOf(SettingsScreenPage.MapConfig) }

    var mapLat by remember { mutableStateOf(initialSelectedPoi?.latitude ?: initialCenter?.latitude ?: settings.lastKnownLat ?: 48.8566) }
    var mapLon by remember { mutableStateOf(initialSelectedPoi?.longitude ?: initialCenter?.longitude ?: settings.lastKnownLon ?: 2.3522) }
    var zoom by remember { mutableStateOf((initialZoom ?: 14f).toInt().coerceIn(4, 18)) }
    var bearing by remember { mutableStateOf(0f) }
    var orientationMode by remember { mutableStateOf(MapOrientationMode.NorthUp) }

    var surfaceWidth by remember { mutableStateOf(0) }
    var surfaceHeight by remember { mutableStateOf(0) }
    var selectedPoi by remember { mutableStateOf<Poi?>(initialSelectedPoi) }
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAddressSearch by remember { mutableStateOf(false) }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var poiSortOrder by remember { mutableStateOf(PoiSortOrder.Distance) }
    var detailRequestPoiId by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(favoritesRepo) {
        if (favoritesRepo != null) {
            favoriteIds = favoritesRepo.getFavorites().map { it.id }.toSet()
        }
    }

    val cameraFlow = remember(mapLat, mapLon, zoom) {
        snapshotFlow { MapCameraSample(mapLat, mapLon, zoom.toFloat()) }
            .distinctUntilChanged()
            .debounce(350)
    }

    val effectiveProviders = remember(settings, mapLat, mapLon) {
        settings.effectiveProvidersAt(mapLat, mapLon)
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
        mapWidthPx = surfaceWidth,
        mapHeightPx = surfaceHeight,
        isLocationPermissionGranted = true,
        requestLocationPermission = {}
    )

    val poisInView = remember(mapData.cachedPois, mapLat, mapLon, zoom, surfaceWidth, surfaceHeight, settings, effectiveProviders) {
        val filteredByFilters = StationMapFilters.apply(
            settings = settings,
            pois = mapData.cachedPois,
            providers = effectiveProviders,
            skipWhenOnlyOverpass = true
        )
        filterPoisByViewport(
            pois = filteredByFilters,
            lat = mapLat,
            lon = mapLon,
            zoom = zoom.toFloat(),
            widthPx = surfaceWidth,
            heightPx = surfaceHeight
        )
    }

    val filteredPois = remember(poisInView, showFavoritesOnly, favoriteIds) {
        if (showFavoritesOnly && favoriteIds.isNotEmpty()) {
            poisInView.filter { it.id in favoriteIds }
        } else {
            poisInView
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

    val currentSearchMode = rememberSearchMode(settings)

    GastonTheme(themeMode = settings.uiThemeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            MapScaffold(
                title = stringResource(R.string.map_title_gas_stations),
                settingsManager = settingsManager,
                mapCenterLatitude = mapLat,
                mapCenterLongitude = mapLon,
                onBack = onBack,
                onRefresh = { mapActions.refresh(true, MapCameraSample(mapLat, mapLon, zoom.toFloat())) },
                onLocateMe = {
                    scope.launch {
                        val (lat, lon) = LocationHelper.getInitialLocation(context, settingsManager)
                        mapLat = lat
                        mapLon = lon
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
                showAds = showAds
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SearchModeSelector(currentMode = currentSearchMode, settingsManager = settingsManager)
                        SearchCategorySelector(
                            currentMode = currentSearchMode,
                            settings = settings,
                            settingsManager = settingsManager,
                            onOpenSettings = { pages: List<SettingsScreenPage>? ->
                                initialSettingsPage = pages?.firstOrNull() ?: SettingsScreenPage.MapConfig
                                showMapSettings = true
                            }
                        )
                        if (showAddressSearch) {
                            PhoneDashboardDestinationSearch(
                                geocodingClient = geocodingClient,
                                hasLocationPermission = true,
                                userLat = mapLat,
                                userLon = mapLon,
                                selectedSearchLocation = null,
                                settings = settings,
                                onLocationSelected = { loc ->
                                    if (loc != null) {
                                        mapLat = loc.latitude
                                        mapLon = loc.longitude
                                        showAddressSearch = false
                                    }
                                },
                                onOpenRoutes = { _, _ -> },
                                onToggleFavorite = { settingsManager.toggleFavoriteLocation(it) }
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        var surfaceRendererRef by remember { mutableStateOf<AutoSurfaceRenderer?>(null) }

                        LaunchedEffect(mapLat, mapLon, zoom, bearing, orientationMode, filteredPois, selectedPoi) {
                            val r = surfaceRendererRef ?: return@LaunchedEffect
                            r.updateLocation(mapLat, mapLon, zoom)
                            r.setMapOrientation(orientationMode, bearing)
                            r.updatePois(
                                newPois = filteredPois,
                                effectiveEnergyTypes = settings.effectiveMapEnergyFilterIds(),
                                effectivePowerLevels = settings.effectiveIrvePowerLevels(),
                                selectedId = selectedPoi?.id
                            )
                        }

                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(zoom, bearing, mapLat, mapLon) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val degreesPerPixelX = 360.0 / (256.0 * (1 shl zoom))
                                        val latRad = Math.toRadians(mapLat)
                                        val degreesPerPixelY = degreesPerPixelX * cos(latRad)

                                        val bearingRad = Math.toRadians(bearing.toDouble())
                                        val cosB = cos(bearingRad)
                                        val sinB = sin(bearingRad)
                                        val rotatedDragX = dragAmount.x * cosB - dragAmount.y * sinB
                                        val rotatedDragY = dragAmount.x * sinB + dragAmount.y * cosB

                                        mapLon -= rotatedDragX * degreesPerPixelX
                                        mapLat += rotatedDragY * degreesPerPixelY
                                    }
                                },
                            factory = { ctx ->
                                SurfaceView(ctx).apply {
                                    holder.addCallback(object : SurfaceHolder.Callback {
                                        var renderer: AutoSurfaceRenderer? = null
                                        override fun surfaceCreated(holder: SurfaceHolder) {}
                                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                            renderer?.stop()
                                            surfaceWidth = width
                                            surfaceHeight = height
                                            val r = AutoSurfaceRenderer(ctx, holder.surface, width, height)
                                            renderer = r
                                            surfaceRendererRef = r
                                            r.start()
                                        }
                                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                                            renderer?.stop()
                                            renderer = null
                                            surfaceRendererRef = null
                                        }
                                    })
                                }
                            }
                        )

                        MapLoadingOverlay(isLoading = mapData.isLoading, palette = palette)

                        MapOverlayWidgets(
                            bearing = bearing,
                            zoom = zoom.toFloat(),
                            latitude = mapLat,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, bottom = 16.dp)
                        )
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
                sortOrder = poiSortOrder,
                poisForOverlay = filteredPois,
                onCenterMapOnPoi = { poi ->
                    mapLat = poi.latitude
                    mapLon = poi.longitude
                },
                onInvalidate = { mapActions.invalidate() },
                initialSelectedPoi = initialSelectedPoi,
                detailRequestPoiId = detailRequestPoiId,
                onDetailRequestConsumed = { detailRequestPoiId = null },
                carouselModifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun SurfaceMapsforgeMapScreen(
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
    initialCenter: LatLng? = null,
    initialZoom: Float? = null,
    showAds: Boolean = false
) {
    val context = LocalContext.current
    val settings by settingsManager.settings.collectAsState()
    val errorLog by diagnostics.errorLog.collectAsState()
    var showMapSettings by remember { mutableStateOf(false) }
    var initialSettingsPage by remember { mutableStateOf(SettingsScreenPage.MapConfig) }

    var mapLat by remember { mutableStateOf(initialSelectedPoi?.latitude ?: initialCenter?.latitude ?: settings.lastKnownLat ?: 48.8566) }
    var mapLon by remember { mutableStateOf(initialSelectedPoi?.longitude ?: initialCenter?.longitude ?: settings.lastKnownLon ?: 2.3522) }
    var zoom by remember { mutableStateOf((initialZoom ?: 14f).toInt().coerceIn(4, 18)) }
    var bearing by remember { mutableStateOf(0f) }
    var orientationMode by remember { mutableStateOf(MapOrientationMode.NorthUp) }

    var surfaceWidth by remember { mutableStateOf(0) }
    var surfaceHeight by remember { mutableStateOf(0) }
    var selectedPoi by remember { mutableStateOf<Poi?>(initialSelectedPoi) }
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showAddressSearch by remember { mutableStateOf(false) }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var poiSortOrder by remember { mutableStateOf(PoiSortOrder.Distance) }
    var detailRequestPoiId by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val mapManager = remember(context) { MapsforgeMapManager(context) }

    LaunchedEffect(favoritesRepo) {
        if (favoritesRepo != null) {
            favoriteIds = favoritesRepo.getFavorites().map { it.id }.toSet()
        }
    }

    val cameraFlow = remember(mapLat, mapLon, zoom) {
        snapshotFlow { MapCameraSample(mapLat, mapLon, zoom.toFloat()) }
            .distinctUntilChanged()
            .debounce(350)
    }

    val effectiveProviders = remember(settings, mapLat, mapLon) {
        settings.effectiveProvidersAt(mapLat, mapLon)
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
        mapWidthPx = surfaceWidth,
        mapHeightPx = surfaceHeight,
        isLocationPermissionGranted = true,
        requestLocationPermission = {}
    )

    val poisInView = remember(mapData.cachedPois, mapLat, mapLon, zoom, surfaceWidth, surfaceHeight, settings, effectiveProviders) {
        val filteredByFilters = StationMapFilters.apply(
            settings = settings,
            pois = mapData.cachedPois,
            providers = effectiveProviders,
            skipWhenOnlyOverpass = true
        )
        filterPoisByViewport(
            pois = filteredByFilters,
            lat = mapLat,
            lon = mapLon,
            zoom = zoom.toFloat(),
            widthPx = surfaceWidth,
            heightPx = surfaceHeight
        )
    }

    val filteredPois = remember(poisInView, showFavoritesOnly, favoriteIds) {
        if (showFavoritesOnly && favoriteIds.isNotEmpty()) {
            poisInView.filter { it.id in favoriteIds }
        } else {
            poisInView
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

    val currentSearchMode = rememberSearchMode(settings)

    GastonTheme(themeMode = settings.uiThemeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            MapScaffold(
                title = stringResource(R.string.map_title_gas_stations),
                settingsManager = settingsManager,
                mapCenterLatitude = mapLat,
                mapCenterLongitude = mapLon,
                onBack = onBack,
                onRefresh = { mapActions.refresh(true, MapCameraSample(mapLat, mapLon, zoom.toFloat())) },
                onLocateMe = {
                    scope.launch {
                        val (lat, lon) = LocationHelper.getInitialLocation(context, settingsManager)
                        mapLat = lat
                        mapLon = lon
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
                showAds = showAds
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SearchModeSelector(currentMode = currentSearchMode, settingsManager = settingsManager)
                        SearchCategorySelector(
                            currentMode = currentSearchMode,
                            settings = settings,
                            settingsManager = settingsManager,
                            onOpenSettings = { pages: List<SettingsScreenPage>? ->
                                initialSettingsPage = pages?.firstOrNull() ?: SettingsScreenPage.MapConfig
                                showMapSettings = true
                            }
                        )
                        if (showAddressSearch) {
                            PhoneDashboardDestinationSearch(
                                geocodingClient = geocodingClient,
                                hasLocationPermission = true,
                                userLat = mapLat,
                                userLon = mapLon,
                                selectedSearchLocation = null,
                                settings = settings,
                                onLocationSelected = { loc ->
                                    if (loc != null) {
                                        mapLat = loc.latitude
                                        mapLon = loc.longitude
                                        showAddressSearch = false
                                    }
                                },
                                onOpenRoutes = { _, _ -> },
                                onToggleFavorite = { settingsManager.toggleFavoriteLocation(it) }
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        var surfaceRendererRef by remember { mutableStateOf<CarMapsforgeRenderer?>(null) }

                        LaunchedEffect(mapLat, mapLon, zoom, bearing, orientationMode, filteredPois, selectedPoi) {
                            val r = surfaceRendererRef ?: return@LaunchedEffect
                            r.updateLocation(mapLat, mapLon, zoom)
                            r.setMapOrientation(orientationMode, bearing)
                            r.updatePois(
                                newPois = filteredPois,
                                effectiveEnergyTypes = settings.effectiveMapEnergyFilterIds(),
                                effectivePowerLevels = settings.effectiveIrvePowerLevels(),
                                selectedId = selectedPoi?.id
                            )
                        }

                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(zoom, bearing, mapLat, mapLon) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val degreesPerPixelX = 360.0 / (256.0 * (1 shl zoom))
                                        val latRad = Math.toRadians(mapLat)
                                        val degreesPerPixelY = degreesPerPixelX * cos(latRad)

                                        val bearingRad = Math.toRadians(bearing.toDouble())
                                        val cosB = cos(bearingRad)
                                        val sinB = sin(bearingRad)
                                        val rotatedDragX = dragAmount.x * cosB - dragAmount.y * sinB
                                        val rotatedDragY = dragAmount.x * sinB + dragAmount.y * cosB

                                        mapLon -= rotatedDragX * degreesPerPixelX
                                        mapLat += rotatedDragY * degreesPerPixelY
                                    }
                                },
                            factory = { ctx ->
                                SurfaceView(ctx).apply {
                                    holder.addCallback(object : SurfaceHolder.Callback {
                                        var renderer: CarMapsforgeRenderer? = null
                                        override fun surfaceCreated(holder: SurfaceHolder) {}
                                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                            renderer?.stop()
                                            surfaceWidth = width
                                            surfaceHeight = height
                                            val r = CarMapsforgeRenderer(
                                                context = ctx,
                                                surface = holder.surface,
                                                width = width,
                                                height = height,
                                                mapManager = mapManager,
                                                initialLat = mapLat,
                                                initialLon = mapLon
                                            )
                                            renderer = r
                                            surfaceRendererRef = r
                                            r.start()
                                        }
                                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                                            renderer?.stop()
                                            renderer = null
                                            surfaceRendererRef = null
                                        }
                                    })
                                }
                            }
                        )

                        MapLoadingOverlay(isLoading = mapData.isLoading, palette = palette)

                        MapOverlayWidgets(
                            bearing = bearing,
                            zoom = zoom.toFloat(),
                            latitude = mapLat,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, bottom = 16.dp)
                        )
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
                sortOrder = poiSortOrder,
                poisForOverlay = filteredPois,
                onCenterMapOnPoi = { poi ->
                    mapLat = poi.latitude
                    mapLon = poi.longitude
                },
                onInvalidate = { mapActions.invalidate() },
                initialSelectedPoi = initialSelectedPoi,
                detailRequestPoiId = detailRequestPoiId,
                onDetailRequestConsumed = { detailRequestPoiId = null },
                carouselModifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
