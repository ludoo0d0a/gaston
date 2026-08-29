package fr.geoking.gaston.auto.maplibre

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
import fr.geoking.gaston.auto.AutoMapOverlayHelper
import fr.geoking.gaston.auto.AutoMapFollowFocalPoint
import fr.geoking.gaston.auto.AutoMapHeading
import fr.geoking.gaston.auto.AutoMapPoiHitTest
import fr.geoking.gaston.auto.AutoMapQueryLoader
import fr.geoking.gaston.auto.AutoSurfaceRenderer
import fr.geoking.gaston.auto.MapOrientationMode
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.resolveAvailabilitySummary
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import java.util.Collections
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * Direct In-Process Vector Map Renderer for Android Auto.
 * Renders OpenFreeMap vector styles off-screen via [MapSnapshotter], then blits the bitmap to the
 * AA [SurfaceContainer] with [Surface.lockHardwareCanvas] / [Surface.lockCanvas].
 *
 * Do not attach an EGL window surface to the same AA Surface — once EGL owns it, Canvas drawing
 * fails or stays black (see [CarEglSurfaceRenderer] for a future native EGL pipeline).
 */
class CarMapLibreRenderer(
    private val carContext: CarContext,
    lifecycle: Lifecycle,
) {
    private val uiHandler = Handler(Looper.getMainLooper())
    private val settingsManager = org.koin.core.context.GlobalContext.get().get<fr.geoking.gaston.SettingsManager>()

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
    private var searchRadiusCenterLat: Double? = null
    private var searchRadiusCenterLon: Double? = null
    private var searchRadiusKm: Double? = null
    private var visibleArea: Rect? = null
    private var surfaceWidth: Int = 800
    private var surfaceHeight: Int = 480
    private var queryPending: Boolean = false

    private var reusableSnapshotter: MapSnapshotter? = null
    private var latestVectorBitmap: Bitmap? = null
    private var isSnapshotPending = false
    private var lastSnapshotLat: Double = Double.NaN
    private var lastSnapshotLon: Double = Double.NaN
    private var lastSnapshotZoom: Int = -1
    private var lastSnapshotStyleUrl: String? = null

    private var debugPhase: String = "idle"
    private var debugSnapshotStatus: String = "—"
    private var debugLastError: String? = null
    private var debugSnapshotCount: Int = 0
    private var surfaceDpi: Int = 0
    private var snapshotStartedAtMs: Long = 0L
    private var lastSnapshotReadyAtMs: Long = 0L
    private var lastStyleLoadedAtMs: Long = 0L
    private var surfaceValid: Boolean = false

    private val searchRadiusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val waitingBasemapPaint = Paint().apply {
        color = Color.rgb(0xF0, 0xF0, 0xF0)
        style = Paint.Style.FILL
    }

    private val snapshotRunnable = Runnable {
        requestVectorSnapshotInternal()
    }

    private val snapshotTimeoutRunnable = Runnable {
        if (!isSnapshotPending) return@Runnable
        isSnapshotPending = false
        debugSnapshotStatus = "timeout"
        debugPhase = "snapshot_timeout"
        val timeoutMessage = "snapshot timed out after ${SNAPSHOT_TIMEOUT_MS}ms"
        debugLastError = timeoutMessage
        Log.e(TAG, timeoutMessage)
        logSnapshotError(timeoutMessage)
        scheduleVectorSnapshot()
        drawOnSurface()
    }

    /** Force an immediate canvas redraw (e.g. after toggling map debug overlay). */
    fun requestRedraw() {
        drawOnSurface()
    }

    private val loaderAnimRunnable = object : Runnable {
        override fun run() {
            if (!queryPending || surfaceContainer == null) return
            drawOnSurface()
            uiHandler.postDelayed(this, 50L)
        }
    }

    fun setStyleUrl(url: String) {
        if (styleUrl == url) {
            Log.d(TAG, "setStyleUrl unchanged: $url")
            return
        }
        Log.i(TAG, "setStyleUrl: $url")
        styleUrl = url
        debugSnapshotStatus = "loading"
        debugPhase = "style_change"
        debugLastError = null
        // Force a new snapshot (and rebuild snapshotter) for the new OpenFreeMap style.
        lastSnapshotZoom = -1
        lastSnapshotStyleUrl = null
        reusableSnapshotter?.cancel()
        reusableSnapshotter = null
        scheduleVectorSnapshot()
    }

    fun updateLocation(lat: Double, lon: Double, zoomLevel: Int) {
        val coercedZoom = zoomLevel.coerceIn(AutoMapCamera.MIN_ZOOM, AutoMapCamera.MAX_ZOOM)
        if (centerLat == lat && centerLon == lon && zoom == coercedZoom) return
        centerLat = lat
        centerLon = lon
        zoom = coercedZoom
        scheduleVectorSnapshot()
    }

    fun updateUserLocation(lat: Double, lon: Double, bearing: Float) {
        headingDegrees = bearing
        scheduleVectorSnapshot()
    }

    fun setMapOrientation(mode: MapOrientationMode, bearing: Float = headingDegrees) {
        orientationMode = mode
        headingDegrees = bearing
        scheduleVectorSnapshot()
    }

    fun updateVisibleArea(area: Rect) {
        if (visibleArea?.equals(area) == true) return
        visibleArea = Rect(area)
        scheduleVectorSnapshot()
    }

    fun bumpZoom(delta: Int) {
        zoom = (zoom + delta).coerceIn(AutoMapCamera.MIN_ZOOM, AutoMapCamera.MAX_ZOOM)
        scheduleVectorSnapshot()
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
        drawOnSurface()
    }

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
        drawOnSurface()
    }

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

    fun findPoisAt(screenX: Float, screenY: Float): List<Poi> =
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

    fun zoomForHitTest(): Int = zoom

    fun mapLatForHitTest(): Double = centerLat

    fun mapLonForHitTest(): Double = centerLon

    fun centerPxXForHitTest(): Double = followFocalPoint().x

    fun centerPxYForHitTest(): Double = followFocalPoint().y

    fun attachSurface(container: SurfaceContainer) {
        surfaceContainer = container
        surfaceWidth = container.width.coerceAtLeast(100)
        surfaceHeight = container.height.coerceAtLeast(100)
        surfaceDpi = container.dpi.coerceAtLeast(1)
        debugPhase = "attaching"
        debugLastError = null
        surfaceValid = container.surface?.isValid == true
        Log.i(
            TAG,
            "attachSurface ${surfaceWidth}x${surfaceHeight} dpi=$surfaceDpi valid=$surfaceValid style=$styleUrl",
        )
        debugPhase = if (surfaceValid) "surface_ready" else "surface_invalid"
        scheduleVectorSnapshot()
        drawOnSurface()
    }

    fun detachSurface() {
        Log.i(TAG, "detachSurface")
        uiHandler.removeCallbacksAndMessages(null)
        reusableSnapshotter?.cancel()
        reusableSnapshotter = null
        surfaceContainer = null
        surfaceValid = false
        debugPhase = "detached"
    }

    private fun followFocalPoint(): AutoMapFollowFocalPoint.FocalPoint =
        AutoMapFollowFocalPoint.focalPointPx(
            visibleArea = visibleArea,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            headingUp = orientationMode == MapOrientationMode.HeadingUp,
        )

    fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        if (scaleFactor > 1.0f) {
            bumpZoom(1)
        } else if (scaleFactor < 1.0f) {
            bumpZoom(-1)
        }
    }

    fun scrollBy(distanceX: Float, distanceY: Float) {
        drawOnSurface()
    }

    private fun scheduleVectorSnapshot() {
        uiHandler.removeCallbacks(snapshotRunnable)
        uiHandler.postDelayed(snapshotRunnable, 50L)
    }

    private fun requestVectorSnapshotInternal() {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) {
            Log.w(TAG, "snapshot skipped: bad size ${surfaceWidth}x${surfaceHeight}")
            return
        }
        if (isSnapshotPending) {
            Log.d(TAG, "snapshot skipped: already pending")
            return
        }

        val styleChanged = lastSnapshotStyleUrl != styleUrl
        if (!styleChanged &&
            lastSnapshotLat == centerLat &&
            lastSnapshotLon == centerLon &&
            lastSnapshotZoom == zoom
        ) {
            drawOnSurface()
            return
        }

        try {
            try {
                org.maplibre.android.MapLibre.getInstance(carContext)
            } catch (e: Throwable) {
                Log.w(TAG, "MapLibre.getInstance failed in CarMapLibreRenderer", e)
                debugLastError = "MapLibre.init: ${e.message}"
            }

            isSnapshotPending = true
            snapshotStartedAtMs = System.currentTimeMillis()
            uiHandler.removeCallbacks(snapshotTimeoutRunnable)
            uiHandler.postDelayed(snapshotTimeoutRunnable, SNAPSHOT_TIMEOUT_MS)
            debugPhase = "snapshot_pending"
            debugSnapshotStatus = "pending"
            Log.i(
                TAG,
                "start snapshot ${surfaceWidth}x${surfaceHeight} cam=$centerLat,$centerLon z=$zoom style=$styleUrl",
            )

            val bearing = AutoMapHeading.effectiveBearing(orientationMode, headingDegrees)
            val pixelRatio = (surfaceDpi / 160f).coerceAtLeast(1f)
            val cameraPosition = CameraPosition.Builder()
                .target(LatLng(centerLat, centerLon))
                .zoom((zoom - 1).toDouble())
                .bearing(bearing.toDouble())
                .build()

            var snapshotter = reusableSnapshotter
            if (snapshotter == null || styleChanged) {
                reusableSnapshotter?.cancel()
                val options = MapSnapshotter.Options(surfaceWidth, surfaceHeight)
                    .withStyle(styleUrl)
                    .withCameraPosition(cameraPosition)
                    .withPixelRatio(pixelRatio)
                    .withLogo(false)
                    .withAttribution(false)
                snapshotter = MapSnapshotter(carContext.applicationContext, options)
                reusableSnapshotter = snapshotter
                snapshotter.setObserver(object : MapSnapshotter.Observer {
                    override fun onDidFinishLoadingStyle() {
                        Log.i(TAG, "MapSnapshotter style loaded: $styleUrl")
                        debugSnapshotStatus = "style_ok"
                        debugPhase = "style_ok"
                        lastStyleLoadedAtMs = System.currentTimeMillis()
                    }

                    override fun onStyleImageMissing(imageName: String) {
                        Log.w(TAG, "MapSnapshotter missing style image: $imageName")
                    }
                })
            } else {
                snapshotter.setSize(surfaceWidth, surfaceHeight)
                snapshotter.setCameraPosition(cameraPosition)
                snapshotter.setStyleUrl(styleUrl)
            }

            snapshotter.start(
                object : MapSnapshotter.SnapshotReadyCallback {
                    override fun onSnapshotReady(snapshot: MapSnapshot) {
                        uiHandler.removeCallbacks(snapshotTimeoutRunnable)
                        isSnapshotPending = false
                        lastSnapshotLat = centerLat
                        lastSnapshotLon = centerLon
                        lastSnapshotZoom = zoom
                        lastSnapshotStyleUrl = styleUrl
                        latestVectorBitmap = snapshot.bitmap
                        debugSnapshotCount++
                        debugSnapshotStatus = "ok"
                        debugPhase = "snapshot_ok"
                        debugLastError = null
                        lastSnapshotReadyAtMs = System.currentTimeMillis()
                        Log.i(
                            TAG,
                            "snapshot ready #$debugSnapshotCount ${snapshot.bitmap.width}x${snapshot.bitmap.height}",
                        )
                        drawOnSurface()
                    }
                },
                object : MapSnapshotter.ErrorHandler {
                    override fun onError(error: String) {
                        uiHandler.removeCallbacks(snapshotTimeoutRunnable)
                        isSnapshotPending = false
                        debugSnapshotStatus = "fail"
                        debugPhase = "snapshot_fail"
                        debugLastError = error
                        Log.e(TAG, "MapSnapshotter error: $error")
                        logSnapshotError(error)
                        drawOnSurface()
                    }
                },
            )
        } catch (e: Throwable) {
            uiHandler.removeCallbacks(snapshotTimeoutRunnable)
            isSnapshotPending = false
            debugSnapshotStatus = "fail"
            debugPhase = "snapshot_fail"
            debugLastError = e.message
            Log.e(TAG, "MapSnapshotter vector render failed", e)
            logSnapshotError(e.message ?: e.toString())
            drawOnSurface()
        }
    }

    private fun drawOnSurface() {
        val container = surfaceContainer ?: return
        val surface = container.surface ?: return
        if (!surface.isValid) return

        val canvas = try {
            surface.lockHardwareCanvas()
        } catch (e: Throwable) {
            try {
                surface.lockCanvas(null)
            } catch (e2: Throwable) {
                Log.e(TAG, "lockCanvas failed", e2)
                debugLastError = "lockCanvas: ${e2.message}"
                null
            }
        } ?: return

        try {
            drawMapOnCanvas(canvas)
        } finally {
            try {
                surface.unlockCanvasAndPost(canvas)
            } catch (e: Exception) {
                Log.e(TAG, "unlockCanvasAndPost failed", e)
                debugLastError = "unlockCanvas: ${e.message}"
            }
        }
    }

    private fun drawMapOnCanvas(canvas: Canvas) {
        val bearing = AutoMapHeading.effectiveBearing(orientationMode, headingDegrees)
        val cx = centerPxXForHitTest().toFloat()
        val cy = centerPxYForHitTest().toFloat()

        val vectorBitmap = latestVectorBitmap
        if (vectorBitmap != null && !vectorBitmap.isRecycled) {
            canvas.drawBitmap(vectorBitmap, 0f, 0f, null)
        } else {
            // Avoid a pure-black AA surface while waiting for the first OpenFreeMap snapshot.
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), waitingBasemapPaint)
        }

        if (bearing != 0f) {
            canvas.save()
            canvas.rotate(-bearing, cx, cy)
        }

        drawSearchRadius(canvas)
        drawPois(canvas)

        if (bearing != 0f) {
            canvas.restore()
        }

        if (queryPending) {
            AutoMapQueryLoader.draw(
                canvas = canvas,
                density = carContext.resources.displayMetrics.density,
                visibleArea = visibleArea,
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
            )
        }

        val density = carContext.resources.displayMetrics.density
        val mapTileDebugEnabled = settingsManager.settings.value.mapTileDebugEnabled
        AutoMapOverlayHelper.drawMapLibreStatusStrip(
            canvas = canvas,
            visibleArea = visibleArea,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            density = density,
            chip = buildStatusChip(),
        )
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
        if (mapTileDebugEnabled) {
            AutoMapOverlayHelper.drawDebugHud(
                canvas = canvas,
                context = carContext,
                visibleArea = visibleArea,
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
                lines = buildDebugLines(),
            )
        }
    }

    private fun buildStatusChip(): AutoMapOverlayHelper.MapLibreStatusChip {
        val hasBitmap = latestVectorBitmap?.takeUnless { it.isRecycled } != null
        val severity = when {
            debugLastError != null || debugPhase.endsWith("fail") || debugPhase == "snapshot_timeout" ->
                AutoMapOverlayHelper.MapLibreStatusSeverity.Error
            debugPhase == "snapshot_ok" && hasBitmap ->
                AutoMapOverlayHelper.MapLibreStatusSeverity.Ok
            else -> AutoMapOverlayHelper.MapLibreStatusSeverity.Pending
        }
        val title = "MapLibre · $debugPhase"
        val subtitle = debugLastError?.take(72)
            ?: buildString {
                append("canvas · surf=")
                append(if (surfaceValid) "ok" else "no")
                append(" · bmp=")
                append(if (hasBitmap) "yes" else "no")
                append(" · ")
                append(debugSnapshotStatus)
                if (isSnapshotPending) append(" · pending")
            }
        return AutoMapOverlayHelper.MapLibreStatusChip(
            title = title,
            subtitle = subtitle,
            severity = severity,
        )
    }

    private fun buildDebugLines(): List<String> {
        val styleShort = styleUrl
            .removePrefix("https://tiles.openfreemap.org/styles/")
            .let { "ofm/$it" }
        val hasBitmap = latestVectorBitmap?.takeUnless { it.isRecycled } != null
        val snapAgeSec = if (lastSnapshotReadyAtMs > 0L) {
            ((System.currentTimeMillis() - lastSnapshotReadyAtMs) / 1000).toString() + "s"
        } else {
            "—"
        }
        val styleAgeSec = if (lastStyleLoadedAtMs > 0L) {
            ((System.currentTimeMillis() - lastStyleLoadedAtMs) / 1000).toString() + "s"
        } else {
            "—"
        }
        val pendingForMs = if (isSnapshotPending && snapshotStartedAtMs > 0L) {
            System.currentTimeMillis() - snapshotStartedAtMs
        } else {
            0L
        }
        return listOf(
            "MLAA phase=$debugPhase render=canvas+snapshot (no EGL)",
            "surf=${surfaceWidth}x${surfaceHeight}@${surfaceDpi} valid=$surfaceValid attached=${surfaceContainer != null}",
            "style=$styleShort status=$debugSnapshotStatus loaded=$styleAgeSec ago",
            "cam=${"%.4f".format(centerLat)},${"%.4f".format(centerLon)} z=$zoom bearing=${AutoMapHeading.effectiveBearing(orientationMode, headingDegrees).toInt()}°",
            "snaps=$debugSnapshotCount last=$snapAgeSec ago bmp=${if (hasBitmap) "yes" else "no"} pending=$isSnapshotPending${if (pendingForMs > 0) " ${pendingForMs}ms" else ""}",
            "err=${debugLastError ?: "—"}",
        )
    }

    private fun drawSearchRadius(canvas: Canvas) {
        val radiusKm = searchRadiusKm ?: return
        val cLat = searchRadiusCenterLat ?: return
        val cLon = searchRadiusCenterLon ?: return
        if (radiusKm <= 0.0) return

        val mapCenterX = lonToTileX(centerLon, zoom)
        val mapCenterY = latToTileY(centerLat, zoom)
        val tileX = lonToTileX(cLon, zoom)
        val tileY = latToTileY(cLat, zoom)
        val cx = ((tileX - mapCenterX) * AutoSurfaceRenderer.TILE_SIZE + centerPxXForHitTest()).toFloat()
        val cy = ((tileY - mapCenterY) * AutoSurfaceRenderer.TILE_SIZE + centerPxYForHitTest()).toFloat()
        val radiusPx = AutoMapCamera.radiusPxForKm(cLat, zoom, radiusKm)
        if (radiusPx < 2f) return
        canvas.drawCircle(cx, cy, radiusPx, searchRadiusPaint)
    }

    private fun drawPois(canvas: Canvas) {
        val bearing = AutoMapHeading.effectiveBearing(orientationMode, headingDegrees)
        val markerWidthPx = AutoSurfaceRenderer.POI_MARKER_WIDTH_PX
        val mapCenterX = lonToTileX(centerLon, zoom)
        val mapCenterY = latToTileY(centerLat, zoom)
        val cx = centerPxXForHitTest().toFloat()
        val cy = centerPxYForHitTest().toFloat()

        lastPois.forEach { poi ->
            val bitmap = PoiMarkerHelper.getMarkerBitmap(
                context = carContext,
                poi = poi,
                effectiveEnergyTypes = effectiveEnergyTypes,
                effectivePowerLevels = effectivePowerLevels,
                isSelected = poi.id == selectedPoiId,
                sizePx = markerWidthPx,
                availability = poi.resolveAvailabilitySummary(availabilityByPoiId[poi.id]),
            )

            val tileX = lonToTileX(poi.longitude, zoom)
            val tileY = latToTileY(poi.latitude, zoom)

            val drawX = ((tileX - mapCenterX) * AutoSurfaceRenderer.TILE_SIZE + cx).toFloat()
            val drawY = ((tileY - mapCenterY) * AutoSurfaceRenderer.TILE_SIZE + cy).toFloat()

            if (bearing != 0f) {
                canvas.save()
                canvas.rotate(bearing, drawX, drawY)
                canvas.drawBitmap(bitmap, drawX - bitmap.width / 2f, drawY - bitmap.height, null)
                canvas.restore()
            } else {
                canvas.drawBitmap(bitmap, drawX - bitmap.width / 2f, drawY - bitmap.height, null)
            }
        }
    }

    private fun lonToTileX(lon: Double, zoom: Int): Double =
        (lon + 180.0) / 360.0 * (1 shl zoom)

    private fun latToTileY(lat: Double, zoom: Int): Double {
        val latRad = Math.toRadians(lat)
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)
    }

    companion object {
        private const val TAG = "CarMapLibreRenderer"
        private const val SNAPSHOT_TIMEOUT_MS = 15_000L
        private const val MAX_SNAPSHOT_ERRORS = 12

        private val recentSnapshotErrors =
            Collections.synchronizedList(mutableListOf<MapLibreSnapshotError>())

        fun getRecentSnapshotErrors(): List<MapLibreSnapshotError> =
            synchronized(recentSnapshotErrors) { recentSnapshotErrors.toList() }

        fun clearRecentSnapshotErrors() {
            synchronized(recentSnapshotErrors) { recentSnapshotErrors.clear() }
        }

        private fun logSnapshotError(message: String) {
            val entry = MapLibreSnapshotError(message, System.currentTimeMillis())
            synchronized(recentSnapshotErrors) {
                recentSnapshotErrors.add(0, entry)
                while (recentSnapshotErrors.size > MAX_SNAPSHOT_ERRORS) {
                    recentSnapshotErrors.removeAt(recentSnapshotErrors.size - 1)
                }
            }
        }

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
                    list.mapIndexed { index, item ->
                        if (index >= 4 && index % 2 == 0) {
                            scaleExpressionArray(item, scale)
                        } else {
                            scaleExpressionArray(item, 1.0f)
                        }
                    }
                }
                "step" -> {
                    list.mapIndexed { index, item ->
                        if (index == 2 || (index >= 4 && index % 2 == 0)) {
                            scaleExpressionArray(item, scale)
                        } else {
                            scaleExpressionArray(item, 1.0f)
                        }
                    }
                }
                "match" -> {
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
                is Iterable<*> -> {
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

data class MapLibreSnapshotError(
    val message: String,
    val timestamp: Long,
)
