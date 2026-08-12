package fr.geoking.gaston.auto

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
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.tan

data class TileError(
    val url: String,
    val statusCode: Int,
    val errorMessage: String,
    val timestamp: Long
)

/**
 * Map renderer for Android Auto surface using OpenStreetMap tiles.
 *
 * It uses an LRU cache for bitmaps and a fixed thread pool to fetch tiles efficiently.
 * Supports north-up and heading-up via [setMapOrientation].
 * Same-zoom pans reuse a composed basemap framebuffer (scroll + edge fill).
 */
class AutoSurfaceRenderer(
    private val context: Context,
    private val surface: Surface,
    private val width: Int,
    private val height: Int,
    initialLat: Double = 48.8566,
    initialLon: Double = 2.3522,
    /** XYZ raster tile URL template with {z}, {x}, {y} placeholders. */
    private var tileUrlTemplate: String = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
) {
    @Volatile
    private var running = true
    @Volatile
    private var mapTileDebugEnabled = false
    private var needsRedraw = false
    private var forceRedraw = false
    private var tileRedrawPending = false
    private var lastDrawMillis: Long = 0
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

    private var historyPoints: List<Pair<Double, Double>> = emptyList()
    private var itineraryPoints: List<Pair<Double, Double>> = emptyList()
    private var searchRadiusCenterLat: Double? = null
    private var searchRadiusCenterLon: Double? = null
    private var searchRadiusKm: Double? = null
    private var queryPending: Boolean = false

    private val pendingRequests = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val executor = Executors.newFixedThreadPool(4)

    /** Composed north-up basemap; scrolled on same-zoom pans. */
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

    private val backgroundPaint = Paint().apply { color = Color.LTGRAY }
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
    private val drawThread = Thread(::runDrawLoop, "AutoSurfaceRenderer")

    private val debugTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 18f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    companion object {
        private const val TAG = "AutoSurfaceRenderer"
        private val NAVIGATION_BLUE = Color.parseColor("#4285F4")
        private const val MIN_DRAW_INTERVAL_MS = 33L
        private const val TILE_SIZE = 256
        const val POI_MARKER_WIDTH_PX = 96

        // Shared LRU cache for tiles across all instances of AutoSurfaceRenderer
        private val sharedTileCache = LruCache<String, Bitmap>(250)
        private val failedTiles = ConcurrentHashMap<String, Long>()

        // Tile retry tracking and diagnostics for debugging
        val tileRetries = ConcurrentHashMap<String, Int>()
        private val recentTileErrors = Collections.synchronizedList(mutableListOf<TileError>())

        fun getRecentTileErrors(): List<TileError> = synchronized(recentTileErrors) {
            recentTileErrors.toList()
        }

        fun clearRecentTileErrors() {
            synchronized(recentTileErrors) {
                recentTileErrors.clear()
            }
        }

        fun logTileError(url: String, statusCode: Int, errorMessage: String) {
            val error = TileError(url, statusCode, errorMessage, System.currentTimeMillis())
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

    fun start() {
        if (!drawThread.isAlive) drawThread.start()
    }

    fun stop() {
        synchronized(this) {
            running = false
            (this as java.lang.Object).notifyAll()
        }
        executor.shutdownNow()
        try { drawThread.join(500) } catch (_: Exception) {}
        recycleBasemap()
    }

    fun invalidate(force: Boolean = false) {
        synchronized(this) {
            needsRedraw = true
            if (force) forceRedraw = true
            tileRedrawPending = false
            (this as java.lang.Object).notifyAll()
        }
    }

    /** Incremental redraw when a tile finishes loading; does not flash the full gray background. */
    private fun scheduleTileRedraw() {
        synchronized(this) {
            if (!tileRedrawPending) {
                tileRedrawPending = true
                needsRedraw = true
                (this as java.lang.Object).notifyAll()
            }
        }
    }

    fun updateLocation(newLat: Double, newLon: Double, newZoom: Int = 13) {
        if (lat == newLat && lon == newLon && zoom == newZoom) return
        lat = newLat
        lon = newLon
        zoom = newZoom
        lastPositionUpdateTime = System.currentTimeMillis()
        invalidate() // Throttled to prevent too frequent redraws when position is moving
    }

    fun setMapOrientation(mode: MapOrientationMode, headingDegrees: Float = this.headingDegrees) {
        val normalizedHeading = AutoMapHeading.normalizeDegrees(headingDegrees)
        if (orientationMode == mode && this.headingDegrees == normalizedHeading) return
        orientationMode = mode
        this.headingDegrees = normalizedHeading
        invalidate(force = true)
    }

    fun updateHeading(headingDegrees: Float) {
        val normalizedHeading = AutoMapHeading.normalizeDegrees(headingDegrees)
        if (this.headingDegrees == normalizedHeading) return
        this.headingDegrees = normalizedHeading
        invalidate()
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
        selectedId: String? = selectedPoiId
    ) {
        val newIds = newPois.map { it.id }
        if (poiIds == newIds &&
            this.effectiveEnergyTypes == effectiveEnergyTypes &&
            this.effectivePowerLevels == effectivePowerLevels &&
            this.selectedPoiId == selectedId
        ) {
            return
        }
        pois = newPois
        poiIds = newIds
        this.effectiveEnergyTypes = effectiveEnergyTypes
        this.effectivePowerLevels = effectivePowerLevels
        this.selectedPoiId = selectedId
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
        invalidate()
    }

    /** Shows a small spinner overlay while a POI query is in flight. */
    fun setQueryPending(pending: Boolean) {
        if (queryPending == pending) return
        queryPending = pending
        invalidate(force = true)
    }

    fun addHistoryPoint(pLat: Double, pLon: Double) {
        val last = historyPoints.lastOrNull()
        if (last != null) {
            // Basic optimization: skip if very close (approx < 10-20m)
            if (abs(last.first - pLat) < 0.0002 && abs(last.second - pLon) < 0.0002) return
        }
        historyPoints = historyPoints + (pLat to pLon)
        invalidate()
    }

    private fun runDrawLoop() {
        while (running) {
            synchronized(this) {
                while (running) {
                    val now = System.currentTimeMillis()
                    val timeSinceLastDraw = now - lastDrawMillis
                    val isMoving = now - lastPositionUpdateTime < 2000L
                    val currentMinInterval = if (isMoving) 2000L else MIN_DRAW_INTERVAL_MS
                    val canDraw = forceRedraw || (needsRedraw && timeSinceLastDraw >= currentMinInterval)

                    if (canDraw) break

                    try {
                        if (needsRedraw && !forceRedraw) {
                            val waitTime = currentMinInterval - timeSinceLastDraw
                            if (waitTime > 0) {
                                (this as java.lang.Object).wait(waitTime)
                                continue
                            }
                        }
                        val nextCheckTime = lastMissingTileCheckTime + 2000L
                        val remaining = nextCheckTime - System.currentTimeMillis()
                        if (hasMissingTiles && remaining > 0) {
                            (this as java.lang.Object).wait(remaining.coerceAtMost(2000L))
                        } else {
                            (this as java.lang.Object).wait()
                        }
                    } catch (e: InterruptedException) {
                        return
                    }
                }
                needsRedraw = false
                forceRedraw = false
                tileRedrawPending = false
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
                drawPois(canvas)
                drawUserLocation(canvas)
                if (bearing != 0f) {
                    canvas.restore()
                }

                // Draw map overlay widgets (scale, compass, debug zoom)
                AutoMapOverlayHelper.drawCompassAndScale(
                    canvas = canvas,
                    context = context,
                    visibleArea = visibleArea,
                    surfaceWidth = width,
                    surfaceHeight = height,
                    bearing = bearing,
                    zoom = zoom.toFloat(),
                    latitude = lat,
                    mapTileDebugEnabled = mapTileDebugEnabled,
                    isDensityScaled = false
                )

                if (queryPending) {
                    AutoMapQueryLoader.draw(
                        canvas = canvas,
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

    /** Half-extent in pixels used to fetch tiles (larger when rotated). */
    private fun tileFetchRadiusPx(): Double {
        if (mapBearingDegrees == 0f) {
            return max(centerPxX, width - centerPxX).coerceAtLeast(max(centerPxY, height - centerPxY))
        }
        return hypot(width.toDouble(), height.toDouble()) / 2.0
    }

    /**
     * Basemap size covers the tile fetch radius so heading-up rotation still has corner tiles.
     * Origin of map-space (0,0) on the bitmap is stored in [composedMapOriginX]/[composedMapOriginY].
     */
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

        // Map-space origin of the basemap bitmap (surface 0,0 relative).
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

        scratch.eraseColor(Color.LTGRAY)
        Canvas(scratch).drawBitmap(basemap, dxF, dyF, null)

        // Swap
        basemapBitmap = scratch
        basemapScratch = basemap

        val scrolled = basemapBitmap!!
        val canvas = Canvas(scrolled)

        // Newly exposed strips in bitmap coordinates.
        if (dx > 0) {
            // Content moved right → left strip exposed
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

    /**
     * Draws tiles whose bitmap rect intersects the clip rect (bitmap coordinates).
     * [mapCenterPxX]/[mapCenterPxY] are surface map-space coords of the camera center;
     * [mapOriginX]/[mapOriginY] are the surface coords of bitmap (0,0).
     */
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
            isLoaded -> "OK"
            isPending -> context.getString(fr.geoking.gaston.R.string.tile_status_loading)
            isFailed -> context.getString(fr.geoking.gaston.R.string.tile_status_failed)
            else -> context.getString(fr.geoking.gaston.R.string.tile_status_not_requested)
        }
        canvas.drawText("Z:$z X:$x Y:$y", drawX + 10f, drawY + 25f, textPaint)
        canvas.drawText(
            context.getString(fr.geoking.gaston.R.string.tile_status_label, statusText),
            drawX + 10f,
            drawY + 45f,
            textPaint
        )
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

        // Bitmap pixel (bx, by) corresponds to surface map-space (bx + mapOriginX, by + mapOriginY).
        // Tile x draws at surface: (x - centerTileX) * TILE + mapCenterPxX
        // In bitmap coords: that minus mapOrigin.
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
                val isValidY = y in 0 until maxTiles
                val key = "$tileUrlTemplate/$zoom/$wrappedX/$y"

                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, drawX, drawY, null)

                    if (mapTileDebugEnabled) {
                        drawTileDebugGrid(canvas, drawX, drawY, x, y, zoom, key, isLoaded = true)
                    }
                } else {
                    hasMissingTiles = true
                    val retries = if (isValidY) tileRetries[key] ?: 0 else 0

                    if (retries > 0) {
                        canvas.drawRect(drawX, drawY, drawX + TILE_SIZE, drawY + TILE_SIZE, backgroundPaint)
                        canvas.drawText(
                            "loading $retries",
                            drawX + TILE_SIZE / 2f,
                            drawY + TILE_SIZE / 2f + debugTextPaint.textSize / 2f,
                            debugTextPaint
                        )

                        if (mapTileDebugEnabled) {
                            drawTileDebugGrid(canvas, drawX, drawY, x, y, zoom, key, isLoaded = false)
                        }
                    } else {
                        var fallbackDrawn = false
                        // 1. Try lower zoom levels (parent tiles) first to fill the entire tile area
                        for (levelDiff in 1..4) {
                            val fallbackZ = zoom - levelDiff
                            if (fallbackZ < 0) break
                            val scale = 1 shl levelDiff
                            val pX = if (x < 0 && x % scale != 0) x / scale - 1 else x / scale
                            val pY = y shr levelDiff
                            val maxTilesP = 1 shl fallbackZ
                            val wrappedPX = (pX % maxTilesP + maxTilesP) % maxTilesP
                            val parentKey = "$tileUrlTemplate/$fallbackZ/$wrappedPX/$pY"
                            val parentBitmap = synchronized(sharedTileCache) { sharedTileCache.get(parentKey) }
                            if (parentBitmap != null) {
                                val subX = x - pX * scale
                                val subY = y - pY * scale
                                val subSize = TILE_SIZE.toDouble() / scale
                                val srcRect = Rect(
                                    (subX * subSize).toInt(),
                                    (subY * subSize).toInt(),
                                    ((subX + 1) * subSize).toInt(),
                                    ((subY + 1) * subSize).toInt()
                                )
                                val dstRect = Rect(
                                    drawX.toInt(),
                                    drawY.toInt(),
                                    (drawX + TILE_SIZE).toInt(),
                                    (drawY + TILE_SIZE).toInt()
                                )
                                canvas.drawBitmap(parentBitmap, srcRect, dstRect, null)
                                fallbackDrawn = true
                                break
                            }
                        }

                        // 2. If no parent tile was found, try higher zoom levels (child tiles)
                        if (!fallbackDrawn) {
                            // Fill with background paint first in case only some child tiles are available
                            canvas.drawRect(
                                drawX,
                                drawY,
                                drawX + TILE_SIZE,
                                drawY + TILE_SIZE,
                                backgroundPaint
                            )

                            val childZ1 = zoom + 1
                            val maxTilesC1 = 1 shl childZ1
                            val subSize1 = TILE_SIZE / 2
                            var childDrawnCount = 0
                            for (dx in 0..1) {
                                for (dy in 0..1) {
                                    val childX = x * 2 + dx
                                    val childY = y * 2 + dy
                                    val wrappedChildX = (childX % maxTilesC1 + maxTilesC1) % maxTilesC1
                                    val childKey = "$tileUrlTemplate/$childZ1/$wrappedChildX/$childY"
                                    val childBitmap = synchronized(sharedTileCache) { sharedTileCache.get(childKey) }
                                    if (childBitmap != null) {
                                        val dstRect = Rect(
                                            (drawX + dx * subSize1).toInt(),
                                            (drawY + dy * subSize1).toInt(),
                                            (drawX + (dx + 1) * subSize1).toInt(),
                                            (drawY + (dy + 1) * subSize1).toInt()
                                        )
                                        canvas.drawBitmap(childBitmap, null, dstRect, null)
                                        childDrawnCount++
                                    }
                                }
                            }

                            // 3. If still no child tiles at zoom + 1, check zoom + 2
                            if (childDrawnCount == 0) {
                                val childZ2 = zoom + 2
                                val maxTilesC2 = 1 shl childZ2
                                val subSize2 = TILE_SIZE / 4
                                for (dx in 0..3) {
                                    for (dy in 0..3) {
                                        val childX = x * 4 + dx
                                        val childY = y * 4 + dy
                                        val wrappedChildX = (childX % maxTilesC2 + maxTilesC2) % maxTilesC2
                                        val childKey = "$tileUrlTemplate/$childZ2/$wrappedChildX/$childY"
                                        val childBitmap = synchronized(sharedTileCache) { sharedTileCache.get(childKey) }
                                        if (childBitmap != null) {
                                            val dstRect = Rect(
                                                (drawX + dx * subSize2).toInt(),
                                                (drawY + dy * subSize2).toInt(),
                                                (drawX + (dx + 1) * subSize2).toInt(),
                                                (drawY + (dy + 1) * subSize2).toInt()
                                            )
                                            canvas.drawBitmap(childBitmap, null, dstRect, null)
                                        }
                                    }
                                }
                            }
                        }

                        if (mapTileDebugEnabled) {
                            drawTileDebugGrid(canvas, drawX, drawY, x, y, zoom, key, isLoaded = false)
                        }
                    }
                }
            }
        }
        canvas.restore()
    }

    /** Patch a newly fetched tile into the composed basemap when still valid for current zoom. */
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

            // Skip if completely outside basemap.
            if (drawX >= basemap.width || drawY >= basemap.height ||
                drawX + TILE_SIZE <= 0 || drawY + TILE_SIZE <= 0
            ) {
                return
            }

            Canvas(basemap).drawBitmap(tileBitmap, drawX, drawY, null)
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

    private fun drawPois(canvas: Canvas) {
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
                sizePx = markerWidthPx
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
                // Rotate back so the icon remains vertical.
                // The canvas is already rotated by -bearing around (centerPxX, centerPxY).
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

    /** POIs at [screenX]/[screenY], nearest first; empty if outside [visibleArea] or no hit. */
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

        val key = "$tileUrlTemplate/$z/$wrappedX/$y"
        synchronized(sharedTileCache) {
            sharedTileCache.get(key)?.let { return it }
        }

        val now = System.currentTimeMillis()
        val lastFailureTime = failedTiles[key]
        if (lastFailureTime != null && now - lastFailureTime < 15000L) {
            return null
        }

        // Prune expired entries from failedTiles occasionally to prevent memory leak
        if (failedTiles.size > 200) {
            val threshold = now - 15000L
            failedTiles.entries.removeIf { it.value < threshold }
        }

        if (pendingRequests.add(key)) {
            executor.submit {
                val urlString = tileUrlTemplate
                    .replace("{z}", z.toString())
                    .replace("{x}", wrappedX.toString())
                    .replace("{y}", y.toString())
                try {
                    val url = URL(urlString)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.setRequestProperty("User-Agent", "gaston-Android-Auto/1.0")
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.connect()
                    if (connection.responseCode == 200) {
                        val bitmap = BitmapFactory.decodeStream(connection.inputStream)
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
                            val count = (tileRetries[key] ?: 0) + 1
                            tileRetries[key] = count
                            logTileError(urlString, 200, "Failed to decode Bitmap stream")
                            scheduleTileRedraw()
                        }
                    } else {
                        failedTiles[key] = System.currentTimeMillis()
                        val count = (tileRetries[key] ?: 0) + 1
                        tileRetries[key] = count
                        logTileError(urlString, connection.responseCode, connection.responseMessage ?: "HTTP Error")
                        scheduleTileRedraw()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch tile $key", e)
                    failedTiles[key] = System.currentTimeMillis()
                    val count = (tileRetries[key] ?: 0) + 1
                    tileRetries[key] = count
                    logTileError(urlString, -1, e.message ?: "Unknown Connection Exception")
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
