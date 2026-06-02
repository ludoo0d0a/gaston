package fr.geoking.gaston.auto

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.belib.BorneAvailabilityProviderFactory
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.api.belib.matchAvailabilityToPois
import fr.geoking.gaston.effectiveIrvePowerLevels
import fr.geoking.gaston.effectiveMapEnergyFilterIds
import fr.geoking.gaston.feature.location.LocationHelper
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.PoiSearchRequest
import kotlinx.coroutines.launch

/**
 * Screen allowing users to search for POIs (fuel or electric stations) by name, brand or type.
 */
class AutoPoiSearchScreen(
    carContext: CarContext,
    private val poiProvider: PoiProvider,
    private val settingsManager: SettingsManager,
    private val availabilityProviderFactory: BorneAvailabilityProviderFactory
) : Screen(carContext) {

    private var searchText = ""
    private var allPois: List<Poi> = emptyList()
    private var isLoadingPois = false
    private var searchLat: Double = settingsManager.settings.value.lastKnownLat ?: 48.8566
    private var searchLon: Double = settingsManager.settings.value.lastKnownLon ?: 2.3522
    private var availabilityByPoiId: Map<String, StationAvailabilitySummary> = emptyMap()

    init {
        loadNearbyPois()
    }

    private fun loadNearbyPois() {
        lifecycleScope.launch {
            isLoadingPois = true
            invalidate()

            val (lat, lon) = LocationHelper.getInitialLocation(carContext, settingsManager)
            searchLat = lat
            searchLon = lon

            try {
                val result = poiProvider.searchResult(
                    PoiSearchRequest(searchLat, searchLon, null, emptySet(), skipFilters = true)
                )
                allPois = result.pois

                val provider = availabilityProviderFactory.getProvider(searchLat, searchLon)
                if (provider != null) {
                    try {
                        val availabilities = provider.getAvailability(searchLat, searchLon, 10)
                        availabilityByPoiId = matchAvailabilityToPois(availabilities, allPois)
                    } catch (e: Exception) {
                        availabilityByPoiId = emptyMap()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load POIs for search", e)
            } finally {
                isLoadingPois = false
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoPoiSearchScreen", "SearchTemplate") {
        val filteredPois = if (searchText.isBlank()) {
            emptyList()
        } else {
            filterPois(searchText)
        }

        val itemListBuilder = ItemList.Builder()
            .setNoItemsMessage(if (isLoadingPois) "Loading nearby stations..." else "No matching stations found")

        val currentSettings = settingsManager.settings.value
        val effectiveEnergies = currentSettings.effectiveMapEnergyFilterIds()
        val effectivePowerLevels = currentSettings.effectiveIrvePowerLevels()

        filteredPois.take(6).forEach { poi ->
            val availability = availabilityByPoiId[poi.id]
            itemListBuilder.addItem(
                AutoPoiUiHelper.buildPoiRow(
                    carContext = carContext,
                    poi = poi,
                    availability = availability,
                    effectiveEnergyTypes = effectiveEnergies,
                    effectivePowerLevels = effectivePowerLevels,
                    distanceFromLatLon = searchLat to searchLon,
                    includePlace = false
                ) {
                    screenManager.push(
                        PoiDetailScreen(
                            carContext = carContext,
                            poi = poi,
                            settingsManager = settingsManager,
                            availabilitySummary = availability,
                            effectiveEnergyTypes = effectiveEnergies,
                            effectivePowerLevels = effectivePowerLevels,
                            rating = null
                        )
                    )
                }
            )
        }

        SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(text: String) {
                searchText = text
                invalidate()
            }

            override fun onSearchSubmitted(text: String) {
                searchText = text
                invalidate()
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Search brand, gas or electric...")
            .setItemList(itemListBuilder.build())
            .setLoading(isLoadingPois && searchText.isBlank())
            .build()
    }

    private fun filterPois(query: String): List<Poi> {
        val q = query.lowercase().trim()
        return allPois.filter { poi ->
            val nameMatch = poi.name.lowercase().contains(q)
            val brandMatch = poi.brand?.lowercase()?.contains(q) ?: false
            val siteNameMatch = poi.siteName?.lowercase()?.contains(q) ?: false

            val energyMatch = when {
                q.contains("electric") || q.contains("irve") || q.contains("borne") || q.contains("recharge") -> poi.isElectric
                q.contains("gas") || q.contains("fuel") || q.contains("essence") || q.contains("gazole") || q.contains("diesel") || q.contains("pumps") -> !poi.isElectric
                else -> false
            }

            nameMatch || brandMatch || siteNameMatch || energyMatch
        }
    }

    companion object {
        private const val TAG = "AutoPoiSearchScreen"
    }
}
