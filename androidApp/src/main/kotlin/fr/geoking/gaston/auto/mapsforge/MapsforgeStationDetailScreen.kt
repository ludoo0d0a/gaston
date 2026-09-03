package fr.geoking.gaston.auto.mapsforge

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
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.auto.AutoPoiUiHelper
import fr.geoking.gaston.auto.MapOrientationMode
import fr.geoking.gaston.auto.favoriteStationAction
import fr.geoking.gaston.auto.maplibre.resolveAutoRasterTileUrl
import fr.geoking.gaston.auto.navigateToStationAction
import fr.geoking.gaston.auto.safeCarTemplate
import fr.geoking.gaston.community.FavoritesRepository
import fr.geoking.gaston.poi.Poi
import kotlinx.coroutines.launch

/**
 * Level-2 station detail screen for [MapsforgePoiScreen].
 */
class MapsforgeStationDetailScreen(
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
    private val mapManager: MapsforgeMapManager,
    private val favoritesRepo: FavoritesRepository? = null,
    private val onDisposed: (() -> Unit)? = null,
) : Screen(carContext), SurfaceCallback, DefaultLifecycleObserver {

    private var surfaceRenderer: CarMapsforgeRenderer? = null
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
        logTag = "MapsforgeStationDetailScreen",
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
        Log.d("MapsforgeStationDetailScreen", "onSurfaceAvailable")
        surfaceRenderer?.stop()
        val surface = surfaceContainer.surface
        if (surface == null) {
            Log.w("MapsforgeStationDetailScreen", "SurfaceContainer.surface is null; skipping renderer start")
            surfaceRenderer = null
            return
        }
        surfaceRenderer = CarMapsforgeRenderer(
            context = carContext,
            surface = surface,
            width = surfaceContainer.width,
            height = surfaceContainer.height,
            mapManager = mapManager,
            initialLat = poi.latitude,
            initialLon = poi.longitude,
        ).apply {
            hudModeLabel = carContext.getString(R.string.map_mode_mapsforge)
            updateLocation(poi.latitude, poi.longitude, zoom)
            setMapOrientation(orientationMode, bearing)
            setTileUrlTemplate(resolveAutoRasterTileUrl(settingsManager.settings.value))
            setMapTileDebugEnabled(settingsManager.settings.value.mapTileDebugEnabled)
            updatePois(
                newPois = listOf(poi),
                effectiveEnergyTypes = effectiveEnergies,
                effectivePowerLevels = effectivePowerLevels,
                selectedId = poi.id,
                availability = availability?.let { mapOf(poi.id to it) } ?: emptyMap(),
            )
            updateUserLocation(searchLat, searchLon, bearing)
            start()
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        surfaceRenderer?.updateVisibleArea(visibleArea)
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.d("MapsforgeStationDetailScreen", "onSurfaceDestroyed")
        surfaceRenderer?.stop()
        surfaceRenderer = null
    }

    override fun onStart(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        surfaceRenderer?.stop()
        onDisposed?.invoke()
    }
}
