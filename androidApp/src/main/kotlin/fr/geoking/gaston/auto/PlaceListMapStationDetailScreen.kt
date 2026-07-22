package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.poi.Poi
import kotlinx.coroutines.launch

/**
 * Level-2 station detail for [NativeMapPoiScreen].
 *
 * Pushed on the screen stack so [Action.BACK] pops back to the station list (host template step
 * matches a real screen push). In-place detail inside [PlaceListMapTemplate] breaks back navigation
 * when list rows are browsable.
 */
class PlaceListMapStationDetailScreen(
    carContext: CarContext,
    private val poi: Poi,
    private val availability: StationAvailabilitySummary?,
    private val searchLat: Double,
    private val searchLon: Double,
    private val effectiveEnergies: Set<String>,
    private val effectivePowerLevels: Set<Int>,
    private val favoritesRepo: FavoritesRepository? = null,
) : Screen(carContext) {

    private var isFavorite: Boolean = false

    init {
        lifecycleScope.launch {
            isFavorite = favoritesRepo?.isFavorite(poi.id) == true
            invalidate()
        }
    }

    private fun toggleFavorite() {
        val repo = favoritesRepo ?: return
        lifecycleScope.launch {
            isFavorite = repo.toggleFavorite(poi)
            invalidate()
        }
    }

    override fun onGetTemplate(): Template = safeCarTemplate(
        carContext = carContext,
        logTag = "PlaceListMapStationDetailScreen",
        templateName = "PlaceListMapTemplate",
    ) {
        val listLimit = try {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } catch (_: Exception) {
            6
        }

        val detailRows = AutoPoiUiHelper.buildPoiDetailRows(
            carContext = carContext,
            poi = poi,
            availability = availability,
            effectiveEnergyTypes = effectiveEnergies,
            effectivePowerLevels = effectivePowerLevels,
            distanceFromLatLon = searchLat to searchLon,
            maxRows = listLimit,
            includePlace = true,
            onHeaderClick = null,
        )

        val itemListBuilder = ItemList.Builder()
        detailRows.forEach { itemListBuilder.addItem(it) }

        val actionStripBuilder = ActionStrip.Builder()
            .addAction(carContext.navigateToStationAction(poi))
        if (favoritesRepo != null) {
            actionStripBuilder.addAction(
                carContext.favoriteStationAction(isFavorite) { toggleFavorite() }
            )
        }
        val actionStrip = actionStripBuilder.build()

        val anchorPlace = Place.Builder(CarLocation.create(poi.latitude, poi.longitude))
            .setMarker(PlaceMarker.Builder().setColor(CarColor.RED).build())
            .build()

        PlaceListMapTemplate.Builder()
            .setTitle(AutoPoiUiHelper.poiDetailTitle(poi))
            .setHeaderAction(Action.BACK)
            .setActionStrip(actionStrip)
            .setLoading(false)
            .setItemList(itemListBuilder.build())
            .setAnchor(anchorPlace)
            .build()
    }
}
