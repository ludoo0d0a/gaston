package fr.geoking.gaston.auto

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Template
import androidx.car.app.model.PlaceListMapTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.R
import fr.geoking.gaston.CarMapMode
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.StationMapFilters
import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiMerger
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.api.belib.matchAvailabilityToPois
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.effectiveProvidersAt
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.shared.location.approxDistanceKm
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * POI map using the host-rendered [PlaceListMapTemplate] (Google Maps on Android Auto).
 *
 * Map camera orientation (north-up vs heading-up), zoom, and the driving arrow are
 * host-controlled on this template. Canvas modes implement [AaCanvasMapControls].
 */
class NativeMapPoiScreen(
    carContext: CarContext,
    private val poiProvider: PoiProvider,
    private val availabilityProviderFactory: BorneAvailabilityProviderFactory,
    private val settingsManager: SettingsManager,
    private val communityRepo: CommunityPoiRepository? = null,
    private val favoritesRepo: FavoritesRepository? = null,
    private val title: String = carContext.getString(R.string.dashboard_nearby_stations)
) : Screen(carContext), DefaultLifecycleObserver {

    private var pois: List<Poi> = emptyList()
    private var availabilityByPoiId: Map<String, StationAvailabilitySummary> = emptyMap()
    private var isLoading = true
    private var searchLat: Double = settingsManager.settings.value.lastKnownLat ?: 48.8566
    private var searchLon: Double = settingsManager.settings.value.lastKnownLon ?: 2.3522
    private var sortByPrice: Boolean = false
    private var refreshJob: Job? = null
    private var loadPoisJob: Job? = null
    private var loadPoisGeneration: Int = 0
    private var isCheapestFilterActive: Boolean = false

    init {
        lifecycle.addObserver(this)
        lifecycleScope.launch {
            settingsManager.settings
                .map { s ->
                    Triple(
                        s.selectedPoiProviders,
                        s.selectedMapEnergyTypes,
                        s.effectiveProvidersAt(searchLat, searchLon)
                    )
                }
                .distinctUntilChanged()
                .collectLatest {
                    loadPois()
                }
        }
    }

    private fun getFilteredPois(currentSettings: AppSettings): List<Poi> {
        val effectiveProviders = currentSettings.effectiveProvidersAt(searchLat, searchLon)
        val basePois = StationMapFilters.apply(
            settings = currentSettings,
            pois = pois,
            providers = effectiveProviders,
            skipWhenOnlyOverpass = true
        )

        return if (isCheapestFilterActive) {
            val fuelIds = currentSettings.effectiveMapEnergyFilterIds() - "electric"
            val isLuxembourg = fr.geoking.gaston.countryCodesAtMapPosition(searchLat, searchLon).contains("LU")
            MapPoiFilter.filterCheapest(
                pois = basePois,
                selectedFuelIds = fuelIds,
                isLuxembourg = isLuxembourg,
                fromLat = searchLat,
                fromLon = searchLon,
                limit = MapPoiFilter.CAR_CHEAPEST_COUNT,
            )
        } else {
            basePois
        }
    }

    private fun loadPois(showLoading: Boolean = true, overrideLat: Double? = null, overrideLon: Double? = null) {
        loadPoisJob?.cancel()
        val gen = ++loadPoisGeneration
        loadPoisJob = lifecycleScope.launch {
            if (showLoading) {
                isLoading = true
                invalidate()
            }

            val (lat, lon) = if (overrideLat != null && overrideLon != null) {
                overrideLat to overrideLon
            } else {
                LocationHelper.getInitialLocation(carContext, settingsManager)
            }

            searchLat = lat
            searchLon = lon

            try {
                poiProvider.searchFlow(
                    PoiSearchRequest(
                        latitude = lat,
                        longitude = lon,
                        viewport = null,
                        categories = emptySet(),
                        skipFilters = true,
                    )
                ).collect { result ->
                    pois = PoiMerger.mergeInto(pois, result.pois)
                        .sortedBy { approxDistanceKm(lat, lon, it.latitude, it.longitude) }
                        .take(200)
                    val provider = availabilityProviderFactory.getProvider(lat, lon)
                    if (provider != null) {
                        val availabilities = try {
                            provider.getAvailability(lat, lon, 10)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            emptyList()
                        }
                        availabilityByPoiId = matchAvailabilityToPois(availabilities, pois)
                    }
                    isLoading = false
                    invalidate()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("NativeMapPoiScreen", "loadPois failed", e)
            } finally {
                if (gen == loadPoisGeneration) {
                    isLoading = false
                    invalidate()
                }
            }
        }
    }

    override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
        if (AutoCarMapModeSwitcher.replaceIfStale(this, CarMapMode.Native, settingsManager, title)) return
        loadPoisJob?.cancel()
        isLoading = false
        invalidate()
        startRefreshLoop()
    }

    override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
        stopRefreshLoop()
    }

    private fun startRefreshLoop() {
        stopRefreshLoop()
        refreshJob = lifecycleScope.launch {
            while (true) {
                delay(30_000)
                val location = LocationHelper.getCurrentLocation(carContext)
                if (location != null) {
                    loadPois(showLoading = false, overrideLat = location.latitude, overrideLon = location.longitude)
                } else {
                    loadPois(showLoading = false)
                }
            }
        }
    }

    private fun stopRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = null
    }

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "NativeMapPoiScreen", "PlaceListMapTemplate") {
        val currentSettings = settingsManager.settings.value
        val effectiveEnergies = currentSettings.effectiveMapEnergyFilterIds()
        val hasFuelFilter = (effectiveEnergies - "electric").isNotEmpty()
        val effectivePowerLevels = currentSettings.effectiveIrvePowerLevels()

        val actionStripBuilder = ActionStrip.Builder()

        actionStripBuilder.addAction(
            Action.Builder()
                .setIcon(carContext.actionSettingsIcon())
                .setOnClickListener {
                    screenManager.push(AutoMapSettingsScreen(carContext, settingsManager))
                }
                .build()
        )
        actionStripBuilder.addAction(
            carContext.cycleMapModeAction(currentSettings.carMapMode) {
                AutoCarMapModeSwitcher.cycle(
                    screen = this@NativeMapPoiScreen,
                    settingsManager = settingsManager,
                    title = title,
                    replaceMapNow = true,
                )
            }
        )

        val fuelIdsForFilter = effectiveEnergies - "electric"
        if (hasFuelFilter && (isCheapestFilterActive || pois.any { p -> p.fuelPrices?.any { MapPoiFilter.fuelNameToId(it.fuelName) in fuelIdsForFilter } == true })) {
            actionStripBuilder.addAction(
                carContext.cheapestFilterAction(isCheapestFilterActive) {
                    if (isCheapestFilterActive) {
                        isCheapestFilterActive = false
                        sortByPrice = false
                    } else {
                        isCheapestFilterActive = true
                        sortByPrice = true
                        invalidate()
                        val cheapestCount = getFilteredPois(currentSettings).size
                        carContext.getCarService(androidx.car.app.AppManager::class.java)
                            .showToast(carContext.getString(R.string.cheapest_stations_toast, cheapestCount), CarToast.LENGTH_SHORT)
                    }
                    invalidate()
                }
            )
        }
        val actionStrip = actionStripBuilder.build()

        val anchorPlace = Place.Builder(CarLocation.create(searchLat, searchLon))
            .setMarker(PlaceMarker.Builder().setColor(CarColor.RED).build())
            .build()

        // PlaceListMapTemplate: loading and item list are mutually exclusive (see Builder.build()).
        if (isLoading) {
            return@safeCarTemplate PlaceListMapTemplate.Builder()
                .setTitle(title)
                .setHeaderAction(Action.BACK)
                .setActionStrip(actionStrip)
                .setLoading(true)
                .setAnchor(anchorPlace)
                .build()
        }

        val listLimit = try {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } catch (_: Exception) {
            6
        }

        val itemListBuilder = ItemList.Builder()
            .setNoItemsMessage(carContext.getString(R.string.poi_no_pois_found))

        val filteredPois = getFilteredPois(currentSettings)

        val fuelIds = effectiveEnergies - "electric"
        val sortedPois = MapPoiFilter.sortPois(
            pois = filteredPois,
            lat = searchLat,
            lon = searchLon,
            sortByPrice = sortByPrice,
            selectedFuelIds = fuelIds
        )

        val poisWithPrices = if (fuelIds.isNotEmpty()) {
            sortedPois.filter { p -> p.fuelPrices?.any { MapPoiFilter.fuelNameToId(it.fuelName) in fuelIds } == true }
                .take(listLimit.coerceAtMost(5))
        } else emptyList()

        val otherPois = sortedPois.filter { it !in poisWithPrices }.take(listLimit - poisWithPrices.size)
        val displayPois = (poisWithPrices + otherPois).sortedBy { approxDistanceKm(searchLat, searchLon, it.latitude, it.longitude) }

        displayPois.take(listLimit).forEach { item ->
            val availability = availabilityByPoiId[item.id]
            itemListBuilder.addItem(
                AutoPoiUiHelper.buildPoiRow(
                    carContext = carContext,
                    poi = item,
                    availability = availability,
                    effectiveEnergyTypes = effectiveEnergies,
                    effectivePowerLevels = effectivePowerLevels,
                    distanceFromLatLon = searchLat to searchLon,
                    includePlace = true,
                ) {
                loadPoisJob?.cancel()
                isLoading = false
                    screenManager.push(
                        PlaceListMapStationDetailScreen(
                            carContext = carContext,
                            poi = item,
                            availability = availability,
                            searchLat = searchLat,
                            searchLon = searchLon,
                            effectiveEnergies = effectiveEnergies,
                            effectivePowerLevels = effectivePowerLevels,
                            favoritesRepo = favoritesRepo,
                        )
                    )
                }
            )
        }

        PlaceListMapTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .setLoading(false)
            .setItemList(itemListBuilder.build())
            .setAnchor(anchorPlace)
            .build()
    }
}
