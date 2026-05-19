package fr.geoking.gaston.auto

import android.Manifest
import android.content.Intent
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
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import android.graphics.Rect
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.AppManager
import androidx.car.app.constraints.ConstraintManager
import androidx.lifecycle.DefaultLifecycleObserver
import fr.geoking.gaston.poi.PoiProviderType
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.FuelCard
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.ThemeMode
import fr.geoking.gaston.StationMapFilters
import fr.geoking.gaston.VehicleType
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.poi.PoiSearchResult
import fr.geoking.gaston.poi.PoiProviderError
import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.poi.PoiProvider
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
import fr.geoking.gaston.shared.location.approxDistanceKm
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

/**
 * POI map with a custom OSM surface renderer. Supports north-up and heading-up orientation
 * via header controls (native [NativeMapPoiScreen] cannot rotate — host-controlled map).
 */
class CustomMapPoiScreen(
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
    private val title: String = "Nearby Stations"
) : Screen(carContext), SurfaceCallback, DefaultLifecycleObserver {

    private var pois: List<Poi> = emptyList()
    private var errors: List<PoiProviderError> = emptyList()
    private var availabilityByPoiId: Map<String, StationAvailabilitySummary> = emptyMap()
    private var favoriteIds: Set<String> = emptySet()
    private var isLoading = true
    private var searchLat: Double = settingsManager.settings.value.lastKnownLat ?: 48.8566
    private var searchLon: Double = settingsManager.settings.value.lastKnownLon ?: 2.3522
    private var zoom: Int = AutoMapCamera.DEFAULT_ZOOM
    private var sortByPrice: Boolean = false
    private var currentVisibleArea: Rect? = null
    private var mapWidthPx: Int = 800
    private var mapHeightPx: Int = 480

    private var surfaceRenderer: AutoSurfaceRenderer? = null
    private var themeCollectionJob: kotlinx.coroutines.Job? = null
    private var headingUpdateJob: Job? = null
    private var orientationMode: MapOrientationMode = MapOrientationMode.NorthUp
    private var lastKnownBearingDegrees: Float = 0f
    private var lastMapOrientationUpdateMillis: Long = 0

    /** Last resolved search center; combined with settings so auto mode reloads when the vehicle moves across regions. */
    private val searchCenterFlow = MutableStateFlow(searchLat to searchLon)

    init {
        lifecycle.addObserver(this)
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
                    loadPois()
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
                        s.vehiclePowerLevels
                    )
                }
                .distinctUntilChanged()
                .collectLatest {
                    invalidate()
                }
        }
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
        val vehiclePowerLevels: Set<Int>
    )

    private fun getFilteredPois(currentSettings: AppSettings): List<Poi> {
        val effectiveProviders = currentSettings.effectiveProvidersAt(searchLat, searchLon)
        return StationMapFilters.apply(
            settings = currentSettings,
            pois = pois,
            providers = effectiveProviders,
            skipWhenOnlyOverpass = true
        )
    }

    private fun mapFitSizePx(): Pair<Int, Int> {
        val area = currentVisibleArea
        return if (area != null && area.width() > 0 && area.height() > 0) {
            area.width() to area.height()
        } else {
            mapWidthPx to mapHeightPx
        }
    }

    private fun applyCameraForStations(userLat: Double, userLon: Double, stations: List<Poi>, searchZoom: Int) {
        val (fitW, fitH) = mapFitSizePx()
        val camera = AutoMapCamera.fitToUserAndStations(
            userLat = userLat,
            userLon = userLon,
            stations = stations,
            mapWidthPx = fitW,
            mapHeightPx = fitH,
            fallbackZoom = searchZoom,
        )
        searchLat = camera.centerLat
        searchLon = camera.centerLon
        zoom = camera.zoom
    }

    private suspend fun searchPoisWithZoomOut(
        userLat: Double,
        userLon: Double,
        settings: AppSettings,
    ): Pair<List<Poi>, List<PoiProviderError>> {
        val (fitW, fitH) = mapFitSizePx()
        var lastResult = PoiSearchResult()
        var lastFiltered = emptyList<Poi>()
        var lastSearchZoom = AutoMapCamera.DEFAULT_ZOOM

        for (searchZoom in AutoMapCamera.searchZoomLevels()) {
            lastSearchZoom = searchZoom
            val viewport = MapViewport(
                zoom = searchZoom.toFloat(),
                mapWidthPx = fitW.coerceAtLeast(1),
                mapHeightPx = fitH.coerceAtLeast(1),
            )
            lastResult = poiProvider.searchResult(
                PoiSearchRequest(
                    latitude = userLat,
                    longitude = userLon,
                    viewport = viewport,
                    categories = emptySet(),
                    skipFilters = true,
                )
            )
            lastFiltered = StationMapFilters.apply(
                settings = settings,
                pois = lastResult.pois,
                providers = settings.effectiveProvidersAt(userLat, userLon),
                skipWhenOnlyOverpass = true,
            )
            if (lastFiltered.isNotEmpty()) break
        }

        applyCameraForStations(userLat, userLon, lastFiltered, lastSearchZoom)
        return lastResult.pois to lastResult.errors
    }

    private fun loadPois() {
        lifecycleScope.launch {
            isLoading = true
            invalidate()

            val location = LocationHelper.getCurrentLocation(carContext)
            val (lat, lon) = if (location != null) {
                settingsManager.saveLastKnownLocation(location.latitude, location.longitude)
                location.latitude to location.longitude
            } else {
                LocationHelper.getInitialLocation(carContext, settingsManager)
            }

            searchLat = lat
            searchLon = lon
            searchCenterFlow.value = lat to lon
            lastKnownBearingDegrees = AutoMapHeading.resolveBearing(location, lastKnownBearingDegrees)
            Log.d("CustomMapPoiScreen", "loadPois search center lat=$lat lon=$lon bearing=$lastKnownBearingDegrees")

            surfaceRenderer?.updateUserLocation(searchLat, searchLon, lastKnownBearingDegrees)
            applyMapOrientationToRenderer()

            try {
                val settings = settingsManager.settings.value
                val (loadedPois, loadedErrors) = searchPoisWithZoomOut(lat, lon, settings)
                pois = loadedPois
                errors = loadedErrors

                val filteredPois = getFilteredPois(settings)
                surfaceRenderer?.let { renderer ->
                    renderer.updateLocation(searchLat, searchLon, zoom)
                    renderer.updateUserLocation(lat, lon, lastKnownBearingDegrees)
                    renderer.updatePois(
                        newPois = filteredPois,
                        effectiveEnergyTypes = settings.effectiveMapEnergyFilterIds(),
                        effectivePowerLevels = settings.effectiveIrvePowerLevels()
                    )
                }

                Log.d(
                    "CustomMapPoiScreen",
                    "pois loaded: ${pois.size} filtered=${filteredPois.size} zoom=$zoom center=$searchLat,$searchLon errors=${errors.size}"
                )
                favoriteIds = favoritesRepo?.getFavorites()?.map { it.id }?.toSet() ?: emptySet()
                val provider = availabilityProviderFactory.getProvider(lat, lon)
                if (provider != null) {
                    try {
                        val availabilities = provider.getAvailability(lat, lon, 10)
                        availabilityByPoiId = matchAvailabilityToPois(availabilities, pois)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        availabilityByPoiId = emptyMap()
                    }
                } else {
                    availabilityByPoiId = emptyMap()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("CustomMapPoiScreen", "getGasStations failed", e)
                pois = emptyList()
                errors = listOf(PoiProviderError("System", e.message ?: "Unknown error", isCritical = true))
                availabilityByPoiId = emptyMap()
            }
            isLoading = false
            invalidate()
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

    private fun mapContentHeaderBuilder(title: String): Header.Builder {
        val builder = Header.Builder()
            .setTitle(title)
            .setStartHeaderAction(Action.BACK)

        val compassTitle = if (orientationMode == MapOrientationMode.NorthUp) {
            "Heading up"
        } else {
            "North up"
        }
        builder.addEndHeaderAction(
            Action.Builder()
                .setTitle(compassTitle)
                .setIcon(carContext.actionCompassIcon())
                .setOnClickListener { toggleMapOrientation() }
                .build()
        )
        builder.addEndHeaderAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.action_recenter))
                .setIcon(carContext.actionRecenterIcon())
                .setOnClickListener { recenterMap() }
                .build()
        )
        builder.addEndHeaderAction(
            Action.Builder()
                .setIcon(carContext.actionZoomInIcon())
                .setOnClickListener { bumpZoom(1) }
                .build()
        )
        builder.addEndHeaderAction(
            Action.Builder()
                .setIcon(carContext.actionZoomOutIcon())
                .setOnClickListener { bumpZoom(-1) }
                .build()
        )
        return builder
    }

    private fun applyMapOrientationToRenderer() {
        surfaceRenderer?.setMapOrientation(orientationMode, lastKnownBearingDegrees)
        lastMapOrientationUpdateMillis = System.currentTimeMillis()
    }

    private fun toggleMapOrientation() {
        orientationMode = when (orientationMode) {
            MapOrientationMode.NorthUp -> MapOrientationMode.HeadingUp
            MapOrientationMode.HeadingUp -> MapOrientationMode.NorthUp
        }
        applyMapOrientationToRenderer()
        if (orientationMode == MapOrientationMode.HeadingUp) {
            lifecycleScope.launch { refreshHeadingFromLocation() }
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
                surfaceRenderer?.updateLocation(searchLat, searchLon, zoom)
                surfaceRenderer?.updateUserLocation(searchLat, searchLon, lastKnownBearingDegrees)
                applyMapOrientationToRenderer()
            }
            loadPois()
        }
    }

    private fun startHeadingUpdates() {
        stopHeadingUpdates()
        headingUpdateJob = lifecycleScope.launch {
            while (isActive) {
                refreshHeadingFromLocation()
                delay(1_000)
            }
        }
    }

    private fun stopHeadingUpdates() {
        headingUpdateJob?.cancel()
        headingUpdateJob = null
    }

    private suspend fun refreshHeadingFromLocation() {
        val location = LocationHelper.getCurrentLocation(carContext, timeoutMs = 2_000L)
        if (location != null) {
            lastKnownBearingDegrees = AutoMapHeading.resolveBearing(location, lastKnownBearingDegrees)
            surfaceRenderer?.updateUserLocation(location.latitude, location.longitude, lastKnownBearingDegrees)

            if (orientationMode == MapOrientationMode.HeadingUp &&
                System.currentTimeMillis() - lastMapOrientationUpdateMillis >= 30_000L) {
                applyMapOrientationToRenderer()
            } else {
                invalidate()
            }
        }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        Log.d("CustomMapPoiScreen", "onSurfaceAvailable")
        surfaceRenderer?.stop()
        themeCollectionJob?.cancel()
        startHeadingUpdates()
        val surface = surfaceContainer.surface
        if (surface == null) {
            // Some head units/emulators can report an available container before the Surface is ready.
            // Avoid crashing; we'll get called again when the Surface is non-null.
            Log.w("CustomMapPoiScreen", "SurfaceContainer.surface is null; skipping renderer start")
            surfaceRenderer = null
            return
        }
        mapWidthPx = surfaceContainer.width
        mapHeightPx = surfaceContainer.height
        surfaceRenderer = AutoSurfaceRenderer(
            carContext,
            surface,
            surfaceContainer.width,
            surfaceContainer.height
        ).apply {
            updateLocation(searchLat, searchLon, zoom)
            currentVisibleArea?.let { updateVisibleArea(it) }
            val settings = settingsManager.settings.value
            val filteredPois = getFilteredPois(settings)
            updateUserLocation(searchLat, searchLon)
            updatePois(
                newPois = filteredPois,
                effectiveEnergyTypes = settings.effectiveMapEnergyFilterIds(),
                effectivePowerLevels = settings.effectiveIrvePowerLevels()
            )
            setMapOrientation(orientationMode, lastKnownBearingDegrees)
            start()
        }

        themeCollectionJob = lifecycleScope.launch {
            settingsManager.settings.collect { settings ->
                val dark = when (settings.uiThemeMode) {
                    ThemeMode.Dark -> true
                    ThemeMode.Light -> false
                    ThemeMode.System -> carContext.isDarkMode
                }
                val url = if (dark) "https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}.png" else "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
                surfaceRenderer?.setTileUrlTemplate(url)
            }
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        Log.d("CustomMapPoiScreen", "onVisibleAreaChanged: $visibleArea")
        currentVisibleArea = visibleArea
        surfaceRenderer?.updateVisibleArea(visibleArea)
        if (!isLoading) {
            val settings = settingsManager.settings.value
            val filteredPois = getFilteredPois(settings)
            if (filteredPois.isNotEmpty()) {
                val (userLat, userLon) = searchCenterFlow.value
                applyCameraForStations(userLat, userLon, filteredPois, zoom)
                surfaceRenderer?.updateLocation(searchLat, searchLon, zoom)
            }
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.d("CustomMapPoiScreen", "onSurfaceDestroyed")
        stopHeadingUpdates()
        surfaceRenderer?.stop()
        surfaceRenderer = null
        themeCollectionJob?.cancel()
    }

    override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
        stopHeadingUpdates()
        surfaceRenderer?.stop()
        surfaceRenderer = null
    }

    private fun bumpZoom(delta: Int) {
        zoom = (zoom + delta).coerceIn(4, 18)
        surfaceRenderer?.updateLocation(searchLat, searchLon, zoom)
        invalidate()
    }

    override fun onGetTemplate(): Template = safeCarTemplate(
        carContext = carContext,
        logTag = "CustomMapPoiScreen",
        templateName = "MapWithContentTemplate"
    ) {
        val actionStripBuilder = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_home))
                    .setIcon(carContext.actionHomeIcon())
                    .setOnClickListener { screenManager.popToRoot() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.cd_settings))
                    .setIcon(carContext.actionSettingsIcon())
                    .setOnClickListener { screenManager.push(AutoMapSettingsScreen(carContext, settingsManager)) }
                    .build()
            )

        if (errors.isNotEmpty()) {
            actionStripBuilder.addAction(
                Action.Builder()
                    .setIcon(carContext.actionErrorIcon())
                    .setOnClickListener { pushApiErrorsDetailScreen() }
                    .build()
            )
        }
        val actionStrip = actionStripBuilder.build()

        if (isLoading) {
            return@safeCarTemplate MapWithContentTemplate.Builder()
                .setContentTemplate(
                    ListTemplate.Builder()
                        .setLoading(true)
                        .setHeader(mapContentHeaderBuilder(title).build())
                        .build()
                )
                .setActionStrip(actionStrip)
                .build()
        }

        val currentSettings = settingsManager.settings.value

        // Respect the host's list limit (varies by vehicle/API level, default 6).
        val listLimit = try {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } catch (_: Exception) {
            6
        }

        val itemListBuilder = ItemList.Builder()
            .setNoItemsMessage("No POIs found")

        val effectiveEnergies = currentSettings.effectiveMapEnergyFilterIds()
        val effectivePowerLevels = currentSettings.effectiveIrvePowerLevels()
        val filteredPois = getFilteredPois(currentSettings)

        surfaceRenderer?.let { renderer ->
            renderer.updateLocation(searchLat, searchLon, zoom)
            renderer.setMapOrientation(orientationMode, lastKnownBearingDegrees)
            renderer.updatePois(
                newPois = filteredPois,
                effectiveEnergyTypes = effectiveEnergies,
                effectivePowerLevels = effectivePowerLevels
            )
        }

        val sortedPois = if (sortByPrice) {
            val fuelIds = effectiveEnergies - "electric"
            if (fuelIds.isEmpty()) {
                filteredPois.sortedBy { approxDistanceKm(searchLat, searchLon, it.latitude, it.longitude) }
            } else {
                filteredPois.sortedWith { a, b ->
                    val pricesA = a.fuelPrices?.filter { MapPoiFilter.fuelNameToId(it.fuelName) in fuelIds }
                    val pricesB = b.fuelPrices?.filter { MapPoiFilter.fuelNameToId(it.fuelName) in fuelIds }

                    val priceA = pricesA?.minByOrNull { it.price }?.price ?: Double.MAX_VALUE
                    val priceB = pricesB?.minByOrNull { it.price }?.price ?: Double.MAX_VALUE

                    if (priceA != priceB && (priceA != Double.MAX_VALUE || priceB != Double.MAX_VALUE)) {
                        priceA.compareTo(priceB)
                    } else {
                        val distA = approxDistanceKm(searchLat, searchLon, a.latitude, a.longitude)
                        val distB = approxDistanceKm(searchLat, searchLon, b.latitude, b.longitude)
                        distA.compareTo(distB)
                    }
                }
            }
        } else {
            filteredPois.sortedBy { approxDistanceKm(searchLat, searchLon, it.latitude, it.longitude) }
        }

        val limitedPois = sortedPois.take(listLimit)
        limitedPois.forEach { poi ->
            val availability = availabilityByPoiId[poi.id]
            itemListBuilder.addItem(
                AutoPoiUiHelper.buildPoiRow(
                    carContext = carContext,
                    poi = poi,
                    availability = availability,
                    effectiveEnergyTypes = effectiveEnergies,
                    effectivePowerLevels = effectivePowerLevels,
                    distanceFromLatLon = searchLat to searchLon
                ) {
                    screenManager.push(
                        PoiDetailScreen(
                            carContext = carContext,
                            poi = poi,
                            availabilitySummary = availability,
                            rating = null
                        )
                    )
                }
            )
        }

        val listTemplate = ListTemplate.Builder()
            .setHeader(mapContentHeaderBuilder(title).build())
            .setSingleList(itemListBuilder.build())
            .build()

        MapWithContentTemplate.Builder()
            .setContentTemplate(listTemplate)
            .setActionStrip(actionStrip)
            .build()
    }
}
