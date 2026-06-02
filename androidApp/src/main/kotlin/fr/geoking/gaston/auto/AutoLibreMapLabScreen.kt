package fr.geoking.gaston.auto

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.constraints.ConstraintManager
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.R
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.ThemeMode
import fr.geoking.gaston.feature.location.LocationHelper
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import fr.geoking.gaston.auto.maplibre.resolveAutoRasterTileUrl

/**
 * Android Auto template lab: map surface + zoom controls for exercising the raster pipeline.
 * Phone VectorMapScreen uses MapLibre + OpenFreeMap; "Open MapLibre on phone" starts the host app with
 * `gaston://map/libremap` (switches engine to MapLibre and opens the map).
 */
class AutoLibreMapLabScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback, DefaultLifecycleObserver, KoinComponent {

    private val settingsManager: SettingsManager by inject()
    private var searchLat = 48.8566
    private var searchLon = 2.3522
    private var zoom = 12
    private var surfaceRenderer: AutoSurfaceRenderer? = null
    private var isLoading = true

    init {
        lifecycle.addObserver(this)
        refreshLocation()
    }

    private fun refreshLocation() {
        lifecycleScope.launch {
            isLoading = true
            invalidate()
            var lat = 48.8566
            var lon = 2.3522
            if (carContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                carContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                LocationHelper.getCurrentLocation(carContext)?.let {
                    lat = it.latitude
                    lon = it.longitude
                }
            }
            searchLat = lat
            searchLon = lon
            surfaceRenderer?.updateLocation(searchLat, searchLon, zoom)
            surfaceRenderer?.updateUserLocation(searchLat, searchLon)
            isLoading = false
            invalidate()
        }
    }

    private fun bumpZoom(delta: Int) {
        zoom = (zoom + delta).coerceIn(4, 18)
        surfaceRenderer?.updateLocation(searchLat, searchLon, zoom)
        invalidate()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        surfaceRenderer?.stop()
        val surface = surfaceContainer.surface
        if (surface == null) {
            Log.w("AutoLibreMapLabScreen", "SurfaceContainer.surface is null; skipping renderer start")
            surfaceRenderer = null
            return
        }
        if (surfaceContainer.width <= 0 || surfaceContainer.height <= 0) {
            Log.w("AutoLibreMapLabScreen", "Skipping map surface: invalid size ${surfaceContainer.width}x${surfaceContainer.height}")
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
            updateUserLocation(searchLat, searchLon)
            updatePois(emptyList(), emptySet(), emptySet())
            setTileUrlTemplate(resolveAutoRasterTileUrl(settingsManager.settings.value, isLab = true))
            start()
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
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

    override fun onGetTemplate(): Template = safeCarTemplate(carContext, "AutoLibreMapLabScreen") {
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_home))
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_home)).build())
                    .setOnClickListener { screenManager.popToRoot() }
                    .build()
            )
            .build()

        val title = "MapLibre (lab)"

        if (isLoading) {
            return@safeCarTemplate MapWithContentTemplate.Builder()
                .setContentTemplate(
                    ListTemplate.Builder()
                        .setLoading(true)
                        .setHeader(
                            Header.Builder()
                                .setTitle(title)
                                .setStartHeaderAction(Action.BACK)
                                .build()
                        )
                        .build()
                )
                .setActionStrip(actionStrip)
                .build()
        }

        surfaceRenderer?.updateLocation(searchLat, searchLon, zoom)

        val list = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.action_recenter))
                    .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_map)).build())
                    .setOnClickListener { refreshLocation() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.action_zoom_in))
                    .addText(carContext.getString(R.string.map_level, zoom))
                    .setOnClickListener { bumpZoom(1) }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.action_zoom_out))
                    .addText(carContext.getString(R.string.map_level, zoom))
                    .setOnClickListener { bumpZoom(-1) }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.map_open_maplibre_phone))
                    .addText(carContext.getString(R.string.map_vector_openfreemap))
                    .setOnClickListener {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("gaston://map/libremap")).apply {
                            setPackage(carContext.packageName)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        try {
                            carContext.startCarApp(intent)
                        } catch (e: Exception) {
                            Log.e("AutoLibreMapLabScreen", "startCarApp failed", e)
                        }
                    }
                    .build()
            )
            .build()

        val listTemplate = ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(title)
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(list)
            .build()

        MapWithContentTemplate.Builder()
            .setContentTemplate(listTemplate)
            .setActionStrip(actionStrip)
            .build()
    }
}
