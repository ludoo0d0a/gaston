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
import androidx.car.app.model.CarIcon
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
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.FuelCard
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.StationMapFilters
import fr.geoking.gaston.VehicleType
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiSearchRequest
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
import fr.geoking.gaston.toll.TollCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import fr.geoking.gaston.api.belib.matchAvailabilityToPois
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
    private val favoritesRepo: FavoritesRepository? = null
) : Screen(carContext), SurfaceCallback, DefaultLifecycleObserver {

    private var pois: List<Poi> = emptyList()
    private var errors: List<PoiProviderError> = emptyList()
    private var availabilityByPoiId: Map<String, StationAvailabilitySummary> = emptyMap()
    private var favoriteIds: Set<String> = emptySet()
    private var isLoading = true
    private var searchLat: Double = settingsManager.settings.value.lastKnownLat ?: 48.8566
    private var searchLon: Double = settingsManager.settings.value.lastKnownLon ?: 2.3522
    private var zoom: Int = 13
    private var sortByPrice: Boolean = false
    private var currentVisibleArea: Rect? = null

    private var surfaceRenderer: AutoSurfaceRenderer? = null

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

    private fun loadPois() {
        lifecycleScope.launch {
            isLoading = true
            invalidate()

            val (lat, lon) = LocationHelper.getInitialLocation(carContext, settingsManager)

            searchLat = lat
            searchLon = lon
            searchCenterFlow.value = lat to lon
            Log.d("CustomMapPoiScreen", "loadPois search center lat=$lat lon=$lon")

            surfaceRenderer?.updateUserLocation(searchLat, searchLon)

            try {
                val settings = settingsManager.settings.value
                val result = poiProvider.searchResult(PoiSearchRequest(lat, lon, null, emptySet(), skipFilters = true))
                pois = result.pois
                errors = result.errors

                val filteredPois = getFilteredPois(settings)
                surfaceRenderer?.let { renderer ->
                    renderer.updateLocation(searchLat, searchLon)
                    renderer.updateUserLocation(searchLat, searchLon)
                    renderer.updatePois(
                        newPois = filteredPois,
                        effectiveEnergyTypes = settings.effectiveMapEnergyFilterIds(),
                        effectivePowerLevels = settings.effectiveIrvePowerLevels()
                    )
                }

                Log.d("CustomMapPoiScreen", "pois loaded: ${pois.size}, errors: ${errors.size}")
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
                                .setTitle("API Errors")
                                .setStartHeaderAction(Action.BACK)
                                .build()
                        )
                        .addAction(
                            Action.Builder()
                                .setTitle("Retry")
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
        if (errors.isNotEmpty()) {
            builder.addEndHeaderAction(
                Action.Builder()
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_error_outline)).build())
                    .setOnClickListener { pushApiErrorsDetailScreen() }
                    .build()
            )
        }
        return builder
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        Log.d("CustomMapPoiScreen", "onSurfaceAvailable")
        surfaceRenderer?.stop()
        val surface = surfaceContainer.surface
        if (surface == null) {
            // Some head units/emulators can report an available container before the Surface is ready.
            // Avoid crashing; we'll get called again when the Surface is non-null.
            Log.w("CustomMapPoiScreen", "SurfaceContainer.surface is null; skipping renderer start")
            surfaceRenderer = null
            return
        }
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
            start()
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        Log.d("CustomMapPoiScreen", "onVisibleAreaChanged: $visibleArea")
        currentVisibleArea = visibleArea
        surfaceRenderer?.updateVisibleArea(visibleArea)
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.d("CustomMapPoiScreen", "onSurfaceDestroyed")
        surfaceRenderer?.stop()
        surfaceRenderer = null
    }

    override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
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
        // With a surface-rendered MapWithContentTemplate, hosts can be strict about the ActionStrip.
        // Keep it to a single action and move other controls into the list content.
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Home")
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_home)).build())
                    .setOnClickListener { screenManager.popToRoot() }
                    .build()
            )
            .build()

        val title = "Nearby Stations"

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

        // 1) Functional rows (action/navigation controls)
        var functionalRowCount = 5
        itemListBuilder
            .addItem(
                Row.Builder()
                    .setTitle("Zoom In")
                    .setOnClickListener { bumpZoom(1) }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Zoom Out")
                    .setOnClickListener { bumpZoom(-1) }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(if (sortByPrice) "Sort: Price" else "Sort: Distance")
                    .setOnClickListener {
                        sortByPrice = !sortByPrice
                        invalidate()
                    }
                    .build()
            )

        val energyModeLabel = when {
            currentSettings.selectedMapEnergyTypes.contains("electric") && (currentSettings.selectedMapEnergyTypes - "electric").isNotEmpty() -> "Hybrid"
            currentSettings.selectedMapEnergyTypes.contains("electric") -> "Electric"
            else -> "Fuel"
        }
        // Navigation rows must be browsable so the host renders the chevron and allows the push.
        itemListBuilder.addItem(
            Row.Builder()
                .setTitle("Energy")
                .addText(energyModeLabel)
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(AutoEnergyMenuScreen(carContext, settingsManager))
                }
                .build()
        )
        itemListBuilder.addItem(
            Row.Builder()
                .setTitle("More Options")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_settings)).build())
                .setBrowsable(true)
                .setOnClickListener {
                    screenManager.push(
                        AutoMapMoreOptionsScreen(
                            carContext = carContext,
                            settingsManager = settingsManager,
                            lat = searchLat,
                            lon = searchLon,
                            onRecenter = { loadPois() }
                        )
                    )
                }
                .build()
        )

        // 2) Optional rows
        val hasCommunity = settingsManager.settings.value.isLoggedIn && communityRepo != null
        if (hasCommunity && functionalRowCount < listLimit) {
            functionalRowCount++
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle("Add POI")
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_add)).build())
                    .setBrowsable(true)
                    .setOnClickListener {
                        lifecycleScope.launch {
                            val loc = LocationHelper.getCurrentLocation(carContext)
                            val clat = loc?.latitude ?: searchLat
                            val clon = loc?.longitude ?: searchLon
                            screenManager.push(AddPoiAutoScreen(carContext, communityRepo, clat, clon) { loadPois() })
                        }
                    }
                    .build()
            )
        }

        val effectiveEnergies = currentSettings.effectiveMapEnergyFilterIds()
        val effectivePowerLevels = currentSettings.effectiveIrvePowerLevels()
        val filteredPois = getFilteredPois(currentSettings)

        surfaceRenderer?.let { renderer ->
            renderer.updateLocation(searchLat, searchLon, zoom)
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

        val capacity = (listLimit - functionalRowCount).coerceAtLeast(0)
        val limitedPois = sortedPois.take(capacity)
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
