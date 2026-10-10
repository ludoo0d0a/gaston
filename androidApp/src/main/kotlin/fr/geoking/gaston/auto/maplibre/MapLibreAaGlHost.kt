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
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.car.app.CarContext
import androidx.car.app.SurfaceContainer
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.api.weather.RainViewerMapsClient
import fr.geoking.gaston.auto.AutoMapCamera
import fr.geoking.gaston.auto.AutoMapFollowFocalPoint
import fr.geoking.gaston.auto.AutoMapHeading
import fr.geoking.gaston.auto.AutoMapPoiHitTest
import fr.geoking.gaston.auto.AutoSurfaceRenderer
import fr.geoking.gaston.auto.MapOrientationMode
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.radar.DangerZoneHudStore
import fr.geoking.gaston.ui.map.maplibre.MapLibreSharedHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Hosts a real MapLibre [MapView] on the Android Auto surface via [VirtualDisplay] + [Presentation].
 * MapLibre owns the GL/EGL context and presents into [SurfaceContainer.surface] (fluid vector path).
 */
internal class MapLibreAaGlHost(
    private val carContext: CarContext,
    private val logTag: String,
) {
    private val uiHandler = Handler(Looper.getMainLooper())

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: MapPresentation? = null
    private var mapView: MapView? = null
    private var mapLibreMap: MapLibreMap? = null
    private var presenceBadge: DangerZonePresenceBadgeView? = null
    private var surfaceContainer: SurfaceContainer? = null

    var styleUrl: String = ""
        private set
    var centerLat: Double = 48.8566
        private set
    var centerLon: Double = 2.3522
        private set
    var zoom: Int = AutoMapCamera.DEFAULT_ZOOM
        private set
    var orientationMode: MapOrientationMode = MapOrientationMode.NorthUp
        private set
    var headingDegrees: Float = 0f
        private set
    var userLat: Double? = null
        private set
    var userLon: Double? = null
        private set
    var userHeading: Float = 0f
        private set

    private var selectedPoiId: String? = null
    private var lastPois: List<Poi> = emptyList()
    private var effectiveEnergyTypes: Set<String> = emptySet()
    private var effectivePowerLevels: Set<Int> = emptySet()
    private var availabilityByPoiId: Map<String, StationAvailabilitySummary> = emptyMap()
    private var visibleArea: Rect? = null
    private var searchRadiusCenterLat: Double? = null
    private var searchRadiusCenterLon: Double? = null
    private var searchRadiusKm: Double? = null
    private var styleReady: Boolean = false
    private var rainViewerTileUrl: String? = null
    private val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun setStyleUrl(url: String) {
        if (styleUrl == url) return
        styleUrl = url
        styleReady = false
        uiHandler.post {
            mapLibreMap?.setStyle(Style.Builder().fromUri(url)) { onStyleLoaded() }
        }
    }

    fun updateLocation(lat: Double, lon: Double, zoomLevel: Int) {
        val coercedZoom = zoomLevel.coerceIn(AutoMapCamera.MIN_ZOOM, AutoMapCamera.MAX_ZOOM)
        if (centerLat == lat && centerLon == lon && zoom == coercedZoom) return
        centerLat = lat
        centerLon = lon
        zoom = coercedZoom
        uiHandler.post { updateCamera() }
    }

    fun updateUserLocation(lat: Double, lon: Double, bearing: Float) {
        userLat = lat
        userLon = lon
        userHeading = bearing
        headingDegrees = bearing
        uiHandler.post {
            updateCamera()
            syncUser()
        }
    }

    fun setMapOrientation(mode: MapOrientationMode, bearing: Float) {
        orientationMode = mode
        headingDegrees = bearing
        uiHandler.post { updateCamera() }
    }

    fun updateVisibleArea(area: Rect) {
        visibleArea = area
        presenceBadge?.setVisibleArea(area)
    }

    fun updatePois(
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
        uiHandler.post { syncPois() }
    }

    fun updateSearchRadius(centerLat: Double, centerLon: Double, radiusKm: Double?) {
        searchRadiusCenterLat = centerLat
        searchRadiusCenterLon = centerLon
        searchRadiusKm = radiusKm
        uiHandler.post { syncSearchRadius() }
    }

    fun findPoisAt(screenX: Float, screenY: Float): List<Poi> =
        AutoMapPoiHitTest.findPoisAt(
            screenX = screenX,
            screenY = screenY,
            pois = lastPois,
            mapLat = centerLat,
            mapLon = centerLon,
            zoom = zoom,
            mapBearingDegrees = AutoMapHeading.effectiveBearing(orientationMode, headingDegrees),
            centerPxX = centerPxX(),
            centerPxY = centerPxY(),
            visibleArea = visibleArea,
        )

    fun centerPxX(): Double = followFocalPoint().x
    fun centerPxY(): Double = followFocalPoint().y

    fun attachSurface(container: SurfaceContainer) {
        surfaceContainer = container
        val surface = container.surface
        if (surface == null || !surface.isValid) {
            Log.w(logTag, "attachSurface: invalid surface")
            return
        }
        uiHandler.post {
            try {
                detachSurfaceInternal()
                val displayManager = carContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val vd = displayManager.createVirtualDisplay(
                    "GastonMapLibreGl-$logTag",
                    container.width.coerceAtLeast(1),
                    container.height.coerceAtLeast(1),
                    container.dpi.coerceAtLeast(160),
                    surface,
                    0,
                )
                virtualDisplay = vd
                val pres = MapPresentation(carContext, vd.display)
                presentation = pres
                pres.show()
                mapView = pres.mapView
                presenceBadge = pres.presenceBadge
                bindPresenceBadge(pres.presenceBadge)
                mapView?.onCreate(null)
                mapView?.onStart()
                mapView?.onResume()
                val url = styleUrl.ifBlank {
                    resolveAutoMapStyleUrl(
                        org.koin.core.context.GlobalContext.get().get<fr.geoking.gaston.SettingsManager>().settings.value,
                        carContext,
                    )
                }.also { styleUrl = it }
                mapView?.getMapAsync { map ->
                    mapLibreMap = map
                    map.uiSettings.setAllGesturesEnabled(false)
                    map.setStyle(Style.Builder().fromUri(url)) { onStyleLoaded() }
                    updateCamera()
                }
                Log.i(logTag, "VirtualDisplay MapView attached ${container.width}x${container.height} style=$url")
            } catch (e: Exception) {
                Log.e(logTag, "Failed to attach VirtualDisplay MapView", e)
                detachSurfaceInternal()
            }
        }
    }

    fun detachSurface() {
        uiHandler.post { detachSurfaceInternal() }
    }

    private fun detachSurfaceInternal() {
        try {
            presentation?.dismiss()
        } catch (e: Exception) {
            Log.w(logTag, "presentation dismiss failed", e)
        }
        presentation = null
        presenceBadge = null
        try {
            mapView?.onPause()
            mapView?.onStop()
            mapView?.onDestroy()
        } catch (e: Exception) {
            Log.w(logTag, "mapView teardown failed", e)
        }
        mapView = null
        mapLibreMap = null
        styleReady = false
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.w(logTag, "virtualDisplay release failed", e)
        }
        virtualDisplay = null
    }

    private fun onStyleLoaded() {
        styleReady = true
        val map = mapLibreMap ?: return
        MapLibreSharedHelper.initPoiLayer(map)
        MapLibreSharedHelper.initUserLocationLayer(carContext, map)
        syncPois()
        syncSearchRadius()
        syncUser()
        updateCamera()
    }

    private fun updateCamera() {
        val map = mapLibreMap ?: return
        val bearing = AutoMapHeading.effectiveBearing(orientationMode, headingDegrees)
        val cameraPosition = CameraPosition.Builder()
            .target(LatLng(centerLat, centerLon))
            .zoom(zoom.toDouble())
            .bearing(bearing.toDouble())
            .build()
        map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun syncPois() {
        val map = mapLibreMap ?: return
        if (!styleReady) return
        val weatherActive = lastPois.any { it.poiCategory == PoiCategory.Weather }
        if (weatherActive && rainViewerTileUrl == null) {
            hostScope.launch {
                val url = runCatching {
                    GlobalContext.getOrNull()?.get<RainViewerMapsClient>()?.latestRadarFrame()?.tileUrlTemplate
                }.getOrNull()
                if (url != null) {
                    rainViewerTileUrl = url
                    uiHandler.post { syncPois() }
                }
            }
        }
        MapLibreSharedHelper.syncRainViewerLayer(
            map = map,
            tileUrlTemplate = if (weatherActive) rainViewerTileUrl else null,
        )
        MapLibreSharedHelper.syncPoiLayer(
            context = carContext,
            map = map,
            pois = lastPois,
            selectedPoiId = selectedPoiId,
            availabilityByPoiId = availabilityByPoiId,
            effectiveEnergyTypes = effectiveEnergyTypes,
            effectivePowerLevels = effectivePowerLevels,
            sizeProvider = { _, isSelected ->
                val base = AutoSurfaceRenderer.POI_MARKER_WIDTH_PX
                if (isSelected) base + 8 else base
            },
        )
    }

    private fun syncSearchRadius() {
        val map = mapLibreMap ?: return
        if (!styleReady) return
        MapLibreSharedHelper.syncSearchRadiusLayer(
            map = map,
            centerLat = searchRadiusCenterLat,
            centerLon = searchRadiusCenterLon,
            radiusKm = searchRadiusKm,
        )
    }

    private fun syncUser() {
        val map = mapLibreMap ?: return
        if (!styleReady) return
        MapLibreSharedHelper.syncUserLocationLayer(
            context = carContext,
            map = map,
            userLat = userLat,
            userLon = userLon,
            userHeading = userHeading,
        )
    }

    private fun followFocalPoint(): AutoMapFollowFocalPoint.FocalPoint =
        AutoMapFollowFocalPoint.focalPointPx(
            visibleArea = visibleArea,
            surfaceWidth = surfaceContainer?.width ?: 800,
            surfaceHeight = surfaceContainer?.height ?: 480,
            headingUp = orientationMode == MapOrientationMode.HeadingUp,
        )

    private fun bindPresenceBadge(badge: DangerZonePresenceBadgeView) {
        badge.setVisibleArea(visibleArea)
        try {
            val store = org.koin.core.context.GlobalContext.getOrNull()
                ?.get<DangerZoneHudStore>()
            if (store != null) {
                badge.bindHudStore(store)
            }
        } catch (e: Exception) {
            Log.w(logTag, "Failed to bind danger-zone HUD store", e)
        }
    }

    private inner class MapPresentation(
        context: Context,
        display: Display,
    ) : Presentation(context, display) {
        lateinit var mapView: MapView
        lateinit var presenceBadge: DangerZonePresenceBadgeView

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val root = FrameLayout(context)
            mapView = MapView(context)
            presenceBadge = DangerZonePresenceBadgeView(context).apply {
                visibility = View.GONE
            }
            root.addView(
                mapView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            root.addView(
                presenceBadge,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            setContentView(root)
        }
    }
}
