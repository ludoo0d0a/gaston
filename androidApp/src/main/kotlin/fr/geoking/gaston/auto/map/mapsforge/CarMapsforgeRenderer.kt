package fr.geoking.gaston.auto.map.mapsforge

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.util.LruCache
import android.view.Surface
import fr.geoking.gaston.R
import fr.geoking.gaston.api.belib.StationAvailabilitySummary
import fr.geoking.gaston.ui.map.components.AutoMapCamera
import fr.geoking.gaston.ui.map.components.AutoMapFollowFocalPoint
import fr.geoking.gaston.ui.map.components.AutoMapHeading
import fr.geoking.gaston.ui.map.components.AutoMapOverlayHelper
import fr.geoking.gaston.ui.map.components.AutoMapPoiHitTest
import fr.geoking.gaston.ui.map.components.AutoMapQueryLoader
import fr.geoking.gaston.ui.map.components.MapOrientationMode
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.resolveAvailabilitySummary
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import org.mapsforge.core.model.Tile
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.datastore.MapDataStore
import org.mapsforge.core.graphics.Filter
import org.mapsforge.core.mapelements.MapElementContainer
import org.mapsforge.core.mapelements.WayTextContainer
import org.mapsforge.core.model.Point
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.labels.TileBasedLabelStore
import org.mapsforge.map.layer.cache.TileCache
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.InternalRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderTheme
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.mapsforge.core.graphics.GraphicFactory
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

data class MapsforgeTileError(
    val url: String,
    val statusCode: Int,
    val errorMessage: String,
    val timestamp: Long
)

data class CityLabel(
    val name: String,
    val lat: Double,
    val lon: Double,
    val placeType: String
)

/**
 * Mapsforge vector & raster map renderer for Android Auto surface.
 * Supports offline vector rendering via downloaded Mapsforge `.map` files with fallbacks,
 * rotation (North-Up vs Heading-Up), POI markers with live availability, search radius,
 * scale bar, and tile debug grid overlay.
 */
class CarMapsforgeRenderer(
    private val context: Context,
    private val surface: Surface,
    private val width: Int,
    private val height: Int,
    private val mapManager: MapsforgeMapManager,
    initialLat: Double = 48.8566,
    initialLon: Double = 2.3522,
    private var tileUrlTemplate: String = "https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png",
    private val httpClient: HttpClient? = null,
) {
    private val client: HttpClient?
        get() = httpClient ?: runCatching { GlobalContext.get().get<HttpClient>() }.getOrNull()
    var hudModeLabel: String = "Mapsforge"

    @Volatile
    private var running = true
    @Volatile
    private var mapTileDebugEnabled = false
    private var needsRedraw = false
    private var forceRedraw = false
    private var tileRedrawPending = false
    private var lastDrawMillis: Long = 0
    private val drawLock = ReentrantLock()
    private val drawCondition = drawLock.newCondition()
    private var lat: Double = initialLat
    private var lon: Double = initialLon
    private var zoom: Int = 13
    private var userLat: Double? = null
    private var userLon: Double? = null
    private var userHeadingDegrees: Float = 0f
    private var visibleArea: Rect? = null
    private var orientationMode: MapOrientationMode = MapOrientationMode.NorthUp
    private var headingDegrees: Float = 0f
    private var hasMissingTiles = false
    private var lastMissingTileCheckTime = 0L
    private var lastPositionUpdateTime = 0L

    private val mapBearingDegrees: Float
        get() = AutoMapHeading.effectiveBearing(orientationMode, headingDegrees)

    private val centerPxX: Double
        get() = followFocalPoint().x

    private val centerPxY: Double
        get() = followFocalPoint().y

    private fun followFocalPoint(): AutoMapFollowFocalPoint.FocalPoint =
        AutoMapFollowFocalPoint.focalPointPx(
            visibleArea = visibleArea,
            surfaceWidth = width,
            surfaceHeight = height,
            headingUp = orientationMode == MapOrientationMode.HeadingUp,
        )

    private var selectedPoiId: String? = null
    private var pois: List<Poi> = emptyList()
    private var poiIds: List<String> = emptyList()
    private var effectiveEnergyTypes: Set<String> = emptySet()
    private var effectivePowerLevels: Set<Int> = emptySet()
    private var availabilityByPoiId: Map<String, StationAvailabilitySummary> = emptyMap()
    var isDevMode: Boolean = false
    var radarWarningDistanceMeters: Int = 1000

    private var historyPoints: List<Pair<Double, Double>> = emptyList()
    private var itineraryPoints: List<Pair<Double, Double>> = emptyList()
    private var searchRadiusCenterLat: Double? = null
    private var searchRadiusCenterLon: Double? = null
    private var searchRadiusKm: Double? = null
    private var queryPending: Boolean = false

    private val pendingRequests = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val tileCityLabelsCache = ConcurrentHashMap<String, List<CityLabel>>()
    private val executor = Executors.newFixedThreadPool(4)

    private var activeMapDataStore: MapDataStore? = null
    private var databaseRenderer: DatabaseRenderer? = null
    /** Labels are kept out of tile bitmaps so they can be drawn upright over the rotated basemap. */
    @Volatile private var labelStore: TileBasedLabelStore? = null
    private var labelCacheKey: String? = null
    private var labelCacheVersion: Int = -1
    private var labelCache: List<MapElementContainer> = emptyList()
    private val identityMatrix by lazy { AndroidGraphicFactory.INSTANCE.createMatrix() }
    private var mapsforgeTileCache: TileCache? = null
    private val renderTheme: XmlRenderTheme = InternalRenderTheme.DEFAULT
    /** Parsed once per map data store; RenderThemeFuture is expensive to rebuild per tile. */
    @Volatile private var themeFuture: RenderThemeFuture? = null
    private val displayModel = DisplayModel()

    /** Composed basemap framebuffer. */
    private val basemapLock = Any()
    private var basemapBitmap: Bitmap? = null
    private var basemapScratch: Bitmap? = null
    private var composedZoom: Int = Int.MIN_VALUE
    private var composedCenterTileX: Double = Double.NaN
    private var composedCenterTileY: Double = Double.NaN
    private var composedBearing: Float = Float.NaN
    private var composedCenterPxX: Double = Double.NaN
    private var composedCenterPxY: Double = Double.NaN
    private var composedMapOriginX: Double = Double.NaN
    private var composedMapOriginY: Double = Double.NaN
    private var basemapDirty: Boolean = true

    private val backgroundPaint = Paint().apply { color = Color.parseColor("#F1F0E8") }
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val searchRadiusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val userLocationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NAVIGATION_BLUE
        style = Paint.Style.FILL
    }
    private val userLocationStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
    }
    private val arrowPath = Path().apply {
        val radius = 24f
        moveTo(0f, -radius)
        lineTo(-radius * 0.8f, radius * 0.8f)
        lineTo(0f, radius * 0.4f)
        lineTo(radius * 0.8f, radius * 0.8f)
        close()
    }
    private val reusablePath = Path()
    private val drawThread = Thread(::runDrawLoop, "CarMapsforgeRenderer")

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

    private val debugTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 18f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    companion object {
        private const val TAG = "CarMapsforgeRenderer"
        private val NAVIGATION_BLUE = Color.parseColor("#4285F4")
        private const val MIN_DRAW_INTERVAL_MS = 33L
        const val TILE_SIZE = 512
        private const val LABEL_STORE_TILES = 256
        const val POI_MARKER_WIDTH_PX = 96
        private const val TILE_CACHE_MAX_BYTES = 24 * 1024 * 1024
        private val VALID_PLACE_TYPES = setOf("city", "town", "village", "hamlet", "suburb")

        private val sharedTileCache = object : LruCache<String, Bitmap>(TILE_CACHE_MAX_BYTES) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                if (value.isRecycled) 1 else value.byteCount.coerceAtLeast(1)
        }
        private val failedTiles = ConcurrentHashMap<String, Long>()
        val tileRetries = ConcurrentHashMap<String, Int>()
        private val recentTileErrors = Collections.synchronizedList(mutableListOf<MapsforgeTileError>())

        fun getRecentTileErrors(): List<MapsforgeTileError> = synchronized(recentTileErrors) {
            recentTileErrors.toList()
        }

        fun clearRecentTileErrors() {
            synchronized(recentTileErrors) {
                recentTileErrors.clear()
            }
        }

        fun logTileError(url: String, statusCode: Int, errorMessage: String) {
            val error = MapsforgeTileError(url, statusCode, errorMessage, System.currentTimeMillis())
            synchronized(recentTileErrors) {
                recentTileErrors.add(0, error)
                if (recentTileErrors.size > 20) {
                    recentTileErrors.removeAt(recentTileErrors.size - 1)
                }
            }
        }

        fun clearTileCache() {
            synchronized(sharedTileCache) {
                sharedTileCache.evictAll()
            }
            failedTiles.clear()
            tileRetries.clear()
        }
    }

    init {
        try {
            val app = context.applicationContext as? Application
            if (app != null) {
                try {
                    AndroidGraphicFactory.createInstance(app)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AndroidGraphicFactory", e)
        }
        reloadMapsforgeDataStore()
    }

    private fun obtainThemeFuture(graphicFactory: GraphicFactory): RenderThemeFuture =
        synchronized(this) {
            themeFuture ?: RenderThemeFuture(graphicFactory, renderTheme, displayModel).also {
                it.run()
                themeFuture = it
            }
        }

    fun reloadMapsforgeDataStore() {
        try {
            themeFuture = null
            activeMapDataStore?.close()
            mapsforgeTileCache?.destroy()
            activeMapDataStore = mapManager.createMapDataStore()
            val ds = activeMapDataStore
            val graphicFactory = try { AndroidGraphicFactory.INSTANCE } catch (_: Exception) { null }
            if (ds != null && graphicFactory != null) {
                val tileCache = InMemoryTileCache(64)
                mapsforgeTileCache = tileCache
                val store = TileBasedLabelStore(LABEL_STORE_TILES)
                labelStore?.destroy()
                labelStore = store
                labelCacheKey = null
                databaseRenderer = DatabaseRenderer(
                    ds,
                    graphicFactory,
                    tileCache,
                    store,
                    false,
                    false,
                    null
                )
                Log.d(TAG, "Initialized Mapsforge vector DatabaseRenderer successfully")
            } else {
                databaseRenderer = null
                mapsforgeTileCache = null
                Log.d(TAG, "No active Mapsforge map file available; using online raster fallback")
            }
            recycleBasemap()
            invalidate(force = true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Mapsforge DataStore", e)
            databaseRenderer = null
        }
    }

    fun start() {
        if (!drawThread.isAlive) drawThread.start()
    }

    fun stop() {
        drawLock.withLock {
            running = false
            drawCondition.signalAll()
        }
        executor.shutdownNow()
        try { drawThread.join(500) } catch (_: Exception) {}
        try { activeMapDataStore?.close() } catch (_: Exception) {}
        try { labelStore?.destroy() } catch (_: Exception) {}
        labelStore = null
        try { mapsforgeTileCache?.destroy() } catch (_: Exception) {}
        recycleBasemap()
    }

    fun invalidate(force: Boolean = false) {
        drawLock.withLock {
            needsRedraw = true
            if (force) forceRedraw = true
            tileRedrawPending = false
            drawCondition.signalAll()
        }
    }

    private fun scheduleTileRedraw() {
        drawLock.withLock {
            if (!tileRedrawPending) {
                tileRedrawPending = true
                needsRedraw = true
                drawCondition.signalAll()
            }
        }
    }

    fun updateLocation(newLat: Double, newLon: Double, newZoom: Int = 13) {
        if (lat == newLat && lon == newLon && zoom == newZoom) return
        lat = newLat
        lon = newLon
        zoom = newZoom
        lastPositionUpdateTime = System.currentTimeMillis()
        invalidate()
    }

    fun setMapOrientation(mode: MapOrientationMode, headingDegrees: Float = this.headingDegrees) {
        val normalizedHeading = AutoMapHeading.normalizeDegrees(headingDegrees)
        if (orientationMode == mode && this.headingDegrees == normalizedHeading) return
        orientationMode = mode
        this.headingDegrees = normalizedHeading
        invalidate(force = true)
    }

    fun setTileUrlTemplate(template: String) {
        if (tileUrlTemplate == template) return
        tileUrlTemplate = template
        clearTileCache()
        recycleBasemap()
        basemapDirty = true
        invalidate()
    }

    fun setMapTileDebugEnabled(enabled: Boolean) {
        if (mapTileDebugEnabled == enabled) return
        mapTileDebugEnabled = enabled
        invalidate()
    }

    fun updateUserLocation(newLat: Double, newLon: Double, heading: Float = userHeadingDegrees) {
        val normalizedHeading = AutoMapHeading.normalizeDegrees(heading)
        if (userLat == newLat && userLon == newLon && userHeadingDegrees == normalizedHeading) return
        userLat = newLat
        userLon = newLon
        userHeadingDegrees = normalizedHeading
        lastPositionUpdateTime = System.currentTimeMillis()
        invalidate()
    }

    fun updateVisibleArea(area: Rect) {
        if (visibleArea?.equals(area) == true) return
        visibleArea = Rect(area)
        invalidate(force = true)
    }

    fun updatePois(
        newPois: List<Poi>,
        effectiveEnergyTypes: Set<String>,
        effectivePowerLevels: Set<Int>,
        selectedId: String? = selectedPoiId,
        availability: Map<String, StationAvailabilitySummary> = availabilityByPoiId,
        isDevMode: Boolean = this.isDevMode,
        radarWarningDistanceMeters: Int = this.radarWarningDistanceMeters,
    ) {
        val newIds = newPois.map { it.id }
        if (poiIds == newIds &&
            this.effectiveEnergyTypes == effectiveEnergyTypes &&
            this.effectivePowerLevels == effectivePowerLevels &&
            this.selectedPoiId == selectedId &&
            this.availabilityByPoiId == availability &&
            this.isDevMode == isDevMode &&
            this.radarWarningDistanceMeters == radarWarningDistanceMeters
        ) {
            return
        }
        pois = newPois
        poiIds = newIds
        this.effectiveEnergyTypes = effectiveEnergyTypes
        this.effectivePowerLevels = effectivePowerLevels
        this.selectedPoiId = selectedId
        this.availabilityByPoiId = availability
        this.isDevMode = isDevMode
        this.radarWarningDistanceMeters = radarWarningDistanceMeters
        invalidate()
    }

    fun setHistory(points: List<Pair<Double, Double>>) {
        historyPoints = points.toList()
        invalidate()
    }

    fun setItinerary(points: List<Pair<Double, Double>>) {
        itineraryPoints = points.toList()
        invalidate()
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
        invalidate()
    }

    fun setQueryPending(pending: Boolean) {
        if (queryPending == pending) return
        queryPending = pending
        invalidate(force = true)
    }

    fun addHistoryPoint(pLat: Double, pLon: Double) {
        val last = historyPoints.lastOrNull()
        if (last != null) {
            if (abs(last.first - pLat) < 0.0002 && abs(last.second - pLon) < 0.0002) return
        }
        historyPoints = historyPoints + (pLat to pLon)
        invalidate()
    }

    private fun runDrawLoop() {
        while (running) {
            drawLock.lock()
            try {
                while (running) {
                    val now = System.currentTimeMillis()
                    val timeSinceLastDraw = now - lastDrawMillis
                    val isMoving = now - lastPositionUpdateTime < 2000L
                    val currentMinInterval = if (isMoving) MIN_DRAW_INTERVAL_MS else 2000L
                    val canDraw = forceRedraw || (needsRedraw && timeSinceLastDraw >= currentMinInterval)

                    if (canDraw) break

                    try {
                        if (needsRedraw && !forceRedraw) {
                            val waitTime = currentMinInterval - timeSinceLastDraw
                            if (waitTime > 0) {
                                drawCondition.await(waitTime, TimeUnit.MILLISECONDS)
                                continue
                            }
                        }
                        val nextCheckTime = lastMissingTileCheckTime + 2000L
                        val remaining = nextCheckTime - System.currentTimeMillis()
                        if (hasMissingTiles && remaining > 0) {
                            drawCondition.await(remaining.coerceAtMost(2000L), TimeUnit.MILLISECONDS)
                        } else {
                            drawCondition.await()
                        }
                    } catch (e: InterruptedException) {
                        return
                    }
                }
                needsRedraw = false
                forceRedraw = false
                tileRedrawPending = false
            } finally {
                drawLock.unlock()
            }
            if (!running) break

            val canvas = try { surface.lockCanvas(null) } catch (_: Exception) { null } ?: continue
            lastDrawMillis = System.currentTimeMillis()
            try {
                val bearing = mapBearingDegrees
                val cx = centerPxX.toFloat()
                val cy = centerPxY.toFloat()
                if (bearing != 0f) {
                    canvas.save()
                    canvas.rotate(-bearing, cx, cy)
                }
                drawBasemap(canvas)
                drawSearchRadius(canvas)
                drawPaths(canvas)
                drawCityLabels(canvas)
                drawPois(canvas)
                drawUserLocation(canvas)
                if (bearing != 0f) {
                    canvas.restore()
                }
                drawMapLabels(canvas, bearing)

                if (databaseRenderer == null) {
                    AutoMapOverlayHelper.drawOfflineUnavailableBanner(
                        canvas = canvas,
                        context = context,
                        visibleArea = visibleArea,
                        surfaceWidth = width,
                        surfaceHeight = height,
                    )
                }

                AutoMapOverlayHelper.drawCompassAndScale(
                    canvas = canvas,
                    context = context,
                    visibleArea = visibleArea,
                    surfaceWidth = width,
                    surfaceHeight = height,
                    zoom = zoom.toFloat(),
                    latitude = lat,
                    isDensityScaled = false,
                    modeLabel = hudModeLabel,
                )

                if (queryPending) {
                    AutoMapQueryLoader.draw(
                        canvas = canvas,
                        density = context.resources.displayMetrics.density,
                        visibleArea = visibleArea,
                        surfaceWidth = width,
                        surfaceHeight = height,
                        nowMs = lastDrawMillis,
                    )
                }
            } finally {
                try { surface.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
            }
            if (queryPending) {
                invalidate()
            }

            if (hasMissingTiles) {
                val now = System.currentTimeMillis()
                if (now - lastMissingTileCheckTime >= 2000L) {
                    lastMissingTileCheckTime = now
                    val threshold = now - 15000L
                    failedTiles.entries.removeIf { it.value < threshold }
                    synchronized(basemapLock) {
                        basemapDirty = true
                    }
                    invalidate(force = true)
                }
            }
        }
    }

    /**
     * Draws Mapsforge labels outside the rotated block: point labels and symbols are re-anchored at
     * their rotated screen position but drawn upright; street names follow their road (rotated).
     */
    private fun drawMapLabels(canvas: Canvas, bearing: Float) {
        val store = labelStore ?: return
        val z = zoom
        val cx = centerPxX
        val cy = centerPxY
        val centerPxMapX = lonToTileX(lon, z) * TILE_SIZE
        val centerPxMapY = latToTileY(lat, z) * TILE_SIZE
        val radius = hypot(max(cx, width - cx), max(cy, height - cy))
        val maxTile = (1 shl z) - 1
        val minTx = floor((centerPxMapX - radius) / TILE_SIZE).toInt().coerceIn(0, maxTile)
        val maxTx = floor((centerPxMapX + radius) / TILE_SIZE).toInt().coerceIn(0, maxTile)
        val minTy = floor((centerPxMapY - radius) / TILE_SIZE).toInt().coerceIn(0, maxTile)
        val maxTy = floor((centerPxMapY + radius) / TILE_SIZE).toInt().coerceIn(0, maxTile)

        val key = "$z/$minTx/$minTy/$maxTx/$maxTy"
        val version = store.version
        if (key != labelCacheKey || version != labelCacheVersion) {
            labelCache = try {
                store.getVisibleItems(
                    Tile(minTx, minTy, z.toByte(), TILE_SIZE),
                    Tile(maxTx, maxTy, z.toByte(), TILE_SIZE),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Mapsforge label query failed", e)
                emptyList()
            }
            labelCacheKey = key
            labelCacheVersion = version
        }
        if (labelCache.isEmpty()) return

        val gc = AndroidGraphicFactory.createGraphicContext(canvas)
        val theta = Math.toRadians(-bearing.toDouble())
        val cosT = cos(theta)
        val sinT = sin(theta)
        val margin = 160.0
        for (item in labelCache.toList()) {
            try {
                if (item is WayTextContainer) {
                    canvas.save()
                    if (bearing != 0f) canvas.rotate(-bearing, cx.toFloat(), cy.toFloat())
                    item.draw(gc, Point(centerPxMapX - cx, centerPxMapY - cy), identityMatrix, Filter.NONE)
                    canvas.restore()
                    continue
                }
                val dx = item.point.x - centerPxMapX
                val dy = item.point.y - centerPxMapY
                val sx = cx + dx * cosT - dy * sinT
                val sy = cy + dx * sinT + dy * cosT
                if (sx < -margin || sy < -margin || sx > width + margin || sy > height + margin) continue
                item.draw(gc, Point(item.point.x - sx, item.point.y - sy), identityMatrix, Filter.NONE)
            } catch (_: Exception) {
            }
        }
    }

    private fun basemapDimensions(): Pair<Int, Int> {
        if (mapBearingDegrees == 0f) {
            return width to height
        }
        val cx = centerPxX
        val cy = centerPxY
        val maxDistance = maxOf(
            hypot(cx, cy),
            hypot(width - cx, cy),
            hypot(cx, height - cy),
            hypot(width - cx, height - cy)
        )
        val side = ceil(2.0 * maxDistance).toInt().coerceAtLeast(max(width, height))
        return side to side
    }

    private fun recycleBasemap() {
        synchronized(basemapLock) {
            basemapBitmap?.recycle()
            basemapScratch?.recycle()
            basemapBitmap = null
            basemapScratch = null
            composedZoom = Int.MIN_VALUE
            composedCenterTileX = Double.NaN
            composedCenterTileY = Double.NaN
            composedBearing = Float.NaN
            composedCenterPxX = Double.NaN
            composedCenterPxY = Double.NaN
            composedMapOriginX = Double.NaN
            composedMapOriginY = Double.NaN
            basemapDirty = true
        }
    }

    private fun ensureBasemapBitmapsLocked(bw: Int, bh: Int): Bitmap {
        val existing = basemapBitmap
        if (existing != null && !existing.isRecycled && existing.width == bw && existing.height == bh) {
            val scratch = basemapScratch
            if (scratch == null || scratch.isRecycled || scratch.width != bw || scratch.height != bh) {
                scratch?.recycle()
                basemapScratch = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            }
            return existing
        }
        basemapBitmap?.recycle()
        basemapScratch?.recycle()
        basemapBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        basemapScratch = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        composedZoom = Int.MIN_VALUE
        composedCenterTileX = Double.NaN
        composedCenterTileY = Double.NaN
        composedBearing = Float.NaN
        composedCenterPxX = Double.NaN
        composedCenterPxY = Double.NaN
        composedMapOriginX = Double.NaN
        composedMapOriginY = Double.NaN
        basemapDirty = true
        return basemapBitmap!!
    }

    private fun drawBasemap(canvas: Canvas) {
        synchronized(basemapLock) {
            updateComposedBasemapLocked()
            val basemap = basemapBitmap ?: return
            val originX = composedMapOriginX
            val originY = composedMapOriginY
            if (originX.isNaN() || originY.isNaN()) return
            canvas.drawBitmap(basemap, originX.toFloat(), originY.toFloat(), null)
        }
    }

    private fun updateComposedBasemapLocked() {
        val (bw, bh) = basemapDimensions()
        val basemap = ensureBasemapBitmapsLocked(bw, bh)
        val bearing = mapBearingDegrees
        val cx = centerPxX
        val cy = centerPxY
        val centerTileX = lonToTileX(lon, zoom)
        val centerTileY = latToTileY(lat, zoom)

        hasMissingTiles = false

        val mapOriginX: Double
        val mapOriginY: Double
        if (bearing == 0f) {
            mapOriginX = 0.0
            mapOriginY = 0.0
        } else {
            mapOriginX = cx - bw / 2.0
            mapOriginY = cy - bh / 2.0
        }

        val canScroll = !basemapDirty &&
            composedZoom == zoom &&
            composedBearing == bearing &&
            composedCenterPxX == cx &&
            composedCenterPxY == cy &&
            !composedCenterTileX.isNaN() &&
            !composedCenterTileY.isNaN()

        if (canScroll) {
            val dx = (composedCenterTileX - centerTileX) * TILE_SIZE
            val dy = (composedCenterTileY - centerTileY) * TILE_SIZE
            if (dx == 0.0 && dy == 0.0) {
                return
            }
            if (abs(dx) < bw && abs(dy) < bh) {
                scrollBasemapLocked(dx, dy, centerTileX, centerTileY, cx, cy, mapOriginX, mapOriginY)
                return
            }
        }

        composeBasemapFullLocked(basemap, centerTileX, centerTileY, cx, cy, bearing, mapOriginX, mapOriginY)
    }

    private fun composeBasemapFullLocked(
        basemap: Bitmap,
        centerTileX: Double,
        centerTileY: Double,
        cx: Double,
        cy: Double,
        bearing: Float,
        mapOriginX: Double,
        mapOriginY: Double,
    ) {
        val canvas = Canvas(basemap)
        canvas.drawRect(0f, 0f, basemap.width.toFloat(), basemap.height.toFloat(), backgroundPaint)
        drawTilesInto(
            canvas = canvas,
            centerTileX = centerTileX,
            centerTileY = centerTileY,
            mapCenterPxX = cx,
            mapCenterPxY = cy,
            mapOriginX = mapOriginX,
            mapOriginY = mapOriginY,
            clipLeft = 0,
            clipTop = 0,
            clipRight = basemap.width,
            clipBottom = basemap.height,
        )
        composedZoom = zoom
        composedCenterTileX = centerTileX
        composedCenterTileY = centerTileY
        composedBearing = bearing
        composedCenterPxX = cx
        composedCenterPxY = cy
        composedMapOriginX = mapOriginX
        composedMapOriginY = mapOriginY
        basemapDirty = false
    }

    private fun scrollBasemapLocked(
        dx: Double,
        dy: Double,
        newCenterTileX: Double,
        newCenterTileY: Double,
        cx: Double,
        cy: Double,
        mapOriginX: Double,
        mapOriginY: Double,
    ) {
        val basemap = basemapBitmap ?: return
        val scratch = basemapScratch ?: return
        val bw = basemap.width
        val bh = basemap.height
        val dxF = dx.toFloat()
        val dyF = dy.toFloat()

        scratch.eraseColor(Color.parseColor("#F1F0E8"))
        Canvas(scratch).drawBitmap(basemap, dxF, dyF, null)

        basemapBitmap = scratch
        basemapScratch = basemap

        val scrolled = basemapBitmap!!
        val canvas = Canvas(scrolled)

        if (dx > 0) {
            val stripW = ceil(dx).toInt().coerceIn(1, bw)
            drawTilesInto(
                canvas, newCenterTileX, newCenterTileY, cx, cy, mapOriginX, mapOriginY,
                clipLeft = 0, clipTop = 0, clipRight = stripW, clipBottom = bh,
            )
        } else if (dx < 0) {
            val stripW = ceil(-dx).toInt().coerceIn(1, bw)
            drawTilesInto(
                canvas, newCenterTileX, newCenterTileY, cx, cy, mapOriginX, mapOriginY,
                clipLeft = bw - stripW, clipTop = 0, clipRight = bw, clipBottom = bh,
            )
        }
        if (dy > 0) {
            val stripH = ceil(dy).toInt().coerceIn(1, bh)
            drawTilesInto(
                canvas, newCenterTileX, newCenterTileY, cx, cy, mapOriginX, mapOriginY,
                clipLeft = 0, clipTop = 0, clipRight = bw, clipBottom = stripH,
            )
        } else if (dy < 0) {
            val stripH = ceil(-dy).toInt().coerceIn(1, bh)
            drawTilesInto(
                canvas, newCenterTileX, newCenterTileY, cx, cy, mapOriginX, mapOriginY,
                clipLeft = 0, clipTop = bh - stripH, clipRight = bw, clipBottom = bh,
            )
        }

        composedCenterTileX = newCenterTileX
        composedCenterTileY = newCenterTileY
        composedCenterPxX = cx
        composedCenterPxY = cy
        composedMapOriginX = mapOriginX
        composedMapOriginY = mapOriginY
        basemapDirty = false
    }

    private fun drawTileDebugGrid(
        canvas: Canvas,
        drawX: Float,
        drawY: Float,
        x: Int,
        y: Int,
        z: Int,
        key: String,
        isLoaded: Boolean
    ) {
        val isPending = pendingRequests.contains(key)
        val isFailed = failedTiles.containsKey(key)

        val borderPaint = Paint().apply {
            color = when {
                isLoaded -> Color.GREEN
                isPending -> Color.YELLOW
                isFailed -> Color.RED
                else -> Color.GRAY
            }
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRect(drawX, drawY, drawX + TILE_SIZE, drawY + TILE_SIZE, borderPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.MAGENTA
            textSize = 14f
            textAlign = Paint.Align.LEFT
            isFakeBoldText = true
        }
        val statusText = when {
            isLoaded -> "OK (MF)"
            isPending -> context.getString(R.string.tile_status_loading)
            isFailed -> context.getString(R.string.tile_status_failed)
            else -> context.getString(R.string.tile_status_not_requested)
        }
        val bearing = mapBearingDegrees
        if (bearing != 0f) {
            canvas.save()
            canvas.rotate(bearing, drawX + 10f, drawY + 25f)
        }
        canvas.drawText("MF Z:$z X:$x Y:$y", drawX + 10f, drawY + 25f, textPaint)
        canvas.drawText(
            context.getString(R.string.tile_status_label, statusText),
            drawX + 10f,
            drawY + 45f,
            textPaint
        )
        if (bearing != 0f) {
            canvas.restore()
        }
    }

    private fun drawTilesInto(
        canvas: Canvas,
        centerTileX: Double,
        centerTileY: Double,
        mapCenterPxX: Double,
        mapCenterPxY: Double,
        mapOriginX: Double,
        mapOriginY: Double,
        clipLeft: Int,
        clipTop: Int,
        clipRight: Int,
        clipBottom: Int,
    ) {
        if (clipLeft >= clipRight || clipTop >= clipBottom) return

        val bitmapCenterX = mapCenterPxX - mapOriginX
        val bitmapCenterY = mapCenterPxY - mapOriginY

        val startTileX = floor((clipLeft - bitmapCenterX) / TILE_SIZE + centerTileX).toInt() - 1
        val endTileX = ceil((clipRight - bitmapCenterX) / TILE_SIZE + centerTileX).toInt() + 1
        val startTileY = floor((clipTop - bitmapCenterY) / TILE_SIZE + centerTileY).toInt() - 1
        val endTileY = ceil((clipBottom - bitmapCenterY) / TILE_SIZE + centerTileY).toInt() + 1

        canvas.save()
        canvas.clipRect(clipLeft, clipTop, clipRight, clipBottom)
        for (x in startTileX..endTileX) {
            for (y in startTileY..endTileY) {
                val bitmap = getTile(x, y, zoom)
                val drawX = ((x - centerTileX) * TILE_SIZE + bitmapCenterX).toFloat()
                val drawY = ((y - centerTileY) * TILE_SIZE + bitmapCenterY).toFloat()

                val maxTiles = 1 shl zoom
                val wrappedX = (x % maxTiles + maxTiles) % maxTiles
                val key = "mf/$zoom/$wrappedX/$y"

                if (bitmap != null) {
                    val dstRect = Rect(
                        drawX.toInt(),
                        drawY.toInt(),
                        (drawX + TILE_SIZE).toInt(),
                        (drawY + TILE_SIZE).toInt()
                    )
                    canvas.drawBitmap(bitmap, null, dstRect, null)

                    if (mapTileDebugEnabled) {
                        drawTileDebugGrid(canvas, drawX, drawY, x, y, zoom, key, isLoaded = true)
                    }
                } else {
                    hasMissingTiles = true
                    if (mapTileDebugEnabled) {
                        drawTileDebugGrid(canvas, drawX, drawY, x, y, zoom, key, isLoaded = false)
                    }
                }
            }
        }
        canvas.restore()
    }

    private fun patchTileIntoBasemap(tileX: Int, tileY: Int, tileZ: Int, tileBitmap: Bitmap) {
        synchronized(basemapLock) {
            val basemap = basemapBitmap ?: return
            if (basemap.isRecycled || basemapDirty) return
            if (composedZoom != tileZ) return
            if (composedCenterTileX.isNaN() || composedCenterTileY.isNaN()) return
            if (composedMapOriginX.isNaN() || composedMapOriginY.isNaN()) return

            val bitmapCenterX = composedCenterPxX - composedMapOriginX
            val bitmapCenterY = composedCenterPxY - composedMapOriginY
            val drawX = ((tileX - composedCenterTileX) * TILE_SIZE + bitmapCenterX).toFloat()
            val drawY = ((tileY - composedCenterTileY) * TILE_SIZE + bitmapCenterY).toFloat()

            if (drawX >= basemap.width || drawY >= basemap.height ||
                drawX + TILE_SIZE <= 0 || drawY + TILE_SIZE <= 0
            ) {
                return
            }

            val dstRect = Rect(
                drawX.toInt(),
                drawY.toInt(),
                (drawX + TILE_SIZE).toInt(),
                (drawY + TILE_SIZE).toInt()
            )
            Canvas(basemap).drawBitmap(tileBitmap, null, dstRect, null)
        }
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
        val density = context.resources.displayMetrics.density
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
            val tile = Tile(x, y, z.toByte(), TILE_SIZE)
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

    private fun drawCityLabels(canvas: Canvas) {
        val centerX = lonToTileX(lon, zoom)
        val centerY = latToTileY(lat, zoom)
        val bearing = mapBearingDegrees
        val currentZoom = zoom

        val visibleLabels = mutableListOf<CityLabel>()
        val startTileX = floor((0 - centerPxX) / TILE_SIZE + centerX).toInt() - 1
        val endTileX = ceil((width - centerPxX) / TILE_SIZE + centerX).toInt() + 1
        val startTileY = floor((0 - centerPxY) / TILE_SIZE + centerY).toInt() - 1
        val endTileY = ceil((height - centerPxY) / TILE_SIZE + centerY).toInt() + 1

        val maxTiles = 1 shl currentZoom
        for (x in startTileX..endTileX) {
            for (y in startTileY..endTileY) {
                val wrappedX = (x % maxTiles + maxTiles) % maxTiles
                val key = "labels/$currentZoom/$wrappedX/$y"
                val labels = tileCityLabelsCache[key] ?: continue
                visibleLabels.addAll(labels)
            }
        }

        if (visibleLabels.isEmpty()) return

        val drawnPositions = mutableListOf<Pair<Float, Float>>()

        for (label in visibleLabels) {
            if (!isPlaceVisibleAtZoom(label.placeType, currentZoom)) continue

            val tileX = lonToTileX(label.lon, currentZoom)
            val tileY = latToTileY(label.lat, currentZoom)
            val drawX = ((tileX - centerX) * TILE_SIZE + centerPxX).toFloat()
            val drawY = ((tileY - centerY) * TILE_SIZE + centerPxY).toFloat()

            if (drawX < -100f || drawX > width + 100f || drawY < -100f || drawY > height + 100f) {
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
        if (radiusKm <= 0.0) return

        val mapCenterX = lonToTileX(lon, zoom)
        val mapCenterY = latToTileY(lat, zoom)
        val tileX = lonToTileX(cLon, zoom)
        val tileY = latToTileY(cLat, zoom)
        val cx = ((tileX - mapCenterX) * TILE_SIZE + centerPxX).toFloat()
        val cy = ((tileY - mapCenterY) * TILE_SIZE + centerPxY).toFloat()
        val radiusPx = AutoMapCamera.radiusPxForKm(cLat, zoom, radiusKm)
        if (radiusPx < 2f) return
        canvas.drawCircle(cx, cy, radiusPx, searchRadiusPaint)
    }

    private fun drawPaths(canvas: Canvas) {
        if (historyPoints.isEmpty() && itineraryPoints.isEmpty()) return

        val centerX = lonToTileX(lon, zoom)
        val centerY = latToTileY(lat, zoom)

        fun drawPoints(points: List<Pair<Double, Double>>) {
            if (points.size < 2) return
            reusablePath.reset()
            var first = true
            points.forEach { (pLat, pLon) ->
                val tileX = lonToTileX(pLon, zoom)
                val tileY = latToTileY(pLat, zoom)
                val x = ((tileX - centerX) * TILE_SIZE + centerPxX).toFloat()
                val y = ((tileY - centerY) * TILE_SIZE + centerPxY).toFloat()
                if (first) {
                    reusablePath.moveTo(x, y)
                    first = false
                } else {
                    reusablePath.lineTo(x, y)
                }
            }
            canvas.drawPath(reusablePath, routePaint)
        }

        drawPoints(itineraryPoints)
        drawPoints(historyPoints)
    }

    private fun drawRadarPreventionCircles(canvas: Canvas) {
        val centerX = lonToTileX(lon, zoom)
        val centerY = latToTileY(lat, zoom)
        AutoMapOverlayHelper.drawRadarDangerZoneTriangles(
            canvas = canvas,
            density = context.resources.displayMetrics.density,
            radarPois = pois.filter { it.poiCategory == PoiCategory.Radar },
        ) { lat, lon ->
            val tileX = lonToTileX(lon, zoom)
            val tileY = latToTileY(lat, zoom)
            val drawX = ((tileX - centerX) * TILE_SIZE + centerPxX).toFloat()
            val drawY = ((tileY - centerY) * TILE_SIZE + centerPxY).toFloat()
            drawX to drawY
        }
    }

    private fun drawPois(canvas: Canvas) {
        drawRadarPreventionCircles(canvas)

        val centerX = lonToTileX(lon, zoom)
        val centerY = latToTileY(lat, zoom)
        val bearing = mapBearingDegrees

        val markerWidthPx = POI_MARKER_WIDTH_PX

        pois.forEach { poi ->
            val tileX = lonToTileX(poi.longitude, zoom)
            val tileY = latToTileY(poi.latitude, zoom)

            val drawX = ((tileX - centerX) * TILE_SIZE + centerPxX).toFloat()
            val drawY = ((tileY - centerY) * TILE_SIZE + centerPxY).toFloat()

            val bitmap = PoiMarkerHelper.getMarkerBitmap(
                context = context,
                poi = poi,
                effectiveEnergyTypes = effectiveEnergyTypes,
                effectivePowerLevels = effectivePowerLevels,
                isSelected = poi.id == selectedPoiId,
                sizePx = markerWidthPx,
                availability = poi.resolveAvailabilitySummary(availabilityByPoiId[poi.id]),
            )

            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val pad = markerWidthPx * 2f
            val maxDistance = maxOf(
                hypot(centerPxX, centerPxY),
                hypot(width - centerPxX, centerPxY),
                hypot(centerPxX, height - centerPxY),
                hypot(width - centerPxX, height - centerPxY)
            )
            val dist = hypot(drawX - centerPxX, drawY - centerPxY)
            if (dist > maxDistance + pad) {
                return@forEach
            }

            if (bearing != 0f) {
                canvas.save()
                canvas.rotate(bearing, drawX, drawY)
                canvas.drawBitmap(bitmap, drawX - bw / 2f, drawY - bh, null)
                canvas.restore()
            } else {
                canvas.drawBitmap(bitmap, drawX - bw / 2f, drawY - bh, null)
            }
        }
    }

    internal fun centerPxXForHitTest(): Double = centerPxX

    internal fun centerPxYForHitTest(): Double = centerPxY

    internal fun mapLatForHitTest(): Double = lat

    internal fun mapLonForHitTest(): Double = lon

    internal fun zoomForHitTest(): Int = zoom

    fun findPoisAt(screenX: Float, screenY: Float): List<Poi> =
        AutoMapPoiHitTest.findPoisAt(
            screenX = screenX,
            screenY = screenY,
            pois = pois,
            mapLat = lat,
            mapLon = lon,
            zoom = zoom,
            mapBearingDegrees = mapBearingDegrees,
            centerPxX = centerPxX,
            centerPxY = centerPxY,
            visibleArea = visibleArea,
        )

    private fun drawUserLocation(canvas: Canvas) {
        val uLat = userLat ?: return
        val uLon = userLon ?: return

        val centerX = lonToTileX(lon, zoom)
        val centerY = latToTileY(lat, zoom)

        val tileX = lonToTileX(uLon, zoom)
        val tileY = latToTileY(uLat, zoom)

        val drawX = ((tileX - centerX) * TILE_SIZE + centerPxX).toFloat()
        val drawY = ((tileY - centerY) * TILE_SIZE + centerPxY).toFloat()

        val rotation = userHeadingDegrees

        canvas.save()
        canvas.translate(drawX, drawY)
        canvas.rotate(rotation)

        canvas.drawPath(arrowPath, userLocationPaint)
        canvas.drawPath(arrowPath, userLocationStrokePaint)
        canvas.restore()
    }

    private fun getTile(x: Int, y: Int, z: Int): Bitmap? {
        val maxTiles = 1 shl z
        val wrappedX = (x % maxTiles + maxTiles) % maxTiles
        if (y < 0 || y >= maxTiles) return null

        val key = "mf/$z/$wrappedX/$y"
        synchronized(sharedTileCache) {
            sharedTileCache.get(key)?.let { return it }
        }

        val now = System.currentTimeMillis()
        val lastFailureTime = failedTiles[key]
        if (lastFailureTime != null && now - lastFailureTime < 15000L) {
            return null
        }

        if (pendingRequests.add(key)) {
            executor.submit {
                try {
                    val renderer = databaseRenderer
                    val ds = activeMapDataStore
                    val graphicFactory = try { AndroidGraphicFactory.INSTANCE } catch (_: Exception) { null }
                    if (ds != null && z >= 5) {
                        extractCityLabelsFromTile(ds, wrappedX, y, z)
                    }
                    val mfTileBitmap = if (renderer != null && ds != null && graphicFactory != null) {
                        try {
                            val mfTile = Tile(wrappedX, y, z.toByte(), TILE_SIZE)
                            val themeFuture = obtainThemeFuture(graphicFactory)
                            val job = RendererJob(
                                mfTile,
                                ds,
                                themeFuture,
                                displayModel,
                                1.0f,
                                false,
                                false
                            )
                            val mfBitmap = renderer.executeJob(job)
                            if (mfBitmap != null) {
                                AndroidGraphicFactory.getBitmap(mfBitmap)
                            } else null
                        } catch (e: Exception) {
                            Log.w(TAG, "Mapsforge vector tile render failed for $key", e)
                            null
                        }
                    } else null

                    if (mfTileBitmap != null) {
                        synchronized(sharedTileCache) {
                            sharedTileCache.put(key, mfTileBitmap)
                        }
                        failedTiles.remove(key)
                        tileRetries.remove(key)
                        patchTileIntoBasemap(x, y, z, mfTileBitmap)
                        scheduleTileRedraw()
                    } else {
                        // Fallback to online raster tile fetch
                        val urlString = tileUrlTemplate
                            .replace("{z}", z.toString())
                            .replace("{x}", wrappedX.toString())
                            .replace("{y}", y.toString())
                        val http = client
                        if (http != null) {
                            runBlocking {
                                val response = http.get(urlString) {
                                    header(HttpHeaders.UserAgent, "gaston-Android-Auto/1.0")
                                }
                                if (response.status.value == 200) {
                                    val bytes = response.body<ByteArray>()
                                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    if (bitmap != null) {
                                        synchronized(sharedTileCache) {
                                            sharedTileCache.put(key, bitmap)
                                        }
                                        failedTiles.remove(key)
                                        tileRetries.remove(key)
                                        patchTileIntoBasemap(x, y, z, bitmap)
                                        scheduleTileRedraw()
                                    } else {
                                        failedTiles[key] = System.currentTimeMillis()
                                        scheduleTileRedraw()
                                    }
                                } else {
                                    failedTiles[key] = System.currentTimeMillis()
                                    scheduleTileRedraw()
                                }
                            }
                        } else {
                            failedTiles[key] = System.currentTimeMillis()
                            scheduleTileRedraw()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to render or fetch tile $key", e)
                    failedTiles[key] = System.currentTimeMillis()
                    scheduleTileRedraw()
                } finally {
                    pendingRequests.remove(key)
                }
            }
        }

        return null
    }

    private fun lonToTileX(lon: Double, zoom: Int): Double =
        (lon + 180.0) / 360.0 * (1 shl zoom)

    private fun latToTileY(lat: Double, zoom: Int): Double {
        val latRad = Math.toRadians(lat)
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)
    }
}
