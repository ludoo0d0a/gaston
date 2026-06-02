package fr.geoking.gaston.auto

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
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import fr.geoking.gaston.R

import androidx.lifecycle.lifecycleScope
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.ThemeMode
import fr.geoking.gaston.feature.location.LocationHelper
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import fr.geoking.gaston.auto.maplibre.resolveAutoRasterTileUrl

class AutoMapTemplateScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback, DefaultLifecycleObserver, KoinComponent {

    private val settingsManager: SettingsManager by inject()
    private var surfaceRenderer: AutoSurfaceRenderer? = null
    private var lat = settingsManager.settings.value.lastKnownLat ?: 48.8566
    private var lon = settingsManager.settings.value.lastKnownLon ?: 2.3522
    private var zoom = 14

    init {
        lifecycle.addObserver(this)
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        surfaceRenderer?.stop()
        val surface = surfaceContainer.surface
        if (surface == null) {
            surfaceRenderer = null
            return
        }
        if (surfaceContainer.width <= 0 || surfaceContainer.height <= 0) {
            Log.w("AutoMapTemplateScreen", "Skipping map surface: invalid size ${surfaceContainer.width}x${surfaceContainer.height}")
            surfaceRenderer = null
            return
        }
        surfaceRenderer = AutoSurfaceRenderer(
            carContext,
            surface,
            surfaceContainer.width,
            surfaceContainer.height
        ).apply {
            updateLocation(lat, lon, zoom)
            updateUserLocation(lat, lon)
            setTileUrlTemplate(resolveAutoRasterTileUrl(settingsManager.settings.value))
            start()
        }

        lifecycleScope.launch {
            val (newLat, newLon) = LocationHelper.getInitialLocation(carContext, settingsManager)
            lat = newLat
            lon = newLon
            surfaceRenderer?.updateLocation(lat, lon, zoom)
            surfaceRenderer?.updateUserLocation(lat, lon)

        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        surfaceRenderer?.stop()
        surfaceRenderer = null
    }

    override fun onStart(owner: LifecycleOwner) {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        surfaceRenderer?.stop()
        surfaceRenderer = null
    }

    private fun bumpZoom(delta: Int) {
        zoom = (zoom + delta).coerceIn(4, 18)
        surfaceRenderer?.updateLocation(lat, lon, zoom)
        invalidate()
    }

    override fun onGetTemplate(): Template = safeCarTemplate(
        carContext = carContext,
        logTag = "AutoMapTemplateScreen",
        templateName = "MapWithContentTemplate"
    ) {
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_home))
                    .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_home)).build())
                    .setOnClickListener { screenManager.popToRoot() }
                    .build()
            )
            .build()

        val listBuilder = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.action_zoom_in_title))
                    .setOnClickListener { bumpZoom(1) }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.action_zoom_out_title))
                    .setOnClickListener { bumpZoom(-1) }
                    .build()
            )

        val contentTemplate = ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(carContext.getString(R.string.map_template_osm))
                    .setStartHeaderAction(Action.BACK)
                    .build()
            )
            .setSingleList(listBuilder.build())
            .build()

        MapWithContentTemplate.Builder()
            .setContentTemplate(contentTemplate)
            .setActionStrip(actionStrip)
            .build()
    }
}
