package fr.geoking.gaston.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import fr.geoking.gaston.CacheManager
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.ThemeMode
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.poi.PoiSearchResult
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.anyProvidesElectric
import fr.geoking.gaston.poi.anyProvidesFuel
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.api.belib.matchAvailabilityToPois
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.api.traffic.TrafficInfo
import fr.geoking.gaston.api.traffic.TrafficProviderFactory
import fr.geoking.gaston.api.traffic.TrafficRequest
import fr.geoking.gaston.api.traffic.TrafficSeverity
import fr.geoking.gaston.shared.diagnostics.DiagnosticStore
import fr.geoking.gaston.shared.network.NetworkException
import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.community.isCommunityPoiId
import fr.geoking.gaston.ui.components.MapScaffold
import fr.geoking.gaston.premium.BillingManager
import fr.geoking.gaston.ui.components.PremiumPaywallPopup
import fr.geoking.gaston.ui.ColorHelper
import fr.geoking.gaston.ui.map.AddPoiSheet
import fr.geoking.gaston.ui.map.PoiDetailCard
import fr.geoking.gaston.ui.map.PoiDetailsFullscreenDialog
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import fr.geoking.gaston.ui.map.MarkerStyle
import fr.geoking.gaston.ui.map.DebugLogOverlay
import fr.geoking.gaston.ui.anim.AnimationPalette
import fr.geoking.gaston.ui.anim.AnimationPalettes
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.poi.PoiMerger
import fr.geoking.gaston.StationMapFilters
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.effectiveProvidersAt
import fr.geoking.gaston.shared.location.approxDistanceKm
import fr.geoking.gaston.shared.location.haversineKm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import fr.geoking.gaston.api.routex.radiusKmFromMapViewport
import fr.geoking.gaston.ui.map.MapCameraSample
import fr.geoking.gaston.ui.map.MapErrorBanner
import fr.geoking.gaston.ui.map.rememberErrorClipboardCopyHandler
import fr.geoking.gaston.ui.map.rememberMapDataState
import fr.geoking.gaston.ui.map.PoiOverlayHost

/** Converts a vector drawable to a BitmapDescriptor for map markers (fromResource only supports bitmaps). Scales with zoom when sizePx varies. */
private fun vectorDrawableToBitmapDescriptor(
    context: android.content.Context,
    drawableResId: Int,
    sizePx: Int
): BitmapDescriptor? {
    val drawable = ContextCompat.getDrawable(context, drawableResId) ?: return null
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/** Marker size in px (fixed, not scaling with zoom). */
private fun markerSizePxForZoom(zoom: Float): Int {
    // Larger marker + label for readability on phone screens.
    return 120
}

private data class LoadedPoiRegion(
    val centerLat: Double,
    val centerLng: Double,
    val maxRadiusKmLoaded: Int,
    val loadedAtMs: Long
)

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun MapScreen(
    poiProvider: PoiProvider,
    availabilityProviderFactory: BorneAvailabilityProviderFactory?,
    trafficProviderFactory: TrafficProviderFactory? = null,
    settingsManager: SettingsManager,
    authManager: fr.geoking.gaston.feature.auth.GoogleAuthManager?,
    diagnostics: DiagnosticStore,
    palette: AnimationPalette,
    onBack: () -> Unit,
    onPlanRoute: (() -> Unit)? = null,
    communityRepo: CommunityPoiRepository? = null,
    favoritesRepo: FavoritesRepository? = null,
    initialSelectedPoi: Poi? = null,
    initialCenter: LatLng? = null
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val settings by settingsManager.settings.collectAsState()
    val errorLog by diagnostics.errorLog.collectAsState()
    var showMapSettings by remember { mutableStateOf(false) }
    var initialSettingsPage by remember { mutableStateOf(SettingsScreenPage.MapConfig) }
    var showAddPoiSheet by remember { mutableStateOf(false) }
    var addPoiLinkedOfficialId by remember { mutableStateOf<String?>(null) }
    var addPoiInitialName by remember { mutableStateOf("") }
    var addPoiInitialAddress by remember { mutableStateOf("") }
    var addPoiInitialLat by remember { mutableStateOf<Double?>(null) }
    var addPoiInitialLng by remember { mutableStateOf<Double?>(null) }
    var addPoiExistingCommunityId by remember { mutableStateOf<String?>(null) }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var frozenPoisForSheet by remember { mutableStateOf<List<Poi>>(emptyList()) }
    val billingManager = org.koin.compose.koinInject<fr.geoking.gaston.premium.BillingManager>()
    var showPaywallForFavorite by remember { mutableStateOf(false) }


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

    val defaultLat = initialSelectedPoi?.latitude ?: initialCenter?.latitude ?: settings.lastKnownLat ?: 48.8566
    val defaultLng = initialSelectedPoi?.longitude ?: initialCenter?.longitude ?: settings.lastKnownLon ?: 2.3522
    val defaultZoom = if (initialSelectedPoi != null || initialCenter != null) 15f else 12f

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(defaultLat, defaultLng), defaultZoom)
    }

    val cameraTarget = cameraPositionState.position.target
    val effectiveProviders = remember(settings, cameraTarget.latitude, cameraTarget.longitude) {
        settings.effectiveProvidersAt(cameraTarget.latitude, cameraTarget.longitude)
    }

    var didInitialCenter by remember { mutableStateOf(false) }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && !didInitialCenter && initialSelectedPoi == null && initialCenter == null) {
            val (lat, lon) = LocationHelper.getInitialLocation(context, settingsManager)
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(lat, lon),
                12f
            )
            didInitialCenter = true
        }
    }

    var mapSizePx by remember { mutableStateOf(IntSize.Zero) }
    var selectedPoi by remember { mutableStateOf<Poi?>(initialSelectedPoi) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(favoritesRepo) {
        if (favoritesRepo != null) {
            favoriteIds = favoritesRepo.getFavorites().map { it.id }.toSet()
        }
    }

    val cameraFlow = remember(cameraPositionState) {
        snapshotFlow { cameraPositionState.position }
            .map { pos -> MapCameraSample(pos.target.latitude, pos.target.longitude, pos.zoom) }
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
        selectedPoi = selectedPoi,
        isLocationPermissionGranted = hasLocationPermission,
        requestLocationPermission = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
    )

    val poisInView = remember(mapData.cachedPois, cameraPositionState.position.target, cameraPositionState.position.zoom, mapSizePx, settings, effectiveProviders) {
        StationMapFilters.apply(
            settings = settings,
            pois = mapData.cachedPois,
            providers = effectiveProviders,
            skipWhenOnlyOverpass = true
        )
    }

    LaunchedEffect(selectedPoi, poisInView) {
        if (selectedPoi != null) {
            if (frozenPoisForSheet.isEmpty()) {
                val currentPois = if (showFavoritesOnly && favoriteIds.isNotEmpty()) {
                    poisInView.filter { it.id in favoriteIds }
                } else {
                    poisInView
                }

                val sel = selectedPoi!!
                val others = currentPois.filter { it.id != sel.id }.toMutableList()
                val sorted = mutableListOf(sel)

                var current = sel
                while (others.isNotEmpty()) {
                    val next = others.minBy { p ->
                        approxDistanceKm(current.latitude, current.longitude, p.latitude, p.longitude)
                    }
                    sorted.add(next)
                    others.remove(next)
                    current = next
                }

                frozenPoisForSheet = sorted
            }
        } else {
            frozenPoisForSheet = emptyList()
        }
    }

    if (showMapSettings) {
        SettingsScreen(
            settingsManager = settingsManager,
            authManager = authManager,
            errorLog = errorLog,
            onDismiss = { showMapSettings = false },
            initialScreenStack = listOf(initialSettingsPage)
        )
        return
    }

    MapScaffold(
        title = "Gas Stations",
        settingsManager = settingsManager,
        mapCenterLatitude = cameraPositionState.position.target.latitude,
        mapCenterLongitude = cameraPositionState.position.target.longitude,
        onBack = onBack,
        onRefresh = {
            mapActions.refresh(true)
        },
        onLocateMe = {
            scope.launch {
                val (lat, lon) = LocationHelper.getInitialLocation(context, settingsManager)
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(lat, lon),
                        12f
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
        showFavoritesOnly = showFavoritesOnly,
        onShowFavoritesOnlyChange = { showFavoritesOnly = it },
        favoritesFilterEnabled = settings.isLoggedIn && favoritesRepo != null,
        isLoading = mapData.isLoading,
        palette = palette
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
                    onIgnore = mapActions.clearError,
                    onRetry = mapActions.retry
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
                                    addPoiInitialLat = cameraPositionState.position.target.latitude
                                    addPoiInitialLng = cameraPositionState.position.target.longitude
                                    addPoiLinkedOfficialId = null
                                    addPoiExistingCommunityId = null
                                    addPoiInitialName = ""
                                    addPoiInitialAddress = ""
                                    showAddPoiSheet = true
                                },
                                label = { Text("+ POI") }
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { mapSizePx = it }
            ) {
                val configuration = LocalConfiguration.current
                val mapPaddingBottom = if (selectedPoi != null) (configuration.screenHeightDp * 0.4f).dp else 0.dp

                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = hasLocationPermission,
                        isTrafficEnabled = settings.mapTrafficEnabled,
                        mapStyleOptions = run {
                            val dark = when (settings.uiThemeMode) {
                                ThemeMode.Dark -> true
                                ThemeMode.Light -> false
                                ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
                            }
                            if (dark) MapStyleOptions.loadRawResourceStyle(context, R.raw.google_map_style_dark)
                            else null
                        }
                    ),
                    uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission),
                    contentPadding = PaddingValues(bottom = mapPaddingBottom)
                ) {
                    val zoom = cameraPositionState.position.zoom
                    val sizePx = remember(zoom) { markerSizePxForZoom(zoom) }

                    val effectiveEnergies = settings.effectiveMapEnergyFilterIds()
                    val effectivePowerLevels = settings.effectiveIrvePowerLevels()

                    val poisToShow = if (frozenPoisForSheet.isNotEmpty()) {
                        frozenPoisForSheet
                    } else if (showFavoritesOnly && favoriteIds.isNotEmpty()) {
                        poisInView.filter { it.id in favoriteIds }
                    } else {
                        poisInView
                    }

                    val fuelIdsForCheapest = effectiveEnergies - "electric"
                    val minPrice = remember(poisToShow, fuelIdsForCheapest) {
                        if (fuelIdsForCheapest.isEmpty()) null
                        else {
                            poisToShow.mapNotNull { poi ->
                                poi.fuelPrices?.filter { !it.outOfStock && MapPoiFilter.fuelNameToId(it.fuelName) in fuelIdsForCheapest }
                                    ?.minByOrNull { it.price }?.price
                            }.minOrNull()
                        }
                    }

                    poisToShow.forEach { poi ->
                        val availability = mapData.availabilityByPoiId[poi.id]
                        val isPoiSelected = selectedPoi?.id == poi.id
                        val isCheapest = remember(poi, minPrice, fuelIdsForCheapest) {
                            if (minPrice == null) false
                            else {
                                poi.fuelPrices?.any { !it.outOfStock && MapPoiFilter.fuelNameToId(it.fuelName) in fuelIdsForCheapest && it.price == minPrice } == true
                            }
                        }
                        val markerBitmap = remember(poi, effectiveEnergies, effectivePowerLevels, isPoiSelected, isCheapest, sizePx, availability) {
                            BitmapDescriptorFactory.fromBitmap(
                                PoiMarkerHelper.getMarkerBitmap(
                                    context = context,
                                    poi = poi,
                                    effectiveEnergyTypes = effectiveEnergies,
                                    effectivePowerLevels = effectivePowerLevels,
                                    isSelected = isPoiSelected,
                                    isCheapest = isCheapest,
                                    sizePx = sizePx,
                                    availability = availability,
                                    markerStyle = MarkerStyle.Bubble
                                )
                            )
                        }

                        Marker(
                            state = MarkerState(position = LatLng(poi.latitude, poi.longitude)),
                            title = poi.name,
                            snippet = poi.address,
                            icon = markerBitmap,
                            anchor = Offset(0.5f, 1f),
                            onClick = {
                                selectedPoi = poi
                                true
                            }
                        )
                    }
                    mapData.trafficInfo?.events?.forEach { event ->
                        val bbox = event.bbox ?: return@forEach
                        val lat = (bbox.latMin + bbox.latMax) / 2
                        val lon = (bbox.lonMin + bbox.lonMax) / 2
                        val hue = when (event.severity) {
                            TrafficSeverity.Normal -> 120f
                            TrafficSeverity.Congestion -> 30f
                            TrafficSeverity.Closure, TrafficSeverity.Accident, TrafficSeverity.Roadworks -> 0f
                            TrafficSeverity.Unknown -> 60f
                        }
                        Marker(
                            state = MarkerState(position = LatLng(lat, lon)),
                            title = "${event.roadRef}${event.direction?.let { " ($it)" } ?: ""}",
                            snippet = event.message,
                            icon = BitmapDescriptorFactory.defaultMarker(hue),
                            onClick = { true }
                        )
                    }
                }

                if (settings.debugLoggingEnabled) {
                    DebugLogOverlay(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 80.dp) // Below the top bar
                            .zIndex(2f)
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
                cameraPositionState.animate(
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

@Preview(showBackground = true, backgroundColor = 0xFF0F172A)
@Composable
private fun MapScreenPreview() {
    val context = LocalContext.current
    val fakeDiagnostics = remember { DiagnosticStore() }
    val fakeSettingsManager = remember {
        SettingsManager(context).apply {
            setPoiProviderTypes(setOf(PoiProviderType.Routex))
        }
    }
    val fakePoiProvider = object : PoiProvider {
        override suspend fun getGasStations(
            latitude: Double,
            longitude: Double,
            viewport: MapViewport?
        ): List<Poi> = emptyList()
    }

    MapScreen(
        poiProvider = fakePoiProvider,
        availabilityProviderFactory = null,
        settingsManager = fakeSettingsManager,
        authManager = fr.geoking.gaston.feature.auth.GoogleAuthManager(
            context,
            fakeSettingsManager,
            fakeDiagnostics,
            com.google.firebase.auth.FirebaseAuth.getInstance()
        ),
        diagnostics = fakeDiagnostics,
        palette = AnimationPalettes.paletteFor(0),
        onBack = {}
    )
}
