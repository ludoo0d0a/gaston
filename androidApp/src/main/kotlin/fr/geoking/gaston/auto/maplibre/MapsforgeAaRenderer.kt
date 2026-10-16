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
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.resolveAvailabilitySummary
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.util.AndroidUtil
import org.mapsforge.map.android.view.MapView
import fr.geoking.gaston.auto.mapsforge.CityLabel
import org.mapsforge.core.model.Tile
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.layer.renderer.TileRendererLayer
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
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
    private var activeDataStore: MapDataStore? = null
    private var loadedMapPath: String? = null
    private var basemapBitmap: Bitmap? = null

    private val tileCityLabelsCache = ConcurrentHashMap<String, List<CityLabel>>()

    private val cityFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#0F172A")
    }
    private val cityStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeJoin = Paint.Join.ROUND
    }

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

    override fun updateUserLocation(lat: Double, lon: Double, bearing: Float) {
        headingDegrees = bearing
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
            activeDataStore = mapDataStore
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
        try { activeDataStore?.close() } catch (_: Exception) {}
        activeDataStore = null
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
        drawCityLabels(canvas, bearing)
        drawPois(canvas, bearing)
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
            zoom = zoom.toFloat(),
            latitude = centerLat,
            isDensityScaled = true,
            modeLabel = hudModeLabel,
        )
    }

    private fun isPlaceVisibleAtZoom(placeType: String, zoom: Int): Boolean = when (placeType) {
        "city" -> zoom >= 5
        "town" -> zoom >= 8
        "village", "suburb" -> zoom >= 11
        "hamlet" -> zoom >= 13
        else -> zoom >= 12
    }

    private fun configureCityPaint(placeType: String) {
        val textSizeDp = when (placeType) {
            "city" -> 20f
            "town" -> 16f
            "village", "suburb" -> 14f
            else -> 12f
        }
        val density = carContext.resources.displayMetrics.density
        val px = textSizeDp * density

        cityFillPaint.textSize = px
        cityStrokePaint.textSize = px

        val isBold = placeType == "city" || placeType == "town"
        cityFillPaint.isFakeBoldText = isBold
        cityStrokePaint.isFakeBoldText = isBold

        cityFillPaint.color = when (placeType) {
            "city" -> Color.parseColor("#0F172A")
            "town" -> Color.parseColor("#1E293B")
            "village", "suburb" -> Color.parseColor("#334155")
            else -> Color.parseColor("#475569")
        }
    }

    private fun extractCityLabelsFromTile(ds: MapDataStore, x: Int, y: Int, z: Int) {
        val key = "labels/$z/$x/$y"
        if (tileCityLabelsCache.containsKey(key)) return
        try {
            val tile = Tile(x, y, z.toByte(), AutoSurfaceRenderer.TILE_SIZE)
            val result = ds.readPoiData(tile)
            val labels = mutableListOf<CityLabel>()
            val pois = result?.pointOfInterests ?: emptyList()
            for (poi in pois) {
                var placeType: String? = null
                var name: String? = null
                for (tag in poi.tags) {
                    if (tag.key == "place" && tag.value in VALID_PLACE_TYPES) {
                        placeType = tag.value
                    } else if (tag.key == "name" || tag.key == "name:fr") {
                        if (name == null || tag.key == "name:fr") {
                            name = tag.value
                        }
                    }
                }
                if (placeType != null && !name.isNullOrBlank()) {
                    labels.add(
                        CityLabel(
                            name = name,
                            lat = poi.position.latitude,
                            lon = poi.position.longitude,
                            placeType = placeType
                        )
                    )
                }
            }
            tileCityLabelsCache[key] = labels
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract city labels for $key", e)
            tileCityLabelsCache[key] = emptyList()
        }
    }

    private fun drawCityLabels(canvas: Canvas, bearing: Float) {
        val ds = activeDataStore ?: return
        if (zoom < 5) return

        val mapCenterX = lonToTileX(centerLon, zoom)
        val mapCenterY = latToTileY(centerLat, zoom)
        val cx = centerPxXForHitTest().toFloat()
        val cy = centerPxYForHitTest().toFloat()
        val tileSize = AutoSurfaceRenderer.TILE_SIZE

        val startTileX = floor((0 - cx) / tileSize + mapCenterX).toInt() - 1
        val endTileX = ceil((surfaceWidth - cx) / tileSize + mapCenterX).toInt() + 1
        val startTileY = floor((0 - cy) / tileSize + mapCenterY).toInt() - 1
        val endTileY = ceil((surfaceHeight - cy) / tileSize + mapCenterY).toInt() + 1

        val maxTiles = 1 shl zoom
        val visibleLabels = mutableListOf<CityLabel>()

        for (x in startTileX..endTileX) {
            for (y in startTileY..endTileY) {
                val wrappedX = (x % maxTiles + maxTiles) % maxTiles
                if (y < 0 || y >= maxTiles) continue
                extractCityLabelsFromTile(ds, wrappedX, y, zoom)
                val key = "labels/$zoom/$wrappedX/$y"
                val labels = tileCityLabelsCache[key] ?: continue
                visibleLabels.addAll(labels)
            }
        }

        if (visibleLabels.isEmpty()) return

        val drawnPositions = mutableListOf<Pair<Float, Float>>()

        for (label in visibleLabels) {
            if (!isPlaceVisibleAtZoom(label.placeType, zoom)) continue

            val tileX = lonToTileX(label.lon, zoom)
            val tileY = latToTileY(label.lat, zoom)
            val drawX = ((tileX - mapCenterX) * tileSize + cx).toFloat()
            val drawY = ((tileY - mapCenterY) * tileSize + cy).toFloat()

            if (drawX < -100f || drawX > surfaceWidth + 100f || drawY < -100f || drawY > surfaceHeight + 100f) {
                continue
            }

            if (drawnPositions.any { hypot(it.first - drawX, it.second - drawY) < 80f }) {
                continue
            }
            drawnPositions.add(drawX to drawY)

            configureCityPaint(label.placeType)

            if (bearing != 0f) {
                canvas.save()
                canvas.rotate(bearing, drawX, drawY)
                canvas.drawText(label.name, drawX, drawY, cityStrokePaint)
                canvas.drawText(label.name, drawX, drawY, cityFillPaint)
                canvas.restore()
            } else {
                canvas.drawText(label.name, drawX, drawY, cityStrokePaint)
                canvas.drawText(label.name, drawX, drawY, cityFillPaint)
            }
        }
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

        AutoMapOverlayHelper.drawRadarDangerZoneTriangles(
            canvas = canvas,
            density = carContext.resources.displayMetrics.density,
            radarPois = lastPois.filter { it.poiCategory == PoiCategory.Radar },
        ) { lat, lon ->
            val tileX = lonToTileX(lon, zoom)
            val tileY = latToTileY(lat, zoom)
            val drawX = ((tileX - mapCenterX) * AutoSurfaceRenderer.TILE_SIZE + cx).toFloat()
            val drawY = ((tileY - mapCenterY) * AutoSurfaceRenderer.TILE_SIZE + cy).toFloat()
            drawX to drawY
        }

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
        private val VALID_PLACE_TYPES = setOf("city", "town", "village", "hamlet", "suburb")
    }
}
