package fr.geoking.gaston.auto

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import android.graphics.Rect
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.AppManager
import androidx.car.app.CarToast
import androidx.car.app.constraints.ConstraintManager
import com.google.android.gms.location.Priority
import androidx.lifecycle.DefaultLifecycleObserver
import fr.geoking.gaston.poi.PoiProviderType
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.FuelCard
import fr.geoking.gaston.MapTheme
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.StationMapFilters
import fr.geoking.gaston.VehicleType
import fr.geoking.gaston.filterPoisByViewport
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiMerger
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.poi.PoiSearchResult
import fr.geoking.gaston.poi.PoiProviderError
import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.LoadedPoiRegion
import fr.geoking.gaston.poi.mergeLoadedRegion
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.api.geocoding.GeocodingClient
import fr.geoking.gaston.api.routing.RoutePlanner
import fr.geoking.gaston.api.routing.RoutingClient
import fr.geoking.gaston.api.traffic.TrafficProviderFactory
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.effectiveProvidersAt
import fr.geoking.gaston.feature.location.LocationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import fr.geoking.gaston.toll.TollCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import fr.geoking.gaston.api.belib.matchAvailabilityToPois
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

import fr.geoking.gaston.auto.maplibre.CarMapLibreRenderer
import fr.geoking.gaston.auto.maplibre.resolveAutoMapStyleUrl

/**
 * POI map with MapLibre OpenFreeMap vector styles via [CarMapLibreRenderer]
 * (MapSnapshotter → AA surface, optional EGL). Enable Settings → Map tile debug for HUD;
 * logcat: `CarMapLibreRenderer` / `CarEglSurfaceRenderer`.
 */
class MapLibrePoiScreen(
    carContext: CarContext,
    private val poiProvider: PoiProvider,
    private val availabilityProviderFactory: BorneAvailabilityProviderFactory,
    private val settingsManager: SettingsManager,
    private val routePlanner: RoutePlanner? = null,
    private val routingClient: RoutingClient? = null,
    private val tollCalculator: TollCalculator? = null,
    private val trafficProviderFactory: TrafficProviderFactory? = null,
    private val geocodingClient: GeocodingClient? = null,
    private val communityRepo: CommunityPoiRepository? = null,
    private val favoritesRepo: FavoritesRepository? = null,
    private val title: String = carContext.getString(R.string.dashboard_nearby_stations),
    private val itineraryPoints: List<Pair<Double, Double>> = emptyList()
) : Screen(carContext), SurfaceCallback, DefaultLifecycleObserver {

    private var pois: List<Poi> = emptyList()
    private var errors: List<PoiProviderError> = emptyList()
    private var availabilityByPoiId: Map<String, StationAvailabilitySummary> = emptyMap()
    private var isLoading = true
    private var isQueryPending = false
    private var queryGeneration: Int = 0
    private var searchLat: Double = settingsManager.settings.value.lastKnownLat ?: 48.8566
    private var searchLon: Double = settingsManager.settings.value.lastKnownLon ?: 2.3522
    private var zoom: Int = AutoMapCamera.DEFAULT_ZOOM
    private var sortByPrice: Boolean = false
    private var isCheapestFilterActive: Boolean = false
    private var currentVisibleArea: Rect? = null
    private var mapWidthPx: Int = 800
    private var mapHeightPx: Int = 480

    private var mapRenderer: CarMapLibreRenderer? = null
    private var headingUpdateJob: Job? = null
    private var orientationMode: MapOrientationMode = MapOrientationMode.HeadingUp
    private var lastKnownBearingDegrees: Float = 0f
    private var lastMapOrientationUpdateMillis: Long = 0
    private val historyPoints = mutableListOf<Pair<Double, Double>>()

    /** Last resolved search center; combined with settings so auto mode reloads when the vehicle moves across regions. */
    private val searchCenterFlow = MutableStateFlow(searchLat to searchLon)

    private var lastCameraFitWidth: Int = 0
    private var lastCameraFitHeight: Int = 0
    private var hasAppliedVisibleAreaCamera: Boolean = false
    private var lastAppliedSearchLat: Double = searchLat
    private var lastAppliedSearchLon: Double = searchLon
    private var lastAppliedZoom: Int = zoom
    private var lastSyncedPoiIds: List<String> = emptyList()
    private var visibleAreaCameraJob: Job? = null
    /** When set, map markers are filtered to this station only (selection / detail handoff). */
    private var mapSelectedPoi: Poi? = null
    private var lastQueryCoverage: LoadedPoiRegion? = null
    private var lastRedrawPosition: Pair<Double, Double>? = null

    private fun openStationDetail(poi: Poi, availability: StationAvailabilitySummary?) {
        loadPoisJob?.cancel()
        isQueryPending = false
        isLoading = false
        val settings = settingsManager.settings.value
        val energies = settings.effectiveMapEnergyFilterIds()
        val powerLevels = settings.effectiveIrvePowerLevels()
        mapSelectedPoi = poi
        // Center + filter before push so a shared MapWithContent surface keeps only this station.
        lastAppliedSearchLat = poi.latitude
        lastAppliedSearchLon = poi.longitude
        lastAppliedZoom = zoom
        mapRenderer?.updateLocation(poi.latitude, poi.longitude, zoom)
        syncRendererWithMapState()
        screenManager.push(
            MapLibreStationDetailScreen(
                carContext = carContext,
                poi = poi,
                availability = availability,
                searchLat = searchLat,
                searchLon = searchLon,
                zoom = zoom,
                orientationMode = orientationMode,
                bearing = lastKnownBearingDegrees,
                effectiveEnergies = energies,
                effectivePowerLevels = powerLevels,
                settingsManager = settingsManager,
                favoritesRepo = favoritesRepo,
                onDisposed = {
                    mapSelectedPoi = null
                    lastAppliedSearchLat = searchLat
                    lastAppliedSearchLon = searchLon
                    mapRenderer?.updateLocation(searchLat, searchLon, zoom)
                    syncRendererWithMapState()
                    invalidate()
                }
            )
        )
    }

    init {
        lifecycle.addObserver(this)
        mapRenderer = createMapRenderer()
        lifecycleScope.launch {
            combine(settingsManager.settings, searchCenterFlow) { s, (la, lo) ->
                PoiFetchSettings(
                    s.effectiveProvidersAt(la, lo),
                    s.useVehicleFilter,
                    s.fuelCard,
                    s.vehicleType,
                    s.vehicleEnergy,
                    s.selectedOverpassAmenityTypes
                )
            }
                .distinctUntilChanged()
                .collectLatest {
                    loadPois(showLoading = pois.isEmpty())
                }
        }
        lifecycleScope.launch {
            settingsManager.settings
                .map { s ->
                    PoiFilterSettings(
                        s.selectedMapEnergyTypes,
                        s.mapPowerLevels,
                        s.mapIrveOperators,
                        s.mapBrands,
                        s.selectedMapConnectorTypes,
                        s.vehicleGasTypes,
                        s.vehiclePowerLevels,
                        s.mapTheme
                    )
                }
                .distinctUntilChanged()
                .collectLatest {
                    syncRendererWithMapState()
                    invalidate()
                }
        }
    }

    private companion object {
        private const val VISIBLE_AREA_SIZE_DELTA_PX = 8
        private const val VISIBLE_AREA_CAMERA_DEBOUNCE_MS = 150L
    }

    private fun recordQueryCoverage(lat: Double, lon: Double) {
        lastQueryCoverage = mergeLoadedRegion(
            existing = lastQueryCoverage,
            centerLat = lat,
            centerLng = lon,
            requiredRadiusKm = currentSearchRadiusKm().toInt(),
            loadedAtMs = System.currentTimeMillis(),
            fetchedProviders = emptySet(),
            fetchedCategories = emptySet(),
        )
        lastRedrawPosition = lat to lon
    }

    private data class PoiFetchSettings(
        val providers: Set<PoiProviderType>,
        val useVehicleFilter: Boolean,
        val fuelCard: FuelCard,
        val vehicleType: VehicleType,
        val vehicleEnergy: String,
        val amenities: Set<String>
    )

    private data class PoiFilterSettings(
        val energies: Set<String>,
        val powerLevels: Set<Int>,
        val operators: Set<String>,
        val brands: Set<String>,
        val connectors: Set<String>,
        val vehicleGasTypes: Set<String>,
        val vehiclePowerLevels: Set<Int>,
        val mapTheme: MapTheme
    )

    private fun getFilteredPois(currentSettings: AppSettings): List<Poi> {
        val effectiveProviders = currentSettings.effectiveProvidersAt(searchLat, searchLon)
        val basePois = StationMapFilters.apply(
            settings = currentSettings,
            pois = pois,
            providers = effectiveProviders,
            skipWhenOnlyOverpass = true
        )

        val visiblePois = filterPoisByViewport(
            pois = basePois,
            lat = searchLat,
            lon = searchLon,
            zoom = zoom.toFloat(),
            widthPx = mapWidthPx,
            heightPx = mapHeightPx
        )

        return if (isCheapestFilterActive) {
            val fuelIds = currentSettings.effectiveMapEnergyFilterIds() - "electric"
            val isLuxembourg = fr.geoking.gaston.countryCodesAtMapPosition(searchLat, searchLon).contains("LU")
            MapPoiFilter.filterCheapest(
                pois = visiblePois,
                selectedFuelIds = fuelIds,
                isLuxembourg = isLuxembourg,
                fromLat = searchLat,
                fromLon = searchLon,
                limit = MapPoiFilter.CAR_CHEAPEST_COUNT,
            )
        } else {
            visiblePois
        }
    }

    private fun mapFitSizePx(): Pair<Int, Int> {
        val area = currentVisibleArea
        return if (area != null && area.width() > 0 && area.height() > 0) {
            area.width() to area.height()
        } else {
            mapWidthPx to mapHeightPx
        }
    }

    /** Viewport sized to the host-visible map boundary when zoomed out; null when default nearby radius covers the screen. */
    private fun currentSearchViewport() = mapFitSizePx().let { (w, h) ->
        AutoMapCamera.searchViewportOrNull(
            centerLat = searchLat,
            centerLon = searchLon,
            zoom = zoom,
            mapWidthPx = w,
            mapHeightPx = h,
        )
    }

    /** Search radius matching the visible map (at least the default nearby radius). */
    private fun currentSearchRadiusKm(): Double = mapFitSizePx().let { (w, h) ->
        AutoMapCamera.searchRadiusKm(
            centerLat = searchLat,
            centerLon = searchLon,
            zoom = zoom,
            mapWidthPx = w,
            mapHeightPx = h,
        ).toDouble()
    }

    private fun applyCameraForStations(
        userLat: Double,
        userLon: Double,
        stations: List<Poi>,
        settings: AppSettings,
        preserveZoom: Boolean = false,
    ) {
        val (fitW, fitH) = mapFitSizePx()
        val fuelIds = settings.effectiveMapEnergyFilterIds() - "electric"
        val camera = if (preserveZoom) {
            AutoMapCamera.Camera(userLat, userLon, zoom)
        } else {
            AutoMapCamera.cameraForMapFocus(
                userLat = userLat,
                userLon = userLon,
                stations = stations,
                mapWidthPx = fitW,
                mapHeightPx = fitH,
                fallbackZoom = AutoMapCamera.DEFAULT_ZOOM,
                sortByPrice = sortByPrice,
                selectedFuelIds = fuelIds,
            )
        }
        searchLat = camera.centerLat
        searchLon = camera.centerLon
        zoom = if (preserveZoom) zoom else camera.zoom
        lastAppliedSearchLat = searchLat
        lastAppliedSearchLon = searchLon
        lastAppliedZoom = zoom
    }

    private fun shouldRefitCameraForVisibleArea(area: Rect): Boolean {
        if (area.width() <= 0 || area.height() <= 0) return false
        if (!hasAppliedVisibleAreaCamera) return true
        return abs(area.width() - lastCameraFitWidth) > VISIBLE_AREA_SIZE_DELTA_PX ||
            abs(area.height() - lastCameraFitHeight) > VISIBLE_AREA_SIZE_DELTA_PX
    }

    private fun refitCameraForVisibleAreaIfNeeded() {
        val area = currentVisibleArea ?: return
        if (isLoading || !shouldRefitCameraForVisibleArea(area)) return
        val settings = settingsManager.settings.value
        val filteredPois = getFilteredPois(settings)
        if (filteredPois.isEmpty() && itineraryPoints.isEmpty()) return

        val prevLat = searchLat
        val prevLon = searchLon
        val prevZoom = zoom
        val (userLat, userLon) = searchCenterFlow.value
        if (itineraryPoints.isNotEmpty()) {
            applyCameraForItinerary(userLat, userLon, filteredPois)
        } else {
            applyCameraForStations(userLat, userLon, filteredPois, settings)
        }
        lastCameraFitWidth = area.width()
        lastCameraFitHeight = area.height()
        hasAppliedVisibleAreaCamera = true

        if (prevLat != searchLat || prevLon != searchLon || prevZoom != zoom) {
            Log.d(
                "MapLibrePoiScreen",
                "refitCamera applied area=${area.width()}x${area.height()} zoom=$prevZoom->$zoom"
            )
            syncRendererWithMapState()
        }
    }

    private fun syncRendererWithMapState() {
        val renderer = mapRenderer ?: return
        val settings = settingsManager.settings.value
        renderer.setStyleUrl(resolveAutoMapStyleUrl(settings, carContext))
        val filteredPois = getFilteredPois(settings)
        val poiIds = filteredPois.map { it.id }

        val selected = mapSelectedPoi
        val cameraLat = selected?.latitude ?: searchLat
        val cameraLon = selected?.longitude ?: searchLon
        if (cameraLat != lastAppliedSearchLat ||
            cameraLon != lastAppliedSearchLon ||
            zoom != lastAppliedZoom
        ) {
            renderer.updateLocation(cameraLat, cameraLon, zoom)
            lastAppliedSearchLat = cameraLat
            lastAppliedSearchLon = cameraLon
            lastAppliedZoom = zoom
        }
        renderer.setMapOrientation(orientationMode, lastKnownBearingDegrees)
        // Show the same filtered stations as the list; focus stations are only for zoom.
        // When a station is selected, keep only that marker (detail handoff / shared surface).
        val mapPois = if (selected != null) listOf(selected) else filteredPois
        renderer.updatePois(
            newPois = mapPois,
            effectiveEnergyTypes = settings.effectiveMapEnergyFilterIds(),
            effectivePowerLevels = settings.effectiveIrvePowerLevels(),
            availability = availabilityByPoiId,
            selectedId = selected?.id,
        )
        val (userLat, userLon) = searchCenterFlow.value
        renderer.updateSearchRadius(
            centerLat = userLat,
            centerLon = userLon,
            radiusKm = if (itineraryPoints.isEmpty()) {
                currentSearchRadiusKm()
            } else {
                null
            },
        )
        renderer.setQueryPending(isQueryPending)
        lastSyncedPoiIds = poiIds
    }

    private suspend fun searchPoisWithZoomOut(
        userLat: Double,
        userLon: Double,
        settings: AppSettings,
        preserveZoom: Boolean = false
    ): Pair<List<Poi>, List<PoiProviderError>> {
        if (itineraryPoints.isNotEmpty() && routePlanner != null) {
            val result = routePlanner.getStationsAlongRoute(
                points = itineraryPoints,
                poiProvider = poiProvider
            )
            val loadedPois = result.getOrDefault(emptyList())
            val filteredPois = StationMapFilters.apply(
                settings = settings,
                pois = loadedPois,
                providers = settings.effectiveProvidersAt(userLat, userLon),
                skipWhenOnlyOverpass = true
            )
            applyCameraForItinerary(userLat, userLon, filteredPois)
            return loadedPois to emptyList()
        }

        if (preserveZoom) {
            val filteredPois = getFilteredPois(settings)
            applyCameraForStations(userLat, userLon, filteredPois, settings, preserveZoom = true)
            return pois to errors
        }

        var lastResult = PoiSearchResult()
        poiProvider.searchFlow(
            PoiSearchRequest(
                latitude = userLat,
                longitude = userLon,
                viewport = currentSearchViewport(),
                categories = emptySet(),
                skipFilters = true,
            )
        ).collect { result ->
            lastResult = result
            val filteredPois = StationMapFilters.apply(
                settings = settings,
                pois = result.pois,
                providers = settings.effectiveProvidersAt(userLat, userLon),
                skipWhenOnlyOverpass = true,
            )
            applyCameraForStations(userLat, userLon, filteredPois, settings)
        }
        return lastResult.pois to lastResult.errors
    }

    private fun applyCameraForItinerary(userLat: Double, userLon: Double, stations: List<Poi>) {
        val pointsToFit = itineraryPoints + stations.map { it.latitude to it.longitude }
        if (pointsToFit.isEmpty()) return

        var minLat = pointsToFit.minOf { it.first }
        var maxLat = pointsToFit.maxOf { it.first }
        var minLon = pointsToFit.minOf { it.second }
        var maxLon = pointsToFit.maxOf { it.second }

        val (fitW, fitH) = mapFitSizePx()
        val zoomLevel = AutoMapCamera.zoomForBounds(
            minLat = minLat,
            maxLat = maxLat,
            minLng = minLon,
            maxLng = maxLon,
            mapWidthPx = fitW,
            mapHeightPx = fitH
        )

        searchLat = (minLat + maxLat) / 2.0
        searchLon = (minLon + maxLon) / 2.0
        zoom = zoomLevel
        lastAppliedSearchLat = searchLat
        lastAppliedSearchLon = searchLon
        lastAppliedZoom = zoom
    }

    private var loadPoisJob: Job? = null

    private fun loadPois(preserveZoom: Boolean = false, showLoading: Boolean = true) {
        if (mapSelectedPoi != null) return
        loadPoisJob?.cancel()
        val gen = ++queryGeneration
        loadPoisJob = lifecycleScope.launch {
            isQueryPending = true
            syncRendererWithMapState()
            if (showLoading) {
                isLoading = true
                invalidate()
            }

            var queryLat: Double? = null
            var queryLon: Double? = null
            try {
                val location = LocationHelper.getCurrentLocation(carContext)
                val (lat, lon) = if (location != null) {
                    settingsManager.saveLastKnownLocation(location.latitude, location.longitude)
                    location.latitude to location.longitude
                } else {
                    LocationHelper.getInitialLocation(carContext, settingsManager)
                }
                queryLat = lat
                queryLon = lon

                searchCenterFlow.value = lat to lon
                lastKnownBearingDegrees = AutoMapHeading.resolveBearing(location, lastKnownBearingDegrees)
                Log.d("MapLibrePoiScreen", "loadPois search center lat=$lat lon=$lon bearing=$lastKnownBearingDegrees")

                if (itineraryPoints.isEmpty()) {
                    searchLat = lat
                    searchLon = lon
                }

                mapRenderer?.updateUserLocation(lat, lon, lastKnownBearingDegrees)
                applyMapOrientationToRenderer()

                val settings = settingsManager.settings.value

                if (itineraryPoints.isNotEmpty() && routePlanner != null) {
                    val result = routePlanner.getStationsAlongRoute(
                        points = itineraryPoints,
                        poiProvider = poiProvider
                    )
                    pois = result.getOrDefault(emptyList())
                    errors = emptyList()
                    val filteredPois = getFilteredPois(settings)
                    applyCameraForItinerary(lat, lon, filteredPois)
                    mapRenderer?.updateUserLocation(lat, lon, lastKnownBearingDegrees)
                    syncRendererWithMapState()
                    isLoading = false
                    refitCameraForVisibleAreaIfNeeded()
                    invalidate()

                    // Availability for itinerary stations
                    val provider = availabilityProviderFactory.getProvider(lat, lon)
                    if (provider != null) {
                        val availabilities = try {
                            provider.getAvailability(lat, lon, 10)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            emptyList()
                        }
                        availabilityByPoiId = matchAvailabilityToPois(availabilities, pois)
                        syncRendererWithMapState()
                        invalidate()
                    }
                } else {
                    suspend fun collectNearbySearch(preserveCameraZoom: Boolean) {
                        poiProvider.searchFlow(
                            PoiSearchRequest(
                                latitude = lat,
                                longitude = lon,
                                viewport = currentSearchViewport(),
                                categories = emptySet(),
                                skipFilters = true,
                            )
                        ).collect { result ->
                            pois = PoiMerger.mergeInto(pois, result.pois)
                            errors = result.errors
                            val filteredPois = getFilteredPois(settings)

                            applyCameraForStations(
                                userLat = lat,
                                userLon = lon,
                                stations = filteredPois,
                                settings = settings,
                                preserveZoom = preserveCameraZoom,
                            )
                            mapRenderer?.updateUserLocation(lat, lon, lastKnownBearingDegrees)
                            syncRendererWithMapState()
                            isLoading = false
                            refitCameraForVisibleAreaIfNeeded()
                            invalidate()

                            val provider = availabilityProviderFactory.getProvider(lat, lon)
                            if (provider != null) {
                                val availabilityRadiusKm = currentSearchRadiusKm().toInt().coerceIn(10, 20)
                                val availabilities = try {
                                    provider.getAvailability(lat, lon, availabilityRadiusKm)
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    emptyList()
                                }
                                availabilityByPoiId = matchAvailabilityToPois(availabilities, pois)
                                syncRendererWithMapState()
                                invalidate()
                            }
                        }
                    }

                    val radiusBeforeCameraKm = currentSearchRadiusKm()
                    collectNearbySearch(preserveCameraZoom = preserveZoom)
                    // Camera may zoom out to frame focus stations; re-query for that wider visible boundary.
                    if (!preserveZoom && currentSearchRadiusKm() > radiusBeforeCameraKm) {
                        collectNearbySearch(preserveCameraZoom = true)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("MapLibrePoiScreen", "loadPois failed", e)
                pois = emptyList()
                errors = listOf(PoiProviderError("System", e.message ?: "Unknown error", isCritical = true))
            } finally {
                if (gen == queryGeneration) {
                    val lat = queryLat
                    val lon = queryLon
                    if (lat != null && lon != null && itineraryPoints.isEmpty()) {
                        recordQueryCoverage(lat, lon)
                    }
                    isQueryPending = false
                    isLoading = false
                    syncRendererWithMapState()
                    invalidate()
                }
            }
        }
    }

    /**
     * MapWithContentTemplate with a surface renderer allows at most one action on the top [ActionStrip].
     * Extra actions (e.g. API errors) belong on the nested template [Header].
     */
    private fun pushApiErrorsDetailScreen() {
        val errorMsg = errors.joinToString("\n") { "${it.providerName}: ${it.message}" }
        screenManager.push(
            object : Screen(carContext) {
                override fun onGetTemplate(): Template {
                    return MessageTemplate.Builder(errorMsg)
                        .setHeader(
                            Header.Builder()
                                .setTitle(carContext.getString(R.string.screen_api_errors))
                                .setStartHeaderAction(Action.BACK)
                                .build()
                        )
                        .addAction(
                            Action.Builder()
                                .setTitle(carContext.getString(R.string.action_retry))
                                .setOnClickListener {
                                    screenManager.pop()
                                    loadPois()
                                }
                                .build()
                        )
                        .build()
                }
            }
        )
    }

    private fun mapContentHeaderBuilder(title: String, currentSettings: AppSettings): Header.Builder {
        return Header.Builder()
            .setTitle(title)
            .setStartHeaderAction(Action.BACK)
    }

    private fun applyMapOrientationToRenderer() {
        mapRenderer?.setMapOrientation(orientationMode, lastKnownBearingDegrees)
        lastMapOrientationUpdateMillis = System.currentTimeMillis()
    }

    private fun createMapRenderer(): CarMapLibreRenderer =
        CarMapLibreRenderer(carContext, lifecycle)

    private fun toggleMapOrientation() {
        orientationMode = when (orientationMode) {
            MapOrientationMode.NorthUp -> MapOrientationMode.HeadingUp
            MapOrientationMode.HeadingUp -> MapOrientationMode.NorthUp
        }
        applyMapOrientationToRenderer()
        if (orientationMode == MapOrientationMode.HeadingUp) {
            lifecycleScope.launch { refreshHeadingFromLocation() }
        } else {
            syncRendererWithMapState()
        }
        invalidate()
    }

    private fun recenterMap() {
        lifecycleScope.launch {
            val location = LocationHelper.getCurrentLocation(carContext)
            if (location != null) {
                searchLat = location.latitude
                searchLon = location.longitude
                settingsManager.saveLastKnownLocation(location.latitude, location.longitude)
                searchCenterFlow.value = searchLat to searchLon
                lastKnownBearingDegrees = AutoMapHeading.resolveBearing(location, lastKnownBearingDegrees)
                mapRenderer?.updateLocation(searchLat, searchLon, zoom)
                mapRenderer?.updateUserLocation(searchLat, searchLon, lastKnownBearingDegrees)
                applyMapOrientationToRenderer()
            }
            loadPois(preserveZoom = true)
        }
    }

    private fun startHeadingUpdates() {
        stopHeadingUpdates()
        headingUpdateJob = lifecycleScope.launch {
            while (isActive) {
                refreshHeadingFromLocation()
                delay(30_000)
            }
        }
    }

    private fun stopHeadingUpdates() {
        headingUpdateJob?.cancel()
        headingUpdateJob = null
    }

    private suspend fun refreshHeadingFromLocation() {
        val priority = if (orientationMode == MapOrientationMode.HeadingUp) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val location = LocationHelper.getCurrentLocation(carContext, timeoutMs = 2_000L, priority = priority)
        if (location != null) {
            val lat = location.latitude
            val lon = location.longitude
            val newPos = lat to lon
            lastKnownBearingDegrees = AutoMapHeading.resolveBearing(location, lastKnownBearingDegrees)

            searchLat = lat
            searchLon = lon
            searchCenterFlow.value = newPos
            // Keep following the vehicle after zoom; only freeze the camera while a station is selected.
            if (mapSelectedPoi == null) {
                mapRenderer?.updateLocation(lat, lon, zoom)
                lastAppliedSearchLat = lat
                lastAppliedSearchLon = lon
                lastAppliedZoom = zoom
            }
            mapRenderer?.updateUserLocation(lat, lon, lastKnownBearingDegrees)

            if (shouldAddTrailPoint(historyPoints.lastOrNull(), newPos)) {
                historyPoints.add(newPos)
            }

            val shouldRequery = itineraryPoints.isEmpty() &&
                mapSelectedPoi == null &&
                shouldRequeryPois(lastQueryCoverage, lat, lon, currentSearchRadiusKm().toInt())
            if (shouldRequery) {
                loadPois(preserveZoom = true, showLoading = false)
            } else if (shouldRedrawFromMovement(lastRedrawPosition, newPos)) {
                lastRedrawPosition = newPos
                syncRendererWithMapState()
                if (orientationMode == MapOrientationMode.HeadingUp) {
                    applyMapOrientationToRenderer()
                } else {
                    invalidate()
                }
            } else if (orientationMode == MapOrientationMode.HeadingUp) {
                applyMapOrientationToRenderer()
            }
        }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        Log.d("MapLibrePoiScreen", "onSurfaceAvailable")
        mapRenderer?.detachSurface()
        startHeadingUpdates()
        val surface = surfaceContainer.surface
        if (surface == null) {
            Log.w("MapLibrePoiScreen", "SurfaceContainer.surface is null; skipping renderer start")
            mapRenderer = null
            return
        }
        mapWidthPx = surfaceContainer.width
        mapHeightPx = surfaceContainer.height
        val renderer = mapRenderer ?: createMapRenderer().also { mapRenderer = it }
        renderer.setStyleUrl(resolveAutoMapStyleUrl(settingsManager.settings.value, carContext))
        renderer.attachSurface(surfaceContainer)
        currentVisibleArea?.let { renderer.updateVisibleArea(it) }
        renderer.updateLocation(searchLat, searchLon, zoom)
        lastSyncedPoiIds = emptyList()
        syncRendererWithMapState()
        registerSurfaceCallback()
        renderer.updateUserLocation(searchLat, searchLon, lastKnownBearingDegrees)
        renderer.setStyleUrl(resolveAutoMapStyleUrl(settingsManager.settings.value, carContext))
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        Log.d("MapLibrePoiScreen", "onVisibleAreaChanged: $visibleArea")
        currentVisibleArea = visibleArea
        mapRenderer?.updateVisibleArea(visibleArea)
        visibleAreaCameraJob?.cancel()
        visibleAreaCameraJob = lifecycleScope.launch {
            delay(VISIBLE_AREA_CAMERA_DEBOUNCE_MS)
            refitCameraForVisibleAreaIfNeeded()
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.d("MapLibrePoiScreen", "onSurfaceDestroyed")
        visibleAreaCameraJob?.cancel()
        mapRenderer?.detachSurface()
    }

    override fun onClick(x: Float, y: Float) {
        val renderer = mapRenderer ?: return
        val clickedPois = renderer.findPoisAt(x, y)
        if (clickedPois.isEmpty()) return

        val poi = if (clickedPois.size == 1) {
            clickedPois.first()
        } else if (
            AutoMapPoiHitTest.shouldZoomInsteadOfOpen(
                hits = clickedPois,
                mapLat = renderer.mapLatForHitTest(),
                mapLon = renderer.mapLonForHitTest(),
                zoom = renderer.zoomForHitTest(),
                centerPxX = renderer.centerPxXForHitTest(),
                centerPxY = renderer.centerPxYForHitTest(),
            )
        ) {
            carContext.getCarService(AppManager::class.java)
                .showToast(carContext.getString(R.string.error_multiple_stations), CarToast.LENGTH_LONG)
            bumpZoom(1)
            return
        } else {
            clickedPois.first()
        }

        openStationDetail(poi, availabilityByPoiId[poi.id])
    }

    private fun registerSurfaceCallback() {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
        registerSurfaceCallback()
        // Returning from station detail: show all filtered pins and resume follow.
        loadPoisJob?.cancel()
        isQueryPending = false
        isLoading = false
        mapSelectedPoi = null
        lastAppliedSearchLat = searchLat
        lastAppliedSearchLon = searchLon
        mapRenderer?.updateLocation(searchLat, searchLon, zoom)
        startHeadingUpdates()
        syncRendererWithMapState()
        invalidate()
    }

    override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
        stopHeadingUpdates()
    }

    private fun bumpZoom(delta: Int) {
        val prevZoom = zoom
        zoom = (zoom + delta).coerceIn(AutoMapCamera.MIN_ZOOM, AutoMapCamera.MAX_ZOOM)
        lastAppliedZoom = zoom
        mapRenderer?.updateLocation(searchLat, searchLon, zoom)
        if (headingUpdateJob?.isActive != true) {
            startHeadingUpdates()
        }
        if (zoom < prevZoom) {
            val (userLat, userLon) = searchCenterFlow.value
            if (shouldRequeryForViewportChange(lastQueryCoverage, userLat, userLon, currentSearchRadiusKm().toInt())) {
                loadPois(preserveZoom = true, showLoading = false)
            } else {
                syncRendererWithMapState()
                invalidate()
            }
        } else {
            syncRendererWithMapState()
            invalidate()
        }
    }

    override fun onGetTemplate(): Template = safeCarTemplate(
        carContext = carContext,
        logTag = "MapLibrePoiScreen",
        templateName = "MapWithContentTemplate"
    ) {
        val currentSettings = settingsManager.settings.value
        val effectiveEnergies = currentSettings.effectiveMapEnergyFilterIds()

        val actionStripBuilder = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(carContext.actionSettingsIcon())
                    .setOnClickListener { screenManager.push(AutoMapSettingsScreen(carContext, settingsManager)) }
                    .build()
            )

        val hasFuelFilter = (effectiveEnergies - "electric").isNotEmpty()
        if (hasFuelFilter && (isCheapestFilterActive || getFilteredPois(currentSettings).any { !it.fuelPrices.isNullOrEmpty() })) {
            actionStripBuilder.addAction(
                carContext.cheapestFilterAction(isCheapestFilterActive) {
                    if (isCheapestFilterActive) {
                        isCheapestFilterActive = false
                        sortByPrice = false
                    } else {
                        isCheapestFilterActive = true
                        sortByPrice = true
                        val filtered = getFilteredPois(currentSettings)
                        carContext.getCarService(AppManager::class.java)
                            .showToast(carContext.getString(R.string.cheapest_stations_toast, filtered.size), CarToast.LENGTH_SHORT)
                    }
                    syncRendererWithMapState()
                    invalidate()
                }
            )
        }
        val actionStrip = actionStripBuilder.build()

        val mapActionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setIcon(carContext.actionZoomInIcon())
                    .setOnClickListener { bumpZoom(1) }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(carContext.actionZoomOutIcon())
                    .setOnClickListener { bumpZoom(-1) }
                    .build()
            )
            .build()

        val mapController = MapController.Builder()
            .setMapActionStrip(mapActionStrip)
            .build()

        val effectivePowerLevels = currentSettings.effectiveIrvePowerLevels()

        val contentTemplate = if (isLoading) {
            ListTemplate.Builder()
                .setLoading(true)
                .setHeader(mapContentHeaderBuilder(title, currentSettings).build())
                .build()
        } else {
            val filteredPoisForSorting = getFilteredPois(currentSettings)
            val (userLat, userLon) = searchCenterFlow.value
            val sortedPois = MapPoiFilter.sortPois(
                pois = filteredPoisForSorting,
                lat = userLat,
                lon = userLon,
                sortByPrice = sortByPrice,
                selectedFuelIds = effectiveEnergies - "electric"
            )

            val listLimit = try {
                carContext.getCarService(ConstraintManager::class.java)
                    .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
            } catch (_: Exception) {
                6
            }

            val itemListBuilder = ItemList.Builder()
                .setNoItemsMessage(carContext.getString(R.string.poi_no_pois_found))

            val limitedPois = sortedPois.take(listLimit)
            limitedPois.forEach { item ->
                val availability = availabilityByPoiId[item.id]
                itemListBuilder.addItem(
                    AutoPoiUiHelper.buildPoiRow(
                        carContext = carContext,
                        poi = item,
                        availability = availability,
                        effectiveEnergyTypes = effectiveEnergies,
                        effectivePowerLevels = effectivePowerLevels,
                        distanceFromLatLon = userLat to userLon,
                        includePlace = false,
                        browsable = true,
                    ) {
                        openStationDetail(item, availability)
                    }
                )
            }

            ListTemplate.Builder()
                .setHeader(mapContentHeaderBuilder(title, currentSettings).build())
                .setSingleList(itemListBuilder.build())
                .build()
        }

        MapWithContentTemplate.Builder()
            .setContentTemplate(contentTemplate)
            .setActionStrip(actionStrip)
            .setMapController(mapController)
            .build()
    }
}
