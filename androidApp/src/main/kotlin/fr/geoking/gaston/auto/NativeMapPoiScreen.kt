package fr.geoking.gaston.auto

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.ConstraintManager
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Template
import androidx.car.app.model.PlaceListMapTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.community.CommunityPoiRepository
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.api.belib.matchAvailabilityToPois
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.shared.location.approxDistanceKm
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class NativeMapPoiScreen(
    carContext: CarContext,
    private val poiProvider: PoiProvider,
    private val availabilityProviderFactory: BorneAvailabilityProviderFactory,
    private val settingsManager: SettingsManager,
    private val communityRepo: CommunityPoiRepository? = null,
    private val favoritesRepo: FavoritesRepository? = null
) : Screen(carContext), DefaultLifecycleObserver {

    private var pois: List<Poi> = emptyList()
    private var availabilityByPoiId: Map<String, StationAvailabilitySummary> = emptyMap()
    private var favoriteIds: Set<String> = emptySet()
    private var isLoading = true
    private var searchLat: Double = settingsManager.settings.value.lastKnownLat ?: 48.8566
    private var searchLon: Double = settingsManager.settings.value.lastKnownLon ?: 2.3522
    private var sortByPrice: Boolean = false

    init {
        lifecycle.addObserver(this)
        lifecycleScope.launch {
            settingsManager.settings
                .map { s ->
                    s.selectedPoiProviders to s.selectedMapEnergyTypes
                }
                .distinctUntilChanged()
                .collectLatest {
                    loadPois()
                }
        }
    }

    private fun loadPois() {
        lifecycleScope.launch {
            isLoading = true
            invalidate()

            val (lat, lon) = LocationHelper.getInitialLocation(carContext, settingsManager)

            searchLat = lat
            searchLon = lon

            try {
                val result = poiProvider.searchResult(PoiSearchRequest(lat, lon, null, emptySet()))
                pois = result.pois
                favoriteIds = favoritesRepo?.getFavorites()?.map { it.id }?.toSet() ?: emptySet()
                val provider = availabilityProviderFactory.getProvider(lat, lon)
                if (provider != null) {
                    val availabilities = try {
                        provider.getAvailability(lat, lon, 10)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    availabilityByPoiId = matchAvailabilityToPois(availabilities, pois)
                }
            } catch (e: Exception) {
                Log.e("NativeMapPoiScreen", "loadPois failed", e)
            }
            isLoading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "NativeMapPoiScreen") {
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Home")
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_home)).build())
                    .setOnClickListener { screenManager.popToRoot() }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_settings)).build())
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
            .build()

        val anchorPlace = Place.Builder(CarLocation.create(searchLat, searchLon))
            .setMarker(PlaceMarker.Builder().setColor(CarColor.RED).build())
            .build()

        // PlaceListMapTemplate: loading and item list are mutually exclusive (see Builder.build()).
        if (isLoading) {
            return@safeCarTemplate PlaceListMapTemplate.Builder()
                .setTitle("Nearby Stations")
                .setHeaderAction(Action.BACK)
                .setActionStrip(actionStrip)
                .setLoading(true)
                .setAnchor(anchorPlace)
                .build()
        }

        val currentSettings = settingsManager.settings.value

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

        val sortedPois = if (sortByPrice) {
            val fuelIds = effectiveEnergies - "electric"
            if (fuelIds.isEmpty()) {
                pois.sortedBy { approxDistanceKm(searchLat, searchLon, it.latitude, it.longitude) }
            } else {
                pois.sortedWith { a, b ->
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
            pois.sortedBy { approxDistanceKm(searchLat, searchLon, it.latitude, it.longitude) }
        }

        sortedPois.take(listLimit).forEach { poi ->
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

        PlaceListMapTemplate.Builder()
            .setTitle("Nearby Stations")
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .setLoading(false)
            .setItemList(itemListBuilder.build())
            .setAnchor(anchorPlace)
            .build()
    }
}
