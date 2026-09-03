package fr.geoking.gaston.auto.maplibre

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.car.app.CarContext
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.Lifecycle
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.auto.AaMapSurfaceRenderer
import fr.geoking.gaston.auto.AutoMapCamera
import fr.geoking.gaston.auto.AutoMapFollowFocalPoint
import fr.geoking.gaston.auto.AutoMapHeading
import fr.geoking.gaston.auto.AutoMapOverlayHelper
import fr.geoking.gaston.auto.AutoMapPoiHitTest
import fr.geoking.gaston.auto.AutoMapQueryLoader
import fr.geoking.gaston.auto.AutoSurfaceRenderer
import fr.geoking.gaston.auto.MapOrientationMode
import fr.geoking.gaston.auto.OfflineMapAvailability
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.resolveAvailabilitySummary
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.layer.renderer.TileRendererLayer
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.tan

/**
 * Offline Mapsforge `.map` renderer for Android Auto (manual file path in settings).
 */
class MapsforgeAaRenderer(
    private val carContext: CarContext,
    @Suppress("UNUSED_PARAMETER") lifecycle: Lifecycle,
) : AaMapSurfaceRenderer {

    override var hudModeLabel: String = "Mapsforge"
    override var offlineUnavailable: Boolean = true

    private val uiHandler = Handler(Looper.getMainLooper())
    private val settingsManager =
        org.koin.core.context.GlobalContext.get().get<fr.geoking.gaston.SettingsManager>()

    private var surfaceContainer: SurfaceContainer? = null
    private var visibleArea: Rect? = null
    private var surfaceWidth: Int = 800
    private var surfaceHeight: Int = 480
    private var centerLat: Double = 48.8566
    private var centerLon: Double = 2.3522
    private var zoom: Int = AutoMapCamera.DEFAULT_ZOOM
    private var orientationMode: MapOrientationMode = MapOrientationMode.NorthUp
    private var headingDegrees: Float = 0f
    private var userLat: Double? = null
    private var userLon: Double? = null
    private var userHeadingDegrees: Float = 0f
    private var selectedPoiId: String? = null
    private var lastPois: List<Poi> = emptyList()
    private var effectiveEnergyTypes: Set<String> = emptySet()
    private var effectivePowerLevels: Set<Int> = emptySet()
    private var availabilityByPoiId: Map<String, StationAvailabilitySummary> = emptyMap()
    private var searchRadiusCenterLat: Double? = null
    private var searchRadiusCenterLon: Double? = null
    private var searchRadiusKm: Double? = null
    private var queryPending: Boolean = false

    private var mapView: MapView? = null
    private var tileLayer: TileRendererLayer? = null
    private var loadedMapPath: String? = null
    private var basemapBitmap: Bitmap? = null

    private val waitingPaint = Paint().apply { color = Color.rgb(0xE8, 0xE8, 0xE8) }
    private val searchRadiusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    init {
        AndroidGraphicFactory.createInstance(carContext.applicationContext)
    }

    override fun setStyleUrl(url: String) {
        // Mapsforge uses local .map files, not style URLs.
    }

    override fun updateLocation(lat: Double, lon: Double, zoomLevel: Int) {
        centerLat = lat
        centerLon = lon
        zoom = zoomLevel.coerceIn(AutoMapCamera.MIN_ZOOM, AutoMapCamera.MAX_ZOOM)
        mapView?.model?.mapViewPosition?.setCenter(LatLong(lat, lon))
        mapView?.model?.mapViewPosition?.setZoomLevel(zoom.toByte())
        requestRedraw()
    }

    override fun bumpZoom(delta: Int) {
        zoom = (zoom + delta).coerceIn(AutoMapCamera.MIN_ZOOM, AutoMapCamera.MAX_ZOOM)
        mapView?.model?.mapViewPosition?.setZoomLevel(zoom.toByte())
        requestRedraw()
    }

    override fun updateUserLocation(lat: Double, lon: Double, headingDegrees: Float) {
        userLat = lat
        userLon = lon
        userHeadingDegrees = AutoMapHeading.normalizeDegrees(headingDegrees)
        this.headingDegrees = headingDegrees
        requestRedraw()
    }

    override fun setMapOrientation(mode: MapOrientationMode, bearing: Float) {
        orientationMode = mode
        headingDegrees = bearing
        requestRedraw()
    }

    override fun updateVisibleArea(area: Rect) {
        visibleArea = Rect(area)
        requestRedraw()
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
        requestRedraw()
    }

    override fun updateSearchRadius(centerLat: Double, centerLon: Double, radiusKm: Double?) {
        searchRadiusCenterLat = centerLat
        searchRadiusCenterLon = centerLon
        searchRadiusKm = radiusKm
        requestRedraw()
    }

    override fun setQueryPending(pending: Boolean) {
        queryPending = pending
        requestRedraw()
    }

    override fun requestRedraw() {
        uiHandler.post { drawOnSurface() }
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

    override fun currentZoom(): Int = zoom
    override fun zoomForHitTest(): Int = zoom
    override fun mapLatForHitTest(): Double = centerLat
    override fun mapLonForHitTest(): Double = centerLon
    override fun centerPxXForHitTest(): Double = followFocalPoint().x
    override fun centerPxYForHitTest(): Double = followFocalPoint().y

    override fun attachSurface(container: SurfaceContainer) {
        surfaceContainer = container
        surfaceWidth = container.width.coerceAtLeast(100)
        surfaceHeight = container.height.coerceAtLeast(100)
        offlineUnavailable = !OfflineMapAvailability.isOfflineFileAvailable(settingsManager.settings.value)
        ensureMapsforgeLoaded()
        requestRedraw()
    }

    override fun detachSurface() {
        uiHandler.removeCallbacksAndMessages(null)
        releaseMapsforge()
        surfaceContainer = null
        basemapBitmap?.recycle()
        basemapBitmap = null
    }

    private fun ensureMapsforgeLoaded() {
        val settings = settingsManager.settings.value
        offlineUnavailable = !OfflineMapAvailability.isOfflineFileAvailable(settings)
        val path = settings.offlineMapsforgePath ?: return
        if (path == loadedMapPath && mapView != null) return
        releaseMapsforge()
        val file = File(path)
        if (!file.isFile) return
        try {
            val mapViewLocal = MapView(carContext)
            mapViewLocal.model.mapViewPosition.setCenter(LatLong(centerLat, centerLon))
            mapViewLocal.model.mapViewPosition.setZoomLevel(zoom.toByte())
            val tileCache = AndroidUtil.createTileCache(
                carContext,
                "mapsforge_aa",
                surfaceWidth,
                1f,
                mapViewLocal.model.frameBufferModel.overdrawFactor,
            )
            val mapDataStore = MapFile(file)
            val layer = TileRendererLayer(
                tileCache,
                mapDataStore,
                mapViewLocal.model.mapViewPosition,
                AndroidGraphicFactory.INSTANCE,
            )
            layer.setXmlRenderTheme(org.mapsforge.map.rendertheme.InternalRenderTheme.DEFAULT)
            mapViewLocal.layerManager.layers.add(layer)
            mapViewLocal.layout(0, 0, surfaceWidth, surfaceHeight)
            mapView = mapViewLocal
            tileLayer = layer
            loadedMapPath = path
            offlineUnavailable = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Mapsforge map: $path", e)
            offlineUnavailable = true
        }
    }

    private fun releaseMapsforge() {
        tileLayer = null
        mapView?.destroy()
        mapView = null
        loadedMapPath = null
    }

    private fun drawOnSurface() {
        val container = surfaceContainer ?: return
        val surface: Surface = container.surface ?: return
        if (!surface.isValid) return
        offlineUnavailable = !OfflineMapAvailability.isOfflineFileAvailable(settingsManager.settings.value)
        if (!offlineUnavailable) {
            ensureMapsforgeLoaded()
        }
        val canvas = try {
            surface.lockHardwareCanvas() ?: surface.lockCanvas(null)
        } catch (_: Exception) {
            null
        } ?: return
        try {
            drawFrame(canvas)
        } finally {
            try {
                surface.unlockCanvasAndPost(canvas)
            } catch (_: Exception) {
            }
        }
    }

    private fun drawFrame(canvas: Canvas) {
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), waitingPaint)
        val view = mapView
        if (!offlineUnavailable && view != null) {
            view.layout(0, 0, canvas.width, canvas.height)
            view.model.mapViewPosition.setCenter(LatLong(centerLat, centerLon))
            view.model.mapViewPosition.setZoomLevel(zoom.toByte())
            view.draw(canvas)
        }
        val bearing = AutoMapHeading.effectiveBearing(orientationMode, headingDegrees)
        val cx = centerPxXForHitTest().toFloat()
        val cy = centerPxYForHitTest().toFloat()
        if (bearing != 0f) {
            canvas.save()
            canvas.rotate(-bearing, cx, cy)
        }
        drawSearchRadius(canvas)
        drawPois(canvas, bearing)
        drawUserLocation(canvas)
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
        if (offlineUnavailable) {
            AutoMapOverlayHelper.drawOfflineUnavailableBanner(
                canvas = canvas,
                context = carContext,
                visibleArea = visibleArea,
                surfaceWidth = surfaceWidth,
                surfaceHeight = surfaceHeight,
            )
        }
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
        val radiusMeters = radiusKm * 1000.0
        val mapCenterX = lonToTileX(centerLon, zoom)
        val mapCenterY = latToTileY(centerLat, zoom)
        val cx = centerPxXForHitTest()
        val cy = centerPxYForHitTest()
        val centerTileX = lonToTileX(cLon, zoom)
        val centerTileY = latToTileY(cLat, zoom)
        val centerPx = (centerTileX - mapCenterX) * AutoSurfaceRenderer.TILE_SIZE + cx
        val centerPy = (centerTileY - mapCenterY) * AutoSurfaceRenderer.TILE_SIZE + cy
        val edgeLon = cLon + radiusMeters / (111_320.0 * cos(Math.toRadians(cLat)))
        val edgeTileX = lonToTileX(edgeLon, zoom)
        val radiusPx = kotlin.math.abs((edgeTileX - centerTileX) * AutoSurfaceRenderer.TILE_SIZE).toFloat()
        canvas.drawCircle(centerPx.toFloat(), centerPy.toFloat(), radiusPx, searchRadiusPaint)
    }

    private fun drawPois(canvas: Canvas, bearing: Float) {
        val markerWidthPx = (48 * carContext.resources.displayMetrics.density).toInt()
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

    private fun drawUserLocation(canvas: Canvas) {
        val uLat = userLat ?: return
        val uLon = userLon ?: return
        val mapCenterX = lonToTileX(centerLon, zoom)
        val mapCenterY = latToTileY(centerLat, zoom)
        val tileX = lonToTileX(uLon, zoom)
        val tileY = latToTileY(uLat, zoom)
        val drawX = ((tileX - mapCenterX) * AutoSurfaceRenderer.TILE_SIZE + centerPxXForHitTest()).toFloat()
        val drawY = ((tileY - mapCenterY) * AutoSurfaceRenderer.TILE_SIZE + centerPxYForHitTest()).toFloat()
        AutoMapOverlayHelper.drawHeadingArrow(canvas, drawX, drawY, userHeadingDegrees)
    }

    private fun followFocalPoint(): AutoMapFollowFocalPoint.FocalPoint =
        AutoMapFollowFocalPoint.focalPointPx(
            visibleArea = visibleArea,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight,
            headingUp = orientationMode == MapOrientationMode.HeadingUp,
        )

    private fun lonToTileX(lon: Double, zoom: Int): Double =
        (lon + 180.0) / 360.0 * (1 shl zoom)

    private fun latToTileY(lat: Double, zoom: Int): Double {
        val latRad = Math.toRadians(lat)
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)
    }

    companion object {
        private const val TAG = "MapsforgeAaRenderer"
    }
}
