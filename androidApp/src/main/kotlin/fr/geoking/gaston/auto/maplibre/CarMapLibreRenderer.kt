package fr.geoking.gaston.auto.maplibre

import android.app.Presentation
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.Lifecycle
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.auto.AutoMapCamera
import fr.geoking.gaston.auto.AutoMapFollowFocalPoint
import fr.geoking.gaston.auto.AutoMapHeading
import fr.geoking.gaston.auto.MapOrientationMode
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.map.maplibre.MapLibreSharedHelper
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Presents MapLibre (OpenFreeMap vector styles) onto the Android Auto [SurfaceContainer]
 * via VirtualDisplay + [Presentation].
 *
 * MapView is created **per attach** and resumed only after [Presentation.show], matching the
 * MapLibre Android Auto sample (Spike B). Reusing a MapView across dismiss often yields a black screen.
 */
class CarMapLibreRenderer(
    private val carContext: CarContext,
    lifecycle: Lifecycle,
) {
    private val mapContainer = CarMapContainer(carContext, lifecycle)
    private val uiHandler = Handler(Looper.getMainLooper())
    private val settingsManager = org.koin.core.context.GlobalContext.get().get<fr.geoking.gaston.SettingsManager>()
    private val scaledStyles = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<org.maplibre.android.maps.Style, Boolean>())
    )

    private fun adjustTextSizes(style: org.maplibre.android.maps.Style) {
        if (!scaledStyles.add(style)) return
        Log.d(TAG, "Scaling texts of new style: ${styleUrl ?: "default"}")
        for (layer in style.layers) {
            if (layer is org.maplibre.android.style.layers.SymbolLayer && layer.id != MapLibreSharedHelper.POI_LAYER_ID) {
                val prop = layer.textSize
                if (prop.isExpression) {
                    val expr = prop.expression
                    try {
                        val arrayRepr = expr?.toArray()
                        if (arrayRepr != null) {
                            val scaledArrayRepr = scaleExpressionArray(arrayRepr, 1.4f)
                            val jsonElement = toJsonElement(scaledArrayRepr)
                            if (jsonElement is JsonArray) {
                                val newExpr = org.maplibre.android.style.expressions.Expression.Converter.convert(jsonElement)
                                if (newExpr != null) {
                                    layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.textSize(newExpr))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to scale text size expression for layer ${layer.id}", e)
                    }
                } else if (prop.isValue) {
                    val value = prop.value
                    if (value is Number) {
                        layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.textSize(value.toFloat() * 1.4f))
                    }
                }
            }
        }
    }

    private var surfaceContainer: SurfaceContainer? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var styleUrl: String? = null
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
    private var searchRadiusCenterLat: Double? = null
    private var searchRadiusCenterLon: Double? = null
    private var searchRadiusKm: Double? = null
    private var visibleArea: Rect? = null
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    private var surfaceDpi: Int = 0
    private var queryPending: Boolean = false

    private var debugPhase: String = "idle"
    private var debugStyleStatus: String = "—"
    private var debugLastError: String? = null
    private var debugFrameCount: Int = 0
    private var firstFrameLogged: Boolean = false

    private val loaderAnimRunnable = object : Runnable {
        override fun run() {
            if (!queryPending || presentation == null) return
            mapContainer.overlayView?.invalidate()
            uiHandler.postDelayed(this, 50L)
        }
    }

    val map: MapLibreMap?
        get() = mapContainer.mapLibreMapInstance

    init {
        mapContainer.onMapReady = { map ->
            debugPhase = "map_ready"
            applyCamera(map)
            syncPoiLayer()
            syncSearchRadiusLayer()
            syncOverlay()
            Log.i(TAG, "onMapReady camera=${centerLat},${centerLon} z=$zoom")
        }
        mapContainer.onStyleLoaded = { url ->
            debugStyleStatus = "ok"
            debugLastError = null
            debugPhase = "style_ok"
            mapContainer.mapLibreMapInstance?.getStyle { style ->
                adjustTextSizes(style)
            }
            syncPoiLayer()
            syncSearchRadiusLayer()
            syncOverlay()
            Log.i(TAG, "style loaded: $url")
        }
        mapContainer.onMapFailLoading = { message ->
            debugStyleStatus = "fail"
            debugLastError = message
            debugPhase = "style_fail"
            syncOverlay()
            Log.e(TAG, "style/map load failed: $message")
        }
    }

    fun setStyleUrl(url: String) {
        if (styleUrl == url) {
            Log.d(TAG, "setStyleUrl unchanged: $url")
            return
        }
        Log.i(TAG, "setStyleUrl: $url")
        styleUrl = url
        debugStyleStatus = "loading"
        debugPhase = "style_loading"
        mapContainer.setStyleUrl(url)
        syncOverlay()
    }

    fun updateLocation(lat: Double, lon: Double, zoomLevel: Int) {
        centerLat = lat
        centerLon = lon
        zoom = zoomLevel.coerceIn(AutoMapCamera.MIN_ZOOM, AutoMapCamera.MAX_ZOOM)
        mapContainer.mapLibreMapInstance?.let { applyCamera(it) }
        syncOverlay()
    }

    fun updateUserLocation(lat: Double, lon: Double, bearing: Float) {
        headingDegrees = bearing
        mapContainer.mapLibreMapInstance?.let { applyCamera(it) }
        syncOverlay()
    }

    fun setMapOrientation(mode: MapOrientationMode, bearing: Float = headingDegrees) {
        orientationMode = mode
        headingDegrees = bearing
        mapContainer.mapLibreMapInstance?.let { applyCamera(it) }
        syncOverlay()
    }

    fun updateVisibleArea(area: Rect) {
        if (visibleArea?.equals(area) == true) return
        visibleArea = Rect(area)
        mapContainer.mapLibreMapInstance?.let { applyCamera(it) }
        syncOverlay()
    }

    fun bumpZoom(delta: Int) {
        zoom = (zoom + delta).coerceIn(AutoMapCamera.MIN_ZOOM, AutoMapCamera.MAX_ZOOM)
        mapContainer.mapLibreMapInstance?.let { map ->
            map.animateCamera(CameraUpdateFactory.zoomTo(zoom.toDouble()))
        }
        syncOverlay()
    }

    fun updatePois(
        newPois: List<Poi>,
        effectiveEnergyTypes: Set<String>,
        effectivePowerLevels: Set<Int>,
        availability: Map<String, StationAvailabilitySummary> = availabilityByPoiId,
        selectedId: String? = selectedPoiId
    ) {
        lastPois = newPois
        this.effectiveEnergyTypes = effectiveEnergyTypes
        this.effectivePowerLevels = effectivePowerLevels
        availabilityByPoiId = availability
        selectedPoiId = selectedId
        syncPoiLayer()
    }

    /**
     * Draws a red stroke circle for the nearby station search boundary.
     * Pass [radiusKm] null to hide.
     */
    fun updateSearchRadius(centerLat: Double, centerLon: Double, radiusKm: Double?) {
        if (searchRadiusCenterLat == centerLat &&
            searchRadiusCenterLon == centerLon &&
            searchRadiusKm == radiusKm
        ) {
            return
        }
        searchRadiusCenterLat = centerLat
        searchRadiusCenterLon = centerLon
        searchRadiusKm = radiusKm
        syncSearchRadiusLayer()
    }

    /** Shows a small spinner overlay while a POI query is in flight. */
    fun setQueryPending(pending: Boolean) {
        if (queryPending == pending) return
        queryPending = pending
        uiHandler.removeCallbacks(loaderAnimRunnable)
        syncOverlay()
        if (pending) {
            uiHandler.post(loaderAnimRunnable)
        }
    }

    fun findPoisAt(screenX: Float, screenY: Float): List<Poi> {
        val map = mapContainer.mapLibreMapInstance ?: return emptyList()
        val tolerance = 32f // 32 pixels tolerance in all directions for easier tap targets on AA screens
        val rect = RectF(
            screenX - tolerance,
            screenY - tolerance,
            screenX + tolerance,
            screenY + tolerance
        )
        val features = map.queryRenderedFeatures(rect, MapLibreSharedHelper.POI_LAYER_ID)
        val ids = features.mapNotNull { it.getStringProperty(MapLibreSharedHelper.POI_ID_PROPERTY) }.toSet()
        val matchedPois = lastPois.filter { it.id in ids }

        // Sort matching POIs nearest-first by their screen-space distance to the touch point
        return matchedPois.sortedBy { poi ->
            val screenPos = map.projection.toScreenLocation(LatLng(poi.latitude, poi.longitude))
            val dx = screenX - screenPos.x
            val dy = screenY - screenPos.y
            dx * dx + dy * dy
        }
    }

    fun zoomForHitTest(): Int = zoom

    fun mapLatForHitTest(): Double = centerLat

    fun mapLonForHitTest(): Double = centerLon

    fun centerPxXForHitTest(): Double = followFocalPoint().x

    fun centerPxYForHitTest(): Double = followFocalPoint().y

    fun attachSurface(container: SurfaceContainer) {
        val surface = container.surface
        if (surface == null) {
            Log.w(TAG, "attachSurface: SurfaceContainer.surface is null")
            debugPhase = "no_surface"
            syncOverlay()
            return
        }
        if (container.width <= 0 || container.height <= 0) {
            Log.w(TAG, "attachSurface: invalid size ${container.width}x${container.height}")
            debugPhase = "bad_size"
            syncOverlay()
            return
        }

        val url = styleUrl ?: DEFAULT_OPENFREEMAP_STYLE.also {
            Log.w(TAG, "attachSurface: styleUrl null, using default $it")
            styleUrl = it
        }

        detachPresentation()

        surfaceContainer = container
        surfaceWidth = container.width
        surfaceHeight = container.height
        surfaceDpi = container.dpi.coerceAtLeast(1)
        debugPhase = "attaching"
        debugStyleStatus = "loading"
        debugLastError = null
        debugFrameCount = 0
        firstFrameLogged = false

        Log.i(
            TAG,
            "attachSurface Presentation: ${container.width}x${container.height} dpi=$surfaceDpi style=$url",
        )

        try {
            val displayManager = carContext.getSystemService(DisplayManager::class.java)
            val vd = displayManager.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                container.width,
                container.height,
                surfaceDpi,
                surface,
                0, // match MapLibre AA sample
            )
            virtualDisplay = vd
            Log.i(TAG, "VirtualDisplay created displayId=${vd.display.displayId}")

            val content = mapContainer.buildContent(url)
            val pres = Presentation(carContext, vd.display)
            presentation = pres
            pres.setContentView(content)
            pres.show()
            Log.i(TAG, "Presentation.show() ok isShowing=${pres.isShowing}")

            mapContainer.resumeAfterPresented()
            wireFirstFrameLogger()
            debugPhase = "presented"
            mapContainer.mapLibreMapInstance?.let { applyCamera(it) }
            syncOverlay()
            syncPoiLayer()
            syncSearchRadiusLayer()
        } catch (e: Exception) {
            debugPhase = "attach_fail"
            debugLastError = e.message
            syncOverlay()
            Log.e(TAG, "attachSurface failed", e)
            detachPresentation()
        }
    }

    fun detachSurface() {
        uiHandler.removeCallbacks(loaderAnimRunnable)
        debugPhase = "detached"
        detachPresentation()
        surfaceContainer = null
        Log.i(TAG, "detachSurface done")
    }

    private fun detachPresentation() {
        mapContainer.tearDown()
        try {
            presentation?.dismiss()
        } catch (e: Exception) {
            Log.w(TAG, "presentation.dismiss failed", e)
        }
        presentation = null
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.w(TAG, "virtualDisplay.release failed", e)
        }
        virtualDisplay = null
    }

    private fun wireFirstFrameLogger() {
        val mapView = mapContainer.mapViewInstance ?: return
        mapView.addOnDidFinishRenderingFrameListener { fully, _, _ ->
            debugFrameCount++
            if (!firstFrameLogged && fully) {
                firstFrameLogged = true
                debugPhase = "first_frame"
                Log.i(TAG, "first fully rendered frame (OpenFreeMap vector)")
                syncOverlay()
            } else if (debugFrameCount % 60 == 0) {
                syncOverlay()
            }
        }
    }

    private fun followFocalPoint(): AutoMapFollowFocalPoint.FocalPoint =
        AutoMapFollowFocalPoint.focalPointPx(
            visibleArea = visibleArea,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            headingUp = orientationMode == MapOrientationMode.HeadingUp,
        )

    fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        mapContainer.onScale(focusX, focusY, scaleFactor)
    }

    fun scrollBy(distanceX: Float, distanceY: Float) {
        mapContainer.scrollBy(distanceX, distanceY)
    }

    private fun syncOverlay() {
        val overlay = mapContainer.overlayView ?: return
        overlay.visibleArea = visibleArea
        overlay.surfaceWidth = surfaceWidth
        overlay.surfaceHeight = surfaceHeight
        overlay.bearing = AutoMapHeading.effectiveBearing(orientationMode, headingDegrees)
        overlay.zoom = zoom.toFloat()
        overlay.latitude = centerLat
        overlay.mapTileDebugEnabled = settingsManager.settings.value.mapTileDebugEnabled
        overlay.queryPending = queryPending
        overlay.debugLines = buildDebugLines()
        overlay.invalidate()
    }

    private fun buildDebugLines(): List<String> {
        val styleShort = styleUrl
            ?.removePrefix("https://tiles.openfreemap.org/styles/")
            ?.let { "ofm/$it" }
            ?: "style=?"
        return listOf(
            "MLAA phase=$debugPhase",
            "surf=${surfaceWidth}x${surfaceHeight}@${surfaceDpi}",
            "style=$styleShort ($debugStyleStatus)",
            "cam=${"%.4f".format(centerLat)},${"%.4f".format(centerLon)} z=$zoom",
            "frames=$debugFrameCount map=${if (map != null) "yes" else "no"}",
            "err=${debugLastError ?: "—"}",
        )
    }

    private fun applyCamera(map: MapLibreMap) {
        applyFollowPadding(map)
        val bearing = AutoMapHeading.effectiveBearing(orientationMode, headingDegrees)
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(centerLat, centerLon))
                    .zoom(zoom.toDouble())
                    .bearing(bearing.toDouble())
                    .build(),
            ),
        )
    }

    private fun applyFollowPadding(map: MapLibreMap) {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return
        val padding = AutoMapFollowFocalPoint.mapLibrePadding(
            visibleArea = visibleArea,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            headingUp = orientationMode == MapOrientationMode.HeadingUp,
        )
        map.setPadding(padding.left, padding.top, padding.right, padding.bottom)
    }

    private fun syncPoiLayer() {
        val map = mapContainer.mapLibreMapInstance ?: return
        map.getStyle { style ->
            adjustTextSizes(style)
        }
        MapLibreSharedHelper.syncPoiLayer(
            context = carContext,
            map = map,
            pois = lastPois,
            selectedPoiId = selectedPoiId,
            availabilityByPoiId = availabilityByPoiId,
            effectiveEnergyTypes = effectiveEnergyTypes,
            effectivePowerLevels = effectivePowerLevels,
            sizeProvider = { _, _ -> 96 }
        )
    }

    private fun syncSearchRadiusLayer() {
        val map = mapContainer.mapLibreMapInstance ?: return
        MapLibreSharedHelper.syncSearchRadiusLayer(
            map = map,
            centerLat = searchRadiusCenterLat,
            centerLon = searchRadiusCenterLon,
            radiusKm = searchRadiusKm
        )
    }

    companion object {
        private const val TAG = "CarMapLibreRenderer"
        private const val VIRTUAL_DISPLAY_NAME = "GastonMapLibreVirtualDisplay"
        /** Fallback OpenFreeMap vector style (Positron). */
        private const val DEFAULT_OPENFREEMAP_STYLE = "https://tiles.openfreemap.org/styles/positron"

        internal fun scaleExpressionArray(value: Any?, scale: Float): Any? {
            if (scale == 1.0f) return value
            return when (value) {
                is Array<*> -> {
                    val list = value.toList()
                    scaleExpressionList(list, scale).toTypedArray()
                }
                is List<*> -> {
                    scaleExpressionList(value, scale)
                }
                is Number -> {
                    value.toFloat() * scale
                }
                else -> value
            }
        }

        internal fun scaleExpressionList(list: List<*>, scale: Float): List<*> {
            if (list.isEmpty()) return list
            val op = list[0]
            return when (op) {
                "interpolate" -> {
                    // [ "interpolate", type, input, stop1_input, stop1_output, ... ]
                    // Outputs are at even indices starting from 4
                    list.mapIndexed { index, item ->
                        if (index >= 4 && index % 2 == 0) {
                            scaleExpressionArray(item, scale)
                        } else {
                            scaleExpressionArray(item, 1.0f)
                        }
                    }
                }
                "step" -> {
                    // [ "step", input, default_output, stop1_input, stop1_output, ... ]
                    // Outputs are at index 2, and even indices >= 4
                    list.mapIndexed { index, item ->
                        if (index == 2 || (index >= 4 && index % 2 == 0)) {
                            scaleExpressionArray(item, scale)
                        } else {
                            scaleExpressionArray(item, 1.0f)
                        }
                    }
                }
                "match" -> {
                    // [ "match", input, label1, output1, label2, output2, ..., default_output ]
                    // Outputs are odd indices starting from 3 up to size - 2, and the last index (size - 1)
                    val size = list.size
                    list.mapIndexed { index, item ->
                        if ((index >= 3 && index < size - 1 && index % 2 != 0) || index == size - 1) {
                            scaleExpressionArray(item, scale)
                        } else {
                            scaleExpressionArray(item, 1.0f)
                        }
                    }
                }
                "case" -> {
                    // [ "case", condition1, output1, condition2, output2, ..., default_output ]
                    // Outputs are even indices starting from 2, and the last index (size - 1)
                    val size = list.size
                    list.mapIndexed { index, item ->
                        if ((index >= 2 && index < size - 1 && index % 2 == 0) || index == size - 1) {
                            scaleExpressionArray(item, scale)
                        } else {
                            scaleExpressionArray(item, 1.0f)
                        }
                    }
                }
                "coalesce" -> {
                    // [ "coalesce", output1, output2, ... ]
                    // All arguments (except index 0) are potential outputs
                    list.mapIndexed { index, item ->
                        if (index > 0) {
                            scaleExpressionArray(item, scale)
                        } else {
                            item
                        }
                    }
                }
                "literal" -> {
                    list.mapIndexed { index, item ->
                        if (index == 1) {
                            scaleExpressionArray(item, scale)
                        } else {
                            item
                        }
                    }
                }
                else -> {
                    // For other operators, just recursively process with scale 1.0f (no direct scaling)
                    list.mapIndexed { index, item ->
                        if (index > 0) {
                            scaleExpressionArray(item, 1.0f)
                        } else {
                            item
                        }
                    }
                }
            }
        }

        internal fun toJsonElement(value: Any?): JsonElement {
            return when (value) {
                null -> JsonNull.INSTANCE
                is Number -> JsonPrimitive(value)
                is String -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is Char -> JsonPrimitive(value)
                is Array<*> -> {
                    val arr = JsonArray()
                    for (item in value) {
                        arr.add(toJsonElement(item))
                    }
                    arr
                }
                is List<*> -> {
                    val arr = JsonArray()
                    for (item in value) {
                        arr.add(toJsonElement(item))
                    }
                    arr
                }
                else -> JsonPrimitive(value.toString())
            }
        }
    }
}
