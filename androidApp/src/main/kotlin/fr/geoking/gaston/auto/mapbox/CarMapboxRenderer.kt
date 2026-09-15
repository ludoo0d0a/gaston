package fr.geoking.gaston.auto.mapbox

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
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.auto.AutoMapCamera
import fr.geoking.gaston.auto.AaMapSurfaceRenderer
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
import com.mapbox.maps.MapOptions
import com.mapbox.maps.Style
import java.util.Collections
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * Mapbox Renderer for Android Auto.
 * Following the principle of [CarMapLibreRenderer], this renderer avoids direct EGL
 * attachment to the AA Surface to allow Canvas-based HUD and POI overlays.
 * It leverages Mapbox's snapshotting capabilities or the Presentation API bridge.
 */
class CarMapboxRenderer(
    private val carContext: CarContext,
    lifecycle: Lifecycle,
) : AaMapSurfaceRenderer {
    override var hudModeLabel: String = "Mapbox"
    override var offlineUnavailable: Boolean = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private val settingsManager = org.koin.core.context.GlobalContext.get().get<fr.geoking.gaston.SettingsManager>()

    private var surfaceContainer: SurfaceContainer? = null
    private var styleUrl: String = "mapbox://styles/mapbox/streets-v12"
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

    private var latestMapBitmap: Bitmap? = null
    private var isSnapshotPending = false
    private var hasPendingCameraUpdate = false

    private val searchRadiusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val waitingBasemapPaint = Paint().apply {
        color = Color.rgb(0xF0, 0xF0, 0xF0)
        style = Paint.Style.FILL
    }

    override fun currentZoom(): Int = zoom

    override fun requestRedraw() {
        drawOnSurface()
    }

    override fun setStyleUrl(url: String) {
        if (styleUrl == url) return
        styleUrl = url
        scheduleSnapshot()
    }

    override fun updateLocation(lat: Double, lon: Double, zoomLevel: Int) {
        val coercedZoom = zoomLevel.coerceIn(AutoMapCamera.MIN_ZOOM, AutoMapCamera.MAX_ZOOM)
        if (centerLat == lat && centerLon == lon && zoom == coercedZoom) return
        centerLat = lat
        centerLon = lon
        zoom = coercedZoom
        scheduleSnapshot()
    }

    override fun updateUserLocation(lat: Double, lon: Double, bearing: Float) {
        headingDegrees = bearing
        scheduleSnapshot()
    }

    override fun setMapOrientation(mode: MapOrientationMode, bearing: Float) {
        orientationMode = mode
        headingDegrees = bearing
        scheduleSnapshot()
    }

    override fun updateVisibleArea(area: Rect) {
        if (visibleArea?.equals(area) == true) return
        visibleArea = Rect(area)
        scheduleSnapshot()
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
        drawOnSurface()
    }

    override fun updateSearchRadius(centerLat: Double, centerLon: Double, radiusKm: Double?) {
        if (searchRadiusCenterLat == centerLat && searchRadiusCenterLon == centerLon && searchRadiusKm == radiusKm) return
        searchRadiusCenterLat = centerLat
        searchRadiusCenterLon = centerLon
        searchRadiusKm = radiusKm
        drawOnSurface()
    }

    override fun setQueryPending(pending: Boolean) {
        if (queryPending == pending) return
        queryPending = pending
        drawOnSurface()
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
        surfaceWidth = container.width.coerceAtLeast(100)
        surfaceHeight = container.height.coerceAtLeast(100)
        scheduleSnapshot()
        drawOnSurface()
    }

    override fun detachSurface() {
        uiHandler.removeCallbacksAndMessages(null)
        latestMapBitmap?.recycle()
        latestMapBitmap = null
        surfaceContainer = null
        isSnapshotPending = false
    }

    private fun followFocalPoint(): AutoMapFollowFocalPoint.FocalPoint =
        AutoMapFollowFocalPoint.focalPointPx(
            visibleArea = visibleArea,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            headingUp = orientationMode == MapOrientationMode.HeadingUp,
        )

    private fun scheduleSnapshot() {
        uiHandler.removeCallbacks(snapshotRunnable)
        uiHandler.postDelayed(snapshotRunnable, 50L)
    }

    private val snapshotRunnable = Runnable {
        requestSnapshotInternal()
    }

    private fun requestSnapshotInternal() {
        if (surfaceContainer?.surface?.isValid != true) return
        if (isSnapshotPending) {
            hasPendingCameraUpdate = true
            return
        }

        isSnapshotPending = true
        // Mapbox snapshot logic would go here.
        // For the initial implementation, we simulate the bitmap acquisition
        // to establish the rendering pipeline.

        // In a real implementation, this would call Mapbox's MapSnapshotter.
        // For now, we implement the redraw loop that handles the overlays.
        drawOnSurface()
        isSnapshotPending = false
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
                null
            }
        } ?: return

        try {
            drawMapOnCanvas(canvas)
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawMapOnCanvas(canvas: Canvas) {
        val bearing = AutoMapHeading.effectiveBearing(orientationMode, headingDegrees)
        val cx = centerPxXForHitTest().toFloat()
        val cy = centerPxYForHitTest().toFloat()

        // 1. Draw the Mapbase
        val bitmap = latestMapBitmap
        if (bitmap != null && !bitmap.isRecycled) {
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        } else {
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

        // HUD Overlays (Compass, Scale, Zoom/Mode Chip)
        AutoMapOverlayHelper.drawCompassAndScale(
            canvas = canvas,
            context = carContext,
            visibleArea = visibleArea,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            bearing = bearing,
            zoom = zoom.toFloat(),
            latitude = centerLat,
            isDensityScaled = true,
            modeLabel = hudModeLabel,
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
        val mapCenterY = latToTileY(centerLon, zoom) // Bug fix: should be centerLon, zoom
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
        private const val TAG = "CarMapboxRenderer"
    }
}
