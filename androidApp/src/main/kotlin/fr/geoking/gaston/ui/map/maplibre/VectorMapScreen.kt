@file:Suppress("DEPRECATION")

package fr.geoking.gaston.ui.map.maplibre

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.poi.*
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.shared.diagnostics.DiagnosticStore
import fr.geoking.gaston.ui.anim.AnimationPalette
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.api.belib.matchAvailabilityToPois
import fr.geoking.gaston.api.traffic.TrafficInfo
import fr.geoking.gaston.api.traffic.TrafficProviderFactory
import fr.geoking.gaston.api.traffic.TrafficRequest
import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.feature.auth.GoogleAuthManager
import fr.geoking.gaston.ui.ColorHelper
import fr.geoking.gaston.ui.MAP_ENERGY_OPTIONS
import fr.geoking.gaston.ui.MAP_IRVE_POWER_OPTIONS
import fr.geoking.gaston.ui.components.MapLoader
import fr.geoking.gaston.StationMapFilters
import fr.geoking.gaston.effectiveProvidersAt
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.api.routex.radiusKmFromMapViewport
import fr.geoking.gaston.community.isCommunityPoiId
import fr.geoking.gaston.ui.SettingsScreen
import fr.geoking.gaston.ui.SettingsScreenPage
import fr.geoking.gaston.ui.components.MapScaffold
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import fr.geoking.gaston.ui.map.MarkerStyle
import fr.geoking.gaston.ui.map.PoiDetailCard
import fr.geoking.gaston.ui.map.PoiDetailsFullscreenDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val rad = kotlin.math.PI / 180.0
    val dLat = (lat2 - lat1) * rad
    val dLon = (lon2 - lon1) * rad
    val sinDLat = kotlin.math.sin(dLat / 2)
    val sinDLon = kotlin.math.sin(dLon / 2)
    val a = sinDLat * sinDLat +
        kotlin.math.cos(lat1 * rad) * kotlin.math.cos(lat2 * rad) *
        sinDLon * sinDLon
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return r * c
}

private fun approxDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLatKm = (lat2 - lat1) * 111.0
    val avgLatRad = ((lat1 + lat2) / 2.0) * kotlin.math.PI / 180.0
    val dLonKm = (lon2 - lon1) * 111.0 * kotlin.math.cos(avgLatRad)
    return kotlin.math.sqrt(dLatKm * dLatKm + dLonKm * dLonKm)
}

private data class LoadedPoiRegion(
    val centerLat: Double,
    val centerLng: Double,
    val maxRadiusKmLoaded: Int,
    val loadedAtMs: Long
)

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun VectorMapScreen(
    poiProvider: PoiProvider,
    availabilityProviderFactory: BorneAvailabilityProviderFactory?,
    trafficProviderFactory: TrafficProviderFactory? = null,
    settingsManager: SettingsManager,
    authManager: GoogleAuthManager?,
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
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var cachedPois by remember { mutableStateOf<List<Poi>>(initialSelectedPoi?.let { listOf(it) } ?: emptyList()) }
    var trafficInfo by remember { mutableStateOf<TrafficInfo?>(null) }
    var availabilityByPoiId by remember { mutableStateOf<Map<String, StationAvailabilitySummary>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }
    var mapErrorMessage by remember(settings.selectedPoiProviders, settings.poiProviderSelectionMode) {
        mutableStateOf<String?>(null)
    }
    var isErrorPaused by remember(settings.selectedPoiProviders, settings.poiProviderSelectionMode) {
        mutableStateOf(false)
    }
    var retryCount by remember { mutableStateOf(0) }
    var mapSizePx by remember { mutableStateOf(IntSize.Zero) }
    var selectedPoi by remember { mutableStateOf<Poi?>(initialSelectedPoi) }
    var showMapSettings by remember { mutableStateOf(false) }
    var initialSettingsPage by remember { mutableStateOf(SettingsScreenPage.MapConfig) }
    var showFavoritesOnly by remember { mutableStateOf(false) }
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var frozenPoisForSheet by remember { mutableStateOf<List<Poi>>(emptyList()) }
    var scrollRequestPoiId by remember { mutableStateOf(initialSelectedPoi?.id) }
    var poiForDetailsDialog by remember { mutableStateOf<Poi?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val lazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialSelectedPoi) {
        if (initialSelectedPoi != null) {
            sheetState.show()
        }
    }

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

    val initialCameraPosition = remember {
        CameraPosition.Builder()
            .target(LatLng(initialSelectedPoi?.latitude ?: initialCenter?.latitude ?: 48.8566, initialSelectedPoi?.longitude ?: initialCenter?.longitude ?: 2.3522))
            .zoom(if (initialSelectedPoi != null || initialCenter != null) 15.0 else 12.0)
            .build()
    }

    val providerResolveLat = mapLibreMap?.cameraPosition?.target?.latitude
        ?: initialCameraPosition.target?.latitude
        ?: 48.8566
    val providerResolveLon = mapLibreMap?.cameraPosition?.target?.longitude
        ?: initialCameraPosition.target?.longitude
        ?: 2.3522
    val effectiveProviders = remember(settings, providerResolveLat, providerResolveLon) {
        settings.effectiveProvidersAt(providerResolveLat, providerResolveLon)
    }

    LaunchedEffect(favoritesRepo) {
        if (favoritesRepo != null) {
            favoriteIds = favoritesRepo.getFavorites().map { it.id }.toSet()
        }
    }

    val poiFetchKey = remember(
        effectiveProviders,
        settings.useVehicleFilter,
        settings.fuelCard,
        settings.vehicleType,
        settings.vehicleEnergy,
        settings.selectedOverpassAmenityTypes
    ) {
        buildString {
            append(effectiveProviders.sortedBy { it.name }.joinToString(",") { it.name })
            append("|vehicleFilter=").append(settings.useVehicleFilter)
            append("|fuelCard=").append(settings.fuelCard)
            append("|vehicleType=").append(settings.vehicleType)
            append("|vehicleEnergy=").append(settings.vehicleEnergy)
            append("|overpassAmenities=").append(settings.selectedOverpassAmenityTypes.sorted().joinToString(","))
        }
    }

    val loadedRegions = remember { mutableListOf<LoadedPoiRegion>() }
    val poiSeenAtMs = remember { mutableStateMapOf<String, Long>() }
    var lastCacheKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(poiFetchKey, mapSizePx, retryCount, mapLibreMap) {
        val map = mapLibreMap ?: return@LaunchedEffect
        val currentCacheKey = "|size=${mapSizePx.width}x${mapSizePx.height}"
        if (lastCacheKey != currentCacheKey) {
            loadedRegions.clear()
            cachedPois = emptyList()
            poiSeenAtMs.clear()
            availabilityByPoiId = emptyMap()
            trafficInfo = null
            mapErrorMessage = null
            isErrorPaused = false
            lastCacheKey = currentCacheKey
        }

        if (mapSizePx.width <= 0 || mapSizePx.height <= 0) return@LaunchedEffect

        snapshotFlow { map.cameraPosition }.debounce(350).collectLatest { position ->
                if (isErrorPaused || selectedPoi != null) return@collectLatest

                val target = position.target ?: return@collectLatest
                val centerLat = target.latitude
                val centerLng = target.longitude
                val zoom = position.zoom.toFloat()
                val nowMs = System.currentTimeMillis()
                val ttlMs = 8L * 60L * 60L * 1000L
                val expiresBeforeMs = nowMs - ttlMs

                val requiredRadiusKm = radiusKmFromMapViewport(
                    centerLat,
                    centerLng,
                    zoom,
                    mapSizePx.width,
                    mapSizePx.height
                ).coerceIn(1, 50)

                loadedRegions.removeAll { it.loadedAtMs < expiresBeforeMs }

                val viewportCovered = loadedRegions.any { region ->
                    region.maxRadiusKmLoaded >= requiredRadiusKm &&
                        haversineKm(
                            centerLat,
                            centerLng,
                            region.centerLat,
                            region.centerLng
                        ) <= (region.maxRadiusKmLoaded - requiredRadiusKm).toDouble() + 0.5
                }

                if (viewportCovered) return@collectLatest

                try {
                    isLoading = true
                    val viewport = MapViewport(zoom, mapSizePx.width, mapSizePx.height)
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
                            val finalPois = result.pois
                            cachedPois = PoiMerger.mergeInto(cachedPois, finalPois)
                            val now = System.currentTimeMillis()
                            finalPois.forEach { poiSeenAtMs[it.id] = now }

                            // Only add to loadedRegions if we have at least one provider that responded successfully
                            // or if it's the final emission (which searchFlow handles).
                            // In this case, we add it to mark this area as covered.
                            loadedRegions.add(LoadedPoiRegion(centerLat, centerLng, requiredRadiusKm, now))

                            // Availability refresh
                            val availabilityProvider = availabilityProviderFactory?.getProvider(centerLat, centerLng)
                            if (availabilityProvider != null) {
                                val availabilityRadiusKm = requiredRadiusKm.coerceAtMost(20).coerceAtLeast(10)
                                val availabilities = availabilityProvider.getAvailability(centerLat, centerLng, availabilityRadiusKm)
                                val matched = matchAvailabilityToPois(availabilities, finalPois)
                                availabilityByPoiId = availabilityByPoiId + matched
                            }
                        }

                        if (result.errors.isNotEmpty() && result.pois.isEmpty()) {
                            mapErrorMessage = result.errors.first().message
                            isErrorPaused = true
                        }
                    }
                } catch (e: Exception) {
                    mapErrorMessage = e.message
                    isErrorPaused = true
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

    val poisInView = remember(cachedPois, mapLibreMap?.cameraPosition, mapSizePx, settings, effectiveProviders) {
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

    MapScaffold(
        title = "Vector Map",
        settingsManager = settingsManager,
        mapCenterLatitude = mapLibreMap?.cameraPosition?.target?.latitude,
        mapCenterLongitude = mapLibreMap?.cameraPosition?.target?.longitude,
        onBack = onBack,
        onRefresh = {
            retryCount++
        },
        onLocateMe = {
            if (hasLocationPermission) {
                scope.launch {
                    val loc = LocationHelper.getCurrentLocation(context)
                    if (loc != null) {
                        mapLibreMap?.animateCamera(
                            CameraUpdateFactory.newLatLng(
                                LatLng(loc.latitude, loc.longitude)
                            )
                        )
                    }
                }
            } else {
                launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onSizeChanged { mapSizePx = it }
        ) {
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

            MapLibreView(
                modifier = Modifier.fillMaxSize(),
                styleUrl = settings.mapTheme.styleUrl,
                cameraPosition = initialCameraPosition,
                onMapReady = { map ->
                    mapLibreMap = map
                    map.getStyle { style ->
                        if (hasLocationPermission) {
                            val options = LocationComponentActivationOptions.builder(context, style).build()
                            map.locationComponent.activateLocationComponent(options)
                            map.locationComponent.isLocationComponentEnabled = true
                        }

                        // Setup POI Source and Layer
                        if (style.getSource("poi-source") == null) {
                            style.addSource(GeoJsonSource("poi-source"))
                        }
                        if (style.getLayer("poi-layer") == null) {
                            style.addLayer(
                                SymbolLayer("poi-layer", "poi-source").withProperties(
                                    PropertyFactory.iconImage("{poi-id}"),
                                    PropertyFactory.iconAllowOverlap(true),
                                    PropertyFactory.iconIgnorePlacement(true)
                                )
                            )
                        }
                    }
                    map.addOnMapClickListener { point ->
                        map.getStyle { style ->
                            val pixel = map.projection.toScreenLocation(point)
                            val features = map.queryRenderedFeatures(pixel, "poi-layer")
                            if (features.isNotEmpty()) {
                                val imageId = features[0].getStringProperty("poi-id")
                                val poiId = imageId.substringBefore("_")
                                val poi = poisToShow.find { it.id == poiId }
                                if (poi != null) {
                                    selectedPoi = poi
                                    scrollRequestPoiId = poi.id
                                    scope.launch { sheetState.show() }
                                }
                            }
                        }
                        true
                    }
                },
                update = { map ->
                    map.getStyle { style ->
                        val features = poisToShow.map { poi ->
                            val isSelected = selectedPoi?.id == poi.id
                            val isCheapest = minPrice != null && poi.fuelPrices?.any { !it.outOfStock && MapPoiFilter.fuelNameToId(it.fuelName) in fuelIdsForCheapest && it.price == minPrice } == true

                            val imageId = "${poi.id}_${isSelected}_${isCheapest}_${availabilityByPoiId[poi.id]?.hashCode()}"

                            if (style.getImage(imageId) == null) {
                                val markerBitmap = PoiMarkerHelper.getMarkerBitmap(
                                    context = context,
                                    poi = poi,
                                    effectiveEnergyTypes = effectiveEnergies,
                                    effectivePowerLevels = effectivePowerLevels,
                                    isSelected = isSelected,
                                    isCheapest = isCheapest,
                                    sizePx = 120,
                                    availability = availabilityByPoiId[poi.id],
                                    markerStyle = MarkerStyle.Bubble
                                )
                                style.addImage(imageId, markerBitmap)
                            }

                            Feature.fromGeometry(
                                Point.fromLngLat(poi.longitude, poi.latitude)
                            ).apply {
                                addStringProperty("poi-id", imageId)
                            }
                        }
                        style.getSourceAs<GeoJsonSource>("poi-source")?.setGeoJson(FeatureCollection.fromFeatures(features))
                    }
                }
            )

            if (isLoading) {
                MapLoader(
                    palette = palette,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(1f)
                )
            }
        }
    }

    LaunchedEffect(selectedPoi?.id, scrollRequestPoiId) {
        val poi = selectedPoi ?: return@LaunchedEffect
        if (scrollRequestPoiId != null) return@LaunchedEffect
        mapLibreMap?.animateCamera(
            CameraUpdateFactory.newLatLng(
                LatLng(poi.latitude, poi.longitude)
            )
        )
    }

    if (selectedPoi != null) {
        val listToShow = frozenPoisForSheet.takeIf { it.isNotEmpty() } ?: listOf(selectedPoi!!)
        val currentListToShow by rememberUpdatedState(listToShow)

        LaunchedEffect(scrollRequestPoiId) {
            val requestId = scrollRequestPoiId ?: return@LaunchedEffect
            val index = currentListToShow.indexOfFirst { it.id == requestId }
            if (index >= 0) {
                lazyListState.scrollToItem(index)
            }
            scrollRequestPoiId = null
        }

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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp, start = 16.dp, end = 16.dp)
            ) {
                items(listToShow, key = { it.id }) { poi ->
                    val isFav = poi.id in favoriteIds
                    PoiDetailCard(
                        modifier = Modifier.width((LocalConfiguration.current.screenWidthDp - 32).dp),
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
                                mapLibreMap?.animateCamera(
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
                    poiForDetailsDialog = null
                    selectedPoi = null
                    scope.launch { sheetState.hide() }
                }
            } else null,
            onDismiss = { poiForDetailsDialog = null }
        )
    }
}
