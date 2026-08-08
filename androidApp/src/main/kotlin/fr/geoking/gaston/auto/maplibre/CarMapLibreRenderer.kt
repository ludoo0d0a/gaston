package fr.geoking.gaston.auto.maplibre

import android.graphics.Canvas
import android.graphics.PointF
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.TextureView
import androidx.car.app.CarContext
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.Lifecycle
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.auto.AutoMapCamera
import fr.geoking.gaston.auto.AutoMapOverlayHelper
import fr.geoking.gaston.auto.AutoMapFollowFocalPoint
import fr.geoking.gaston.auto.AutoMapHeading
import fr.geoking.gaston.auto.AutoMapQueryLoader
import fr.geoking.gaston.auto.MapOrientationMode
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.map.maplibre.MapLibreSharedHelper
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * Copies MapLibre [TextureView] frames onto the Android Auto [SurfaceContainer] surface.
 * Leverages the shared [MapLibreSharedHelper] for POI and search radius layer drawing.
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
    private var frameListenersAttached = false
    private var searchRadiusCenterLat: Double? = null
    private var searchRadiusCenterLon: Double? = null
    private var searchRadiusKm: Double? = null
    private var visibleArea: Rect? = null
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    private var queryPending: Boolean = false

    private val loaderAnimRunnable = object : Runnable {
        override fun run() {
            if (!queryPending || surfaceContainer == null) return
            drawOnSurface()
            uiHandler.postDelayed(this, 50L)
        }
    }

    val map: MapLibreMap?
        get() = mapContainer.mapLibreMapInstance

    init {
        mapContainer.onMapReady = { map ->
            val url = styleUrl
            if (url != null) {
                map.setStyle(url) { style ->
                    adjustTextSizes(style)
                    applyCamera(map)
                    syncPoiLayer()
                    syncSearchRadiusLayer()
                }
            } else {
                map.getStyle { style ->
                    adjustTextSizes(style)
                }
                applyCamera(map)
                syncPoiLayer()
                syncSearchRadiusLayer()
            }
        }
    }

    fun setStyleUrl(url: String) {
        if (styleUrl == url) return
        styleUrl = url
        mapContainer.setStyleUrl(url)
    }

    fun updateLocation(lat: Double, lon: Double, zoomLevel: Int) {
        centerLat = lat
        centerLon = lon
        zoom = zoomLevel.coerceIn(AutoMapCamera.MIN_ZOOM, AutoMapCamera.MAX_ZOOM)
        mapContainer.mapLibreMapInstance?.let { applyCamera(it) }
    }

    fun updateUserLocation(lat: Double, lon: Double, bearing: Float) {
        headingDegrees = bearing
        mapContainer.mapLibreMapInstance?.let { applyCamera(it) }
    }

    fun setMapOrientation(mode: MapOrientationMode, bearing: Float = headingDegrees) {
        orientationMode = mode
        headingDegrees = bearing
        mapContainer.mapLibreMapInstance?.let { applyCamera(it) }
    }

    fun updateVisibleArea(area: Rect) {
        if (visibleArea?.equals(area) == true) return
        visibleArea = Rect(area)
        mapContainer.mapLibreMapInstance?.let { applyCamera(it) }
    }

    fun bumpZoom(delta: Int) {
        zoom = (zoom + delta).coerceIn(AutoMapCamera.MIN_ZOOM, AutoMapCamera.MAX_ZOOM)
        mapContainer.mapLibreMapInstance?.let { map ->
            map.animateCamera(CameraUpdateFactory.zoomTo(zoom.toDouble()))
        }
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
        if (pending) {
            uiHandler.post(loaderAnimRunnable)
        } else {
            drawOnSurface()
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
        surfaceContainer = container
        surfaceWidth = container.width
        surfaceHeight = container.height
        mapContainer.setSurfaceSize(container.width, container.height)
        attachFrameListeners()
        mapContainer.mapLibreMapInstance?.let { applyCamera(it) }
        drawOnSurface()
    }

    fun detachSurface() {
        detachFrameListeners()
        surfaceContainer = null
        uiHandler.removeCallbacksAndMessages(null)
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

    private fun attachFrameListeners() {
        if (frameListenersAttached) return
        val mapView = mapContainer.mapViewInstance ?: return
        mapView.addOnDidBecomeIdleListener { drawOnSurface() }
        mapView.addOnWillStartRenderingFrameListener { drawOnSurface() }
        frameListenersAttached = true
    }

    private fun detachFrameListeners() {
        frameListenersAttached = false
    }

    private fun drawOnSurface() {
        val mapView = mapContainer.mapViewInstance ?: return
        val surface = surfaceContainer?.surface ?: return
        val canvas = surface.lockHardwareCanvas() ?: return
        try {
            drawMapOnCanvas(mapView, canvas)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawMapOnCanvas(mapView: MapView, canvas: Canvas) {
        val textureView = mapView.takeIf { it.childCount > 0 }?.getChildAt(0) as? TextureView
        textureView?.bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        if (queryPending) {
            AutoMapQueryLoader.draw(
                canvas = canvas,
                visibleArea = visibleArea,
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
            )
        }

        // Draw map overlay widgets (scale, compass, debug zoom)
        val bearing = AutoMapHeading.effectiveBearing(orientationMode, headingDegrees)
        val mapTileDebugEnabled = settingsManager.settings.value.mapTileDebugEnabled
        AutoMapOverlayHelper.drawCompassAndScale(
            canvas = canvas,
            context = carContext,
            visibleArea = visibleArea,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            bearing = bearing,
            zoom = zoom.toFloat(),
            latitude = centerLat,
            mapTileDebugEnabled = mapTileDebugEnabled,
            isDensityScaled = true
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
