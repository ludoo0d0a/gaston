package fr.geoking.gaston.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.geoking.gaston.PoiProviderSelectionMode
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.StationMapFilters
import fr.geoking.gaston.VehicleType
import fr.geoking.gaston.api.geocoding.GeocodedPlace
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.effectiveProviders
import fr.geoking.gaston.effectiveProvidersAt
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.poi.EnergyFilterMode
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.shared.location.approxDistanceKm
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private data class FetchKey(
    val selectedLocation: GeocodedPlace?,
    val providerMode: PoiProviderSelectionMode,
    val effectiveProviders: Set<PoiProviderType>,
    val amenityFetchUnion: Set<String>,
    val useVehicleFilter: Boolean,
    val vehicleType: VehicleType,
    val refreshTick: Int,
)

data class PhoneDashboardUiState(
    val favoriteIds: Set<String> = emptySet(),
    val rawNearbyPois: List<Poi> = emptyList(),
    val isLoadingPois: Boolean = false,
    val searchError: String? = null,
    val userLat: Double? = null,
    val userLon: Double? = null,
    val nearbyFuelPois: List<Poi> = emptyList(),
    val nearbyElectricPois: List<Poi> = emptyList(),
    val showLoaderByDelay: Boolean = false
)

@OptIn(FlowPreview::class)
class PhoneDashboardViewModel(
    private val settingsManager: SettingsManager,
    private val context: Context
) : ViewModel() {

    private val poiProviderFlow = MutableStateFlow<PoiProvider?>(null)
    private val favoritesRepoFlow = MutableStateFlow<FavoritesRepository?>(null)
    private val hasLocationPermissionFlow = MutableStateFlow(false)
    private val selectedSearchLocationFlow = MutableStateFlow<GeocodedPlace?>(null)

    private val favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    private val rawNearbyPois = MutableStateFlow<List<Poi>>(emptyList())
    private val isLoadingPois = MutableStateFlow(false)
    private val searchError = MutableStateFlow<String?>(null)
    private val userLat = MutableStateFlow<Double?>(settingsManager.settings.value.lastKnownLat)
    private val userLon = MutableStateFlow<Double?>(settingsManager.settings.value.lastKnownLon)
    private val showLoaderByDelay = MutableStateFlow(false)
    private val refreshTick = MutableStateFlow(0)

    init {
        // Collect favorite IDs when favoritesRepo is available
        viewModelScope.launch {
            favoritesRepoFlow.collectLatest { repo ->
                if (repo != null) {
                    favoriteIds.value = repo.getFavorites().map { it.id }.toSet()
                } else {
                    favoriteIds.value = emptySet()
                }
            }
        }

        // Delay loader visibility slightly to prevent flickering on fast connections
        viewModelScope.launch {
            isLoadingPois.collectLatest { loading ->
                if (loading) {
                    delay(400)
                    showLoaderByDelay.value = true
                } else {
                    showLoaderByDelay.value = false
                }
            }
        }

        // Fetch POIs flow: combines triggers and debounces network operations
        viewModelScope.launch {
            combine(
                poiProviderFlow,
                hasLocationPermissionFlow,
                selectedSearchLocationFlow,
                settingsManager.settings,
                refreshTick
            ) { provider, hasPerm, selectedLoc, settings, tick ->
                if (provider == null) return@combine null

                val lat = selectedLoc?.latitude ?: userLat.value
                val lon = selectedLoc?.longitude ?: userLon.value
                val effectiveProviders = if (lat != null && lon != null) {
                    settings.effectiveProvidersAt(lat, lon)
                } else {
                    settings.effectiveProviders()
                }

                FetchKey(
                    selectedLocation = selectedLoc,
                    providerMode = settings.poiProviderSelectionMode,
                    effectiveProviders = effectiveProviders,
                    amenityFetchUnion = settings.selectedOverpassAmenityTypes + settings.cacheWarmAmenityTypes,
                    useVehicleFilter = settings.useVehicleFilter,
                    vehicleType = settings.vehicleType,
                    refreshTick = tick
                ) to provider
            }
                .filterNotNull()
                .distinctUntilChanged { old, new -> old.first == new.first }
                .debounce(300)
                .collectLatest { (key, provider) ->
                    val selectedLoc = key.selectedLocation
                    rawNearbyPois.value = emptyList()
                    isLoadingPois.value = true
                    searchError.value = null

                    val baseLat: Double?
                    val baseLon: Double?

                    if (selectedLoc != null) {
                        baseLat = selectedLoc.latitude
                        baseLon = selectedLoc.longitude
                    } else if (hasLocationPermissionFlow.value || (userLat.value != null && userLon.value != null)) {
                        if (hasLocationPermissionFlow.value) {
                            val freshLoc = LocationHelper.getCurrentLocation(context)
                            if (freshLoc != null && (freshLoc.latitude != userLat.value || freshLoc.longitude != userLon.value)) {
                                userLat.value = freshLoc.latitude
                                userLon.value = freshLoc.longitude
                                settingsManager.saveLastKnownLocation(freshLoc.latitude, freshLoc.longitude)
                            }
                        }
                        baseLat = selectedLoc?.latitude ?: userLat.value
                        baseLon = selectedLoc?.longitude ?: userLon.value
                    } else {
                        rawNearbyPois.value = emptyList()
                        searchError.value = "Location permission is required to find nearby stations."
                        isLoadingPois.value = false
                        return@collectLatest
                    }

                    if (baseLat != null && baseLon != null) {
                        userLat.value = baseLat
                        userLon.value = baseLon

                        try {
                            provider.searchFlow(
                                PoiSearchRequest(
                                    latitude = baseLat,
                                    longitude = baseLon,
                                    categories = setOf(fr.geoking.gaston.poi.PoiCategory.Gas, fr.geoking.gaston.poi.PoiCategory.Irve),
                                    skipFilters = true
                                )
                            ).collect { result ->
                                if (result.pois.isEmpty()) return@collect
                                rawNearbyPois.value = if (rawNearbyPois.value.isEmpty()) {
                                    result.pois
                                } else {
                                    fr.geoking.gaston.poi.PoiMerger.mergeInto(rawNearbyPois.value, result.pois)
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.e("PhoneDashboardViewModel", "Failed to fetch nearby POIs", e)
                            if (rawNearbyPois.value.isEmpty()) {
                                searchError.value = "Unable to fetch nearby stations. Please check your connection."
                            }
                        }
                    } else {
                        searchError.value = "Unable to determine your location."
                        rawNearbyPois.value = emptyList()
                    }
                    isLoadingPois.value = false
                }
        }
    }

    val nearbyFuelPois: StateFlow<List<Poi>> = combine(
        userLat,
        userLon,
        rawNearbyPois,
        settingsManager.settings
    ) { baseLat, baseLon, rawPois, settings ->
        if (baseLat == null || baseLon == null || rawPois.isEmpty()) {
            return@combine emptyList<Poi>()
        }
        val currentProviders = settings.effectiveProvidersAt(baseLat, baseLon)

        val fuelSettings = if (settings.useVehicleFilter && settings.vehicleEnergy == "hybrid") {
            settings.copy(useVehicleFilter = false, mapEnergyMode = EnergyFilterMode.Fuel, selectedMapEnergyTypes = settings.vehicleGasTypes)
        } else {
            settings
        }
        val fuelIds = fuelSettings.effectiveMapEnergyFilterIds() - "electric"

        StationMapFilters.apply(
            settings = fuelSettings,
            pois = rawPois,
            providers = currentProviders,
            skipWhenOnlyOverpass = true
        )
            .filter { approxDistanceKm(baseLat, baseLon, it.latitude, it.longitude) <= 10.0 }
            .sortedWith { a, b ->
                val pricesA = if (fuelIds.isEmpty()) a.fuelPrices else a.fuelPrices?.filter { MapPoiFilter.fuelNameToId(it.fuelName) in fuelIds }
                val pricesB = if (fuelIds.isEmpty()) b.fuelPrices else b.fuelPrices?.filter { MapPoiFilter.fuelNameToId(it.fuelName) in fuelIds }

                val priceA = pricesA?.minByOrNull { it.price }?.price ?: Double.MAX_VALUE
                val priceB = pricesB?.minByOrNull { it.price }?.price ?: Double.MAX_VALUE

                if (priceA != priceB && (priceA != Double.MAX_VALUE || priceB != Double.MAX_VALUE)) {
                    priceA.compareTo(priceB)
                } else {
                    val distA = approxDistanceKm(baseLat, baseLon, a.latitude, a.longitude)
                    val distB = approxDistanceKm(baseLat, baseLon, b.latitude, b.longitude)
                    distA.compareTo(distB)
                }
            }
            .take(5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nearbyElectricPois: StateFlow<List<Poi>> = combine(
        userLat,
        userLon,
        rawNearbyPois,
        settingsManager.settings
    ) { baseLat, baseLon, rawPois, settings ->
        if (baseLat == null || baseLon == null || rawPois.isEmpty()) {
            return@combine emptyList<Poi>()
        }
        val currentProviders = settings.effectiveProvidersAt(baseLat, baseLon)

        val electricSettings = if (settings.useVehicleFilter && settings.vehicleEnergy == "hybrid") {
            settings.copy(useVehicleFilter = false, mapEnergyMode = EnergyFilterMode.Electric)
        } else {
            settings
        }

        StationMapFilters.apply(
            settings = electricSettings,
            pois = rawPois,
            providers = currentProviders,
            skipWhenOnlyOverpass = true
        )
            .filter { approxDistanceKm(baseLat, baseLon, it.latitude, it.longitude) <= 10.0 }
            .sortedBy { approxDistanceKm(baseLat, baseLon, it.latitude, it.longitude) }
            .take(5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<PhoneDashboardUiState> = combine(
        combine(favoriteIds, rawNearbyPois, isLoadingPois, searchError) { favs, raw, loading, err ->
            Pair(Pair(favs, raw), Pair(loading, err))
        },
        combine(userLat, userLon, showLoaderByDelay) { lat, lon, delay ->
            Pair(Pair(lat, lon), delay)
        },
        combine(nearbyFuelPois, nearbyElectricPois) { fuelPois, electricPois ->
            Pair(fuelPois, electricPois)
        }
    ) { part1, part2, part3 ->
        PhoneDashboardUiState(
            favoriteIds = part1.first.first,
            rawNearbyPois = part1.first.second,
            isLoadingPois = part1.second.first,
            searchError = part1.second.second,
            userLat = part2.first.first,
            userLon = part2.first.second,
            showLoaderByDelay = part2.second,
            nearbyFuelPois = part3.first,
            nearbyElectricPois = part3.second
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PhoneDashboardUiState())

    // Public inputs for dynamic context
    fun setPoiProvider(provider: PoiProvider?) {
        poiProviderFlow.value = provider
    }

    fun setFavoritesRepo(repo: FavoritesRepository?) {
        favoritesRepoFlow.value = repo
    }

    fun setLocationPermission(hasPermission: Boolean) {
        hasLocationPermissionFlow.value = hasPermission
    }

    fun setSelectedSearchLocation(location: GeocodedPlace?) {
        selectedSearchLocationFlow.value = location
    }

    fun refresh() {
        viewModelScope.launch {
            poiProviderFlow.value?.clearCache()
            // Clear rawNearbyPois so we get the loader/shimmer/full-fetch correctly
            rawNearbyPois.value = emptyList()
            refreshTick.value++
        }
    }

    fun toggleFavorite(poi: Poi) {
        val repo = favoritesRepoFlow.value ?: return
        viewModelScope.launch {
            repo.toggleFavorite(poi)
            // Immediately update the ID cache
            favoriteIds.value = repo.getFavorites().map { it.id }.toSet()
        }
    }
}
