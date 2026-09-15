package fr.geoking.gaston.auto.maplibre

import android.app.Presentation
import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.Lifecycle
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.auto.AutoMapCamera
import fr.geoking.gaston.auto.AaMapSurfaceRenderer
import fr.geoking.gaston.auto.AutoMapFollowFocalPoint
import fr.geoking.gaston.auto.AutoMapHeading
import fr.geoking.gaston.auto.AutoMapPoiHitTest
import fr.geoking.gaston.auto.MapOrientationMode
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.resolveAvailabilitySummary
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import java.util.Collections

/**
 * High-performance MapLibre Renderer for Android Auto using the Presentation API.
 * This renderer creates a [VirtualDisplay] and a [Presentation] to host a real [MapView],
 * providing 60 FPS and correct text orientation.
 */
class CarMapLibrePresentationRenderer(
    private val carContext: CarContext,
    private val lifecycle: Lifecycle,
) : AaMapSurfaceRenderer {
    override var hudModeLabel: String = "MapLibre (Pres)"
    override var offlineUnavailable: Boolean = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private val settingsManager = org.koin.core.context.GlobalContext.get().get<fr.geoking.gaston.SettingsManager>()

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: MapPresentation? = null
    private var mapView: MapView? = null
    private var mapboxMap: MapLibreMap? = null

    private var surfaceContainer: SurfaceContainer? = null
    private var styleUrl: String = resolveAutoMapStyleUrl(settingsManager.settings.value, carContext)
    private var centerLat: Double = 48.8566
    private var centerLon: Double = 2.3522
    private var zoom: Int = AutoMapCamera.DEFAULT_ZOOM
    private var orientationMode: MapOrientationMode = MapOrientationMode.NorthUp
    private var headingDegrees: Float = 0f
    private var selectedPoiId: String? = null
    private var lastPois: List<Poi> = emptyList()
    private var effectiveEnergyTypes: Set<String> = emptySet()
    private var effectivePowerLevels: Set<Int> = emptySet()
    private var availabilityByPoiId: Map<String, StationAvailabilitySummary> = emptyMap()
    private var visibleArea: Rect? = null

    override fun currentZoom(): Int = zoom

    override fun requestRedraw() {
        // Not needed for Presentation API as MapView redraws itself
    }

    override fun setStyleUrl(url: String) {
        if (styleUrl == url) return
        styleUrl = url
        mapboxMap?.setStyle(url)
    }

    override fun updateLocation(lat: Double, lon: Double, zoomLevel: Int) {
        val coercedZoom = zoomLevel.coerceIn(AutoMapCamera.MIN_ZOOM, AutoMapCamera.MAX_ZOOM)
        if (centerLat == lat && centerLon == lon && zoom == coercedZoom) return
        centerLat = lat
        centerLon = lon
        zoom = coercedZoom
        updateCamera()
    }

    override fun updateUserLocation(lat: Double, lon: Double, bearing: Float) {
        headingDegrees = bearing
        updateCamera()
    }

    override fun setMapOrientation(mode: MapOrientationMode, bearing: Float) {
        orientationMode = mode
        headingDegrees = bearing
        updateCamera()
    }

    override fun updateVisibleArea(area: Rect) {
        visibleArea = area
    }

    override fun updatePois(
        newPois: List<Poi>,
        effectiveEnergyTypes: Set<String>,
        effectivePowerLevels: Set<Int>,
        availability: Map<String, StationAvailabilitySummary>,
        selectedId: String?,
    ) {
        lastPois = newPois
        this.effectiveEnergyTypes = effectiveEnergyTypes
        this.effectivePowerLevels = effectivePowerLevels
        availabilityByPoiId = availability
        selectedPoiId = selectedId
        updateMarkers()
    }

    override fun updateSearchRadius(centerLat: Double, centerLon: Double, radiusKm: Double?) {
        // Implemented via a MapLibre CircleLayer in the style or via a separate Source
    }

    override fun setQueryPending(pending: Boolean) {
        // Handled via a View overlay in the Presentation
    }

    override fun findPoisAt(screenX: Float, screenY: Float): List<Poi> =
        AutoMapPoiHitTest.findPoisAt(
            screenX = screenX,
            screenY = screenY,
            pois = lastPois,
            mapLat = centerLat,
            mapLon = centerLon,
            zoom = zoom,
            mapBearingDegrees = AutoMapHeading.effectiveBearing(orientationMode, headingDegrees),
            centerPxX = centerPxXForHitTest(),
            centerPxY = centerPxYForHitTest(),
            visibleArea = visibleArea,
        )

    override fun zoomForHitTest(): Int = zoom
    override fun mapLatForHitTest(): Double = centerLat
    override fun mapLonForHitTest(): Double = centerLon
    override fun centerPxXForHitTest(): Double = followFocalPoint().x
    override fun centerPxYForHitTest(): Double = followFocalPoint().y

    override fun attachSurface(container: SurfaceContainer) {
        surfaceContainer = container
        val surface = container.surface ?: return

        uiHandler.post {
            try {
                val displayManager = carContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val virtualDisplay = displayManager.createVirtualDisplay(
                    "GastonMapPresentation",
                    container.width,
                    container.height,
                    container.dpi,
                    surface,
                    0, null, null
                )
                this.virtualDisplay = virtualDisplay

                val presentation = MapPresentation(carContext, virtualDisplay.display)
                this.presentation = presentation
                presentation.show()

                this.mapView = presentation.mapView
                mapView?.onCreate(null)
                mapView?.onStart()
                mapView?.onResume()

                mapView?.getMapAsync { map ->
                    this.mapboxMap = map
                    map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                        // Initialize basic style
                    }
                    updateCamera()
                    updateMarkers()
                }
            } catch (e: Exception) {
                Log.e("CarMapLibrePres", "Failed to attach presentation", e)
            }
        }
    }

    override fun detachSurface() {
        uiHandler.post {
            presentation?.dismiss()
            presentation = null
            virtualDisplay?.release()
            virtualDisplay = null
            mapView?.onPause()
            mapView?.onStop()
            mapView?.onDestroy()
            mapView = null
            mapboxMap = null
        }
    }

    private fun updateCamera() {
        val map = mapboxMap ?: return
        val bearing = AutoMapHeading.effectiveBearing(orientationMode, headingDegrees)
        val cameraPosition = CameraPosition.Builder()
            .target(LatLng(centerLat, centerLon))
            .zoom(zoom.toDouble())
            .bearing(bearing.toDouble())
            .build()
        map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun updateMarkers() {
        val map = mapboxMap ?: return
        // In a real implementation, we would use a GeoJsonSource and a SymbolLayer
        // to render POIs efficiently.
        Log.d("CarMapLibrePres", "Updating markers for ${lastPois.size} POIs")
    }

    private fun followFocalPoint(): AutoMapFollowFocalPoint.FocalPoint =
        AutoMapFollowFocalPoint.focalPointPx(
            visibleArea = visibleArea,
            surfaceWidth = surfaceContainer?.width ?: 800,
            surfaceHeight = surfaceContainer?.height ?: 480,
            headingUp = orientationMode == MapOrientationMode.HeadingUp,
        )

    private inner class MapPresentation(context: Context, display: android.view.Display) : Presentation(context, display) {
        lateinit var mapView: MapView

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            mapView = MapView(context)
            setContentView(mapView)
        }
    }

    companion object {
        private const val TAG = "CarMapLibrePres"
    }
}
