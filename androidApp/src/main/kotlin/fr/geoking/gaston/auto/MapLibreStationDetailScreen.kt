package fr.geoking.gaston.auto

import android.graphics.Rect
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.auto.maplibre.CarMapLibreRenderer
import fr.geoking.gaston.auto.maplibre.resolveAutoMapStyleUrl
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.poi.Poi
import kotlinx.coroutines.launch

/**
 * Level-2 station detail for [MapLibrePoiScreen].
 *
 * Pushed on the screen stack so [Action.BACK] pops back to the station list (host template step
 * matches a real screen push). In-place detail inside [MapWithContentTemplate] breaks back
 * navigation while driving.
 */
class MapLibreStationDetailScreen(
    carContext: CarContext,
    private val poi: Poi,
    private val availability: StationAvailabilitySummary?,
    private val searchLat: Double,
    private val searchLon: Double,
    private val zoom: Int,
    private val orientationMode: MapOrientationMode,
    private val bearing: Float,
    private val effectiveEnergies: Set<String>,
    private val effectivePowerLevels: Set<Int>,
    private val settingsManager: SettingsManager,
    private val favoritesRepo: FavoritesRepository? = null,
    private val onDisposed: (() -> Unit)? = null,
) : Screen(carContext), SurfaceCallback, DefaultLifecycleObserver {

    private val mapRenderer: CarMapLibreRenderer = CarMapLibreRenderer(carContext, lifecycle)
    private var isFavorite: Boolean = false

    init {
        lifecycle.addObserver(this)
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
        logTag = "MapLibreStationDetailScreen",
        templateName = "MapWithContentTemplate",
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
            includePlace = false,
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

        val contentTemplate = ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(AutoPoiUiHelper.poiDetailTitle(poi))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(itemListBuilder.build())
            .build()

        MapWithContentTemplate.Builder()
            .setContentTemplate(contentTemplate)
            .setActionStrip(actionStrip)
            .build()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        Log.d("MapLibreStationDetailScreen", "onSurfaceAvailable")
        mapRenderer.detachSurface()
        val surface = surfaceContainer.surface
        if (surface == null) {
            Log.w("MapLibreStationDetailScreen", "SurfaceContainer.surface is null; skipping renderer start")
            return
        }
        val settings = settingsManager.settings.value
        mapRenderer.setStyleUrl(resolveAutoMapStyleUrl(settings, carContext))
        mapRenderer.attachSurface(surfaceContainer)
        mapRenderer.updateLocation(poi.latitude, poi.longitude, zoom)
        mapRenderer.setMapOrientation(orientationMode, bearing)
        val availabilityMap = availability?.let { mapOf(poi.id to it) } ?: emptyMap()
        mapRenderer.updatePois(
            newPois = listOf(poi),
            effectiveEnergyTypes = effectiveEnergies,
            effectivePowerLevels = effectivePowerLevels,
            availability = availabilityMap,
            selectedId = poi.id,
        )
        mapRenderer.updateUserLocation(searchLat, searchLon, bearing)
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        mapRenderer.updateVisibleArea(visibleArea)
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.d("MapLibreStationDetailScreen", "onSurfaceDestroyed")
        mapRenderer.detachSurface()
    }

    override fun onStart(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        mapRenderer.detachSurface()
        onDisposed?.invoke()
    }
}
