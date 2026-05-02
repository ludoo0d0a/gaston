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
import com.google.maps.android.compose.*
import fr.geoking.gaston.CacheManager
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
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
import fr.geoking.gaston.ui.ColorHelper
import fr.geoking.gaston.ui.map.AddPoiSheet
import fr.geoking.gaston.ui.map.PoiDetailCard
import fr.geoking.gaston.ui.map.PoiDetailsFullscreenDialog
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import fr.geoking.gaston.ui.map.MarkerStyle
import fr.geoking.gaston.ui.map.DebugLogOverlay
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
import kotlinx.coroutines.launch
import fr.geoking.gaston.api.routex.radiusKmFromMapViewport
import fr.geoking.gaston.ui.anim.AnimationPalette
import fr.geoking.gaston.ui.anim.AnimationPalettes

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
    initialSelectedPoi: Poi? = null
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val settings by settingsManager.settings.collectAsState()
    val errorLog by diagnostics.errorLog.collectAsState()
    var cachedPois by remember { mutableStateOf<List<Poi>>(initialSelectedPoi?.let { listOf(it) } ?: emptyList()) }
    var trafficInfo by remember { mutableStateOf<TrafficInfo?>(null) }
    var mapErrorMessage by remember(settings.selectedPoiProviders, settings.poiProviderSelectionMode) {
        mutableStateOf<String?>(null)
    }
    var isErrorPaused by remember(settings.selectedPoiProviders, settings.poiProviderSelectionMode) {
        mutableStateOf(false)
    }
    var retryCount by remember { mutableStateOf(0) }
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
    var isLoading by remember { mutableStateOf(false) }
    var frozenPoisForSheet by remember { mutableStateOf<List<Poi>>(emptyList()) }

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

    val defaultLat = initialSelectedPoi?.latitude ?: 48.8566
    val defaultLng = initialSelectedPoi?.longitude ?: 2.3522
    val defaultZoom = if (initialSelectedPoi != null) 15f else 12f

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(defaultLat, defaultLng), defaultZoom)
    }

    val cameraTarget = cameraPositionState.position.target
    val effectiveProviders = remember(settings, cameraTarget.latitude, cameraTarget.longitude) {
        settings.effectiveProvidersAt(cameraTarget.latitude, cameraTarget.longitude)
    }

    var didInitialCenter by remember { mutableStateOf(false) }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission && !didInitialCenter && initialSelectedPoi == null) {
            val location = LocationHelper.getCurrentLocation(context)
            if (location != null) {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    12f
                )
                didInitialCenter = true
            }
        }
    }

    var mapSizePx by remember { mutableStateOf(IntSize.Zero) }
    var selectedPoi by remember { mutableStateOf<Poi?>(initialSelectedPoi) }
    var scrollRequestPoiId by remember { mutableStateOf(initialSelectedPoi?.id) }
    var poiForDetailsDialog by remember { mutableStateOf<Poi?>(null) }
    var availabilityByPoiId by remember { mutableStateOf<Map<String, StationAvailabilitySummary>>(emptyMap()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialSelectedPoi) {
        if (initialSelectedPoi != null) {
            sheetState.show()
        }
    }

    LaunchedEffect(favoritesRepo) {
        if (favoritesRepo != null) {
            favoriteIds = favoritesRepo.getFavorites().map { it.id }.toSet()
        }
    }

    val poisInView = remember(cachedPois, cameraPositionState.position.target, cameraPositionState.position.zoom, mapSizePx, settings, effectiveProviders) {
        StationMapFilters.apply(
            settings = settings,
            pois = cachedPois,
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

    LaunchedEffect(mapSizePx, retryCount) {
        if (mapSizePx.width <= 0 || mapSizePx.height <= 0) return@LaunchedEffect

        if (!hasLocationPermission) {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        snapshotFlow { cameraPositionState.position }
            .debounce(350)
            .collectLatest { position ->
                if (isErrorPaused || selectedPoi != null) return@collectLatest

                val centerLat = position.target.latitude
                val centerLng = position.target.longitude
                val zoom = position.zoom

                val requiredRadiusKm = radiusKmFromMapViewport(
                    centerLat,
                    centerLng,
                    zoom,
                    mapSizePx.width,
                    mapSizePx.height
                ).coerceIn(1, 50)

                mapErrorMessage = null

                val viewport = MapViewport(
                    zoom = zoom,
                    mapWidthPx = mapSizePx.width,
                    mapHeightPx = mapSizePx.height
                )

                try {
                    isLoading = true
                    poiProvider.searchFlow(
                        PoiSearchRequest(
                            latitude = centerLat,
                            longitude = centerLng,
                            viewport = viewport,
                            categories = emptySet(),
                            skipFilters = true
                        )
                    ).collect { result ->
                        if (result.errors.isEmpty() || result.pois.isNotEmpty()) {
                            cachedPois = PoiMerger.mergeInto(cachedPois, result.pois)

                            // Availability: refresh for POIs close enough for the cards.
                            val availabilityProvider = availabilityProviderFactory?.getProvider(centerLat, centerLng)
                            if (availabilityProvider != null) {
                                val availabilityRadiusKm = requiredRadiusKm.coerceAtMost(20).coerceAtLeast(10)
                                val availabilities = availabilityProvider.getAvailability(centerLat, centerLng, availabilityRadiusKm)
                                val poisForAvailability = cachedPois.filter { poi ->
                                    approxDistanceKm(centerLat, centerLng, poi.latitude, poi.longitude) <= availabilityRadiusKm * 1.05
                                }
                                val matched = matchAvailabilityToPois(availabilities, poisForAvailability)
                                availabilityByPoiId = availabilityByPoiId + matched
                            }
                        }

                        if (result.errors.isNotEmpty() && result.pois.isEmpty()) {
                            val firstError = result.errors.first()
                            val msg = firstError.message
                            val code = firstError.httpCode

                            mapErrorMessage = msg
                            isErrorPaused = true
                            diagnostics.recordError(code, "Map ($effectiveProviders): $msg")
                        }
                    }

                    // Traffic: updated only when a POI fetch happens.
                    val availabilityProvider = availabilityProviderFactory?.getProvider(centerLat, centerLng)
                    if (availabilityProvider != null) {
                        val availabilityRadiusKm = requiredRadiusKm.coerceAtMost(20).coerceAtLeast(10)
                        val availabilities = availabilityProvider.getAvailability(centerLat, centerLng, availabilityRadiusKm)
                        val poisForAvailability = cachedPois.filter { poi ->
                            approxDistanceKm(centerLat, centerLng, poi.latitude, poi.longitude) <= availabilityRadiusKm * 1.05
                        }
                        val matched = matchAvailabilityToPois(availabilities, poisForAvailability)
                        availabilityByPoiId = availabilityByPoiId + matched
                    }

                    val trafficProvider = trafficProviderFactory?.getProvider(centerLat, centerLng)
                    if (trafficProvider != null) {
                        val halfSpan = 0.15
                        trafficInfo = trafficProvider.getTraffic(
                            TrafficRequest.Bbox(
                                centerLat - halfSpan,
                                centerLng - halfSpan,
                                centerLat + halfSpan,
                                centerLng + halfSpan
                            )
                        )
                    } else {
                        trafficInfo = null
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    val msg = e.message?.takeIf { it.isNotBlank() } ?: e.toString()
                    mapErrorMessage = msg
                    isErrorPaused = true
                    diagnostics.recordError(
                        (e as? NetworkException)?.httpCode,
                        "Map ($effectiveProviders): $msg"
                    )
                } finally {
                    isLoading = false
                }
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
            scope.launch {
                CacheManager.clearAllCaches(context)
                cachedPois = emptyList()
                availabilityByPoiId = emptyMap()
                trafficInfo = null
                mapErrorMessage = null
                isErrorPaused = false
                retryCount++
            }
        },
        onLocateMe = {
            scope.launch {
                val location = LocationHelper.getCurrentLocation(context)
                if (location != null) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(location.latitude, location.longitude),
                            12f
                        )
                    )
                }
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
        isLoading = isLoading,
        palette = palette
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            mapErrorMessage?.let { msg ->
                val configuration = LocalConfiguration.current
                val maxHeight = configuration.screenHeightDp.dp * 0.15f
                val clipboard = LocalClipboard.current

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = msg,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("error", msg)))
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Copy", fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = {
                                    mapErrorMessage = null
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Ignore", fontSize = 12.sp)
                            }
                            Button(
                                onClick = {
                                    mapErrorMessage = null
                                    isErrorPaused = false
                                    retryCount++
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Retry", fontSize = 12.sp)
                            }
                        }
                    }
                }
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
                        isTrafficEnabled = settings.mapTrafficEnabled
                    ),
                    uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission),
                    contentPadding = PaddingValues(bottom = mapPaddingBottom)
                ) {
                    val mapContext = LocalContext.current
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
                        val availability = availabilityByPoiId[poi.id]
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
                                    context = mapContext,
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
                                scrollRequestPoiId = poi.id
                                scope.launch { sheetState.show() }
                                true
                            }
                        )
                    }
                    trafficInfo?.events?.forEach { event ->
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

    // Keep the map camera centered on the POI currently selected in the bottom sheet.
    // We intentionally wait for programmatic sheet scrolling to finish (see `scrollRequestPoiId`),
    // otherwise the map animation could fight with the sheet repositioning.
    LaunchedEffect(selectedPoi?.id, scrollRequestPoiId) {
        val poi = selectedPoi ?: return@LaunchedEffect
        if (scrollRequestPoiId != null) return@LaunchedEffect
        // Animation accounts for the contentPadding set in GoogleMap when selectedPoi != null
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLng(
                LatLng(poi.latitude, poi.longitude)
            )
        )
    }

    if (selectedPoi != null) {
        val listToShow = frozenPoisForSheet.takeIf { it.isNotEmpty() } ?: listOf(selectedPoi!!)
        val currentListToShow by rememberUpdatedState(listToShow)

        // If the user tapped a map marker, scroll the bottom sheet so the corresponding card is shown.
        LaunchedEffect(scrollRequestPoiId) {
            val requestId = scrollRequestPoiId ?: return@LaunchedEffect
            val index = currentListToShow.indexOfFirst { it.id == requestId }
            if (index >= 0) {
                lazyListState.scrollToItem(index)
            }
            scrollRequestPoiId = null
        }

        // When the LazyRow is snapped, consider the card closest to the center as the "selected" card.
        // Selecting a new card drives the camera centering via the `LaunchedEffect(selectedPoi?.id, ...)` above.
        val currentScrollRequestPoiId by rememberUpdatedState(scrollRequestPoiId)
        LaunchedEffect(lazyListState) {
            snapshotFlow { lazyListState.isScrollInProgress }.collect { inProgress ->
                if (inProgress) return@collect
                if (currentScrollRequestPoiId != null) return@collect

                val viewportWidth = lazyListState.layoutInfo.viewportSize.width
                if (viewportWidth <= 0) return@collect

                val viewportCenter = viewportWidth / 2
                val closestItem = lazyListState.layoutInfo.visibleItemsInfo.minByOrNull { item ->
                    val itemCenter = item.offset + item.size / 2
                    kotlin.math.abs(itemCenter - viewportCenter)
                }

                val centeredPoi = closestItem?.index?.let { idx -> currentListToShow.getOrNull(idx) } ?: return@collect
                if (selectedPoi?.id != centeredPoi.id) {
                    selectedPoi = centeredPoi
                }
            }
        }

        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { sheetState.hide() }
                selectedPoi = null
                scrollRequestPoiId = null
            },
            sheetState = sheetState,
            sheetGesturesEnabled = true,
            containerColor = Color(0xFF1E293B),
            dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.7f)) }
        ) {
            LazyRow(
                state = lazyListState,
                flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(listToShow, key = { it.id }) { poi ->
                    val isFav = poi.id in favoriteIds
                    PoiDetailCard(
                        modifier = Modifier.width(LocalConfiguration.current.screenWidthDp.dp),
                        poi = poi,
                        availabilitySummary = availabilityByPoiId[poi.id],
                        highlightedFuelIds = settings.effectiveMapEnergyFilterIds(),
                        highlightedPowerLevels = settings.effectiveIrvePowerLevels(),
                        onNavigate = {
                            val uri = Uri.parse("geo:${poi.latitude},${poi.longitude}?q=${Uri.encode(poi.name)}")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        },
                        onLocate = {
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLng(
                                        LatLng(poi.latitude, poi.longitude)
                                    )
                                )
                            }
                        },
                        onShowDetails = { poiForDetailsDialog = poi },
                        isSelected = poi.id == selectedPoi?.id,
                        isLoggedIn = settings.isLoggedIn,
                        isFavorite = isFav,
                        onToggleFavorite = if (settings.isLoggedIn && favoritesRepo != null) {
                            {
                                scope.launch {
                                    favoritesRepo.toggleFavorite(poi)
                                    favoriteIds = favoritesRepo.getFavorites().map { it.id }.toSet()
                                }
                            }
                        } else null
                    )
                }
            }
        }
    }

    if (showAddPoiSheet) {
        AddPoiSheet(
            initialLat = addPoiInitialLat,
            initialLng = addPoiInitialLng,
            linkedOfficialId = addPoiLinkedOfficialId,
            existingCommunityId = addPoiExistingCommunityId,
            initialName = addPoiInitialName,
            initialAddress = addPoiInitialAddress,
            communityRepo = communityRepo,
            onDismiss = { showAddPoiSheet = false },
            onSaved = { retryCount++ }
        )
    }

    poiForDetailsDialog?.let { poi ->
        val ratingState = remember(poi.id) { mutableStateOf(settingsManager.getPoiRating(poi.id)) }
        PoiDetailsFullscreenDialog(
            poi = poi,
            availabilitySummary = availabilityByPoiId[poi.id],
            highlightedFuelIds = settings.effectiveMapEnergyFilterIds(),
            highlightedPowerLevels = settings.effectiveIrvePowerLevels(),
            rating = ratingState.value,
            onRate = { r ->
                settingsManager.setPoiRating(poi.id, r)
                ratingState.value = r
            },
            isLoggedIn = settings.isLoggedIn,
            isCommunityPoi = isCommunityPoiId(poi.id),
            onEdit = if (settings.isLoggedIn && isCommunityPoiId(poi.id) && communityRepo != null) {
                {
                    addPoiExistingCommunityId = poi.id
                    addPoiInitialName = poi.name
                    addPoiInitialAddress = poi.address
                    addPoiInitialLat = poi.latitude
                    addPoiInitialLng = poi.longitude
                    addPoiLinkedOfficialId = null
                    showAddPoiSheet = true
                    poiForDetailsDialog = null
                    selectedPoi = null
                    scope.launch { sheetState.hide() }
                }
            } else null,
            onRemove = if (settings.isLoggedIn && isCommunityPoiId(poi.id) && communityRepo != null) {
                {
                    scope.launch {
                        communityRepo.removeCommunityPoi(poi.id)
                        retryCount++
                        poiForDetailsDialog = null
                        selectedPoi = null
                        sheetState.hide()
                    }
                }
            } else null,
            onHide = if (settings.isLoggedIn && !isCommunityPoiId(poi.id) && communityRepo != null) {
                {
                    scope.launch {
                        communityRepo.hideOfficialPoi(poi.id)
                        retryCount++
                        poiForDetailsDialog = null
                        selectedPoi = null
                        sheetState.hide()
                    }
                }
            } else null,
            onSuggestCorrection = if (settings.isLoggedIn && !isCommunityPoiId(poi.id) && communityRepo != null) {
                {
                    addPoiLinkedOfficialId = poi.id
                    addPoiExistingCommunityId = null
                    addPoiInitialName = poi.name
                    addPoiInitialAddress = poi.address
                    addPoiInitialLat = poi.latitude
                    addPoiInitialLng = poi.longitude
                    showAddPoiSheet = true
                    poiForDetailsDialog = null
                    selectedPoi = null
                    scope.launch { sheetState.hide() }
                }
            } else null,
            onDismiss = { poiForDetailsDialog = null }
        )
    }
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
