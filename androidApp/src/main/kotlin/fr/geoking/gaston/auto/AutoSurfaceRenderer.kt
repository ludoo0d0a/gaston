package fr.geoking.gaston.auto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.util.LruCache
import android.view.Surface
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.ui.BrandHelper
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
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Map renderer for Android Auto surface using OpenStreetMap tiles.
 *
 * It uses an LRU cache for bitmaps and a fixed thread pool to fetch tiles efficiently.
 * Supports north-up and heading-up via [setMapOrientation].
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

    private val mapBearingDegrees: Float
        get() = AutoMapHeading.effectiveBearing(orientationMode, headingDegrees)

    private val centerPxX: Double
        get() = visibleArea?.let { (it.left + it.right) / 2.0 } ?: (width / 2.0)

    private val centerPxY: Double
        get() = visibleArea?.let { (it.top + it.bottom) / 2.0 } ?: (height / 2.0)

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

    // Cache tile bitmaps (heading-up can need ~25+ tiles; 100 ≈ 25MB peak)
    private val tileCache = LruCache<String, Bitmap>(100)
    private val pendingRequests = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val executor = Executors.newFixedThreadPool(4)

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

    companion object {
        private const val TAG = "AutoSurfaceRenderer"
        private val NAVIGATION_BLUE = Color.parseColor("#4285F4")
        private const val MIN_DRAW_INTERVAL_MS = 33L
        const val POI_MARKER_WIDTH_PX = 96
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
        invalidate(force = true) // User interaction or explicit move should be immediate
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
        synchronized(tileCache) {
            tileCache.evictAll()
        }
        invalidate()
    }

    fun updateUserLocation(newLat: Double, newLon: Double, heading: Float = userHeadingDegrees) {
        val normalizedHeading = AutoMapHeading.normalizeDegrees(heading)
        if (userLat == newLat && userLon == newLon && userHeadingDegrees == normalizedHeading) return
        userLat = newLat
        userLon = newLon
        userHeadingDegrees = normalizedHeading
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
                    val canDraw = forceRedraw || (needsRedraw && timeSinceLastDraw >= MIN_DRAW_INTERVAL_MS)

                    if (canDraw) break

                    try {
                        if (needsRedraw && !forceRedraw) {
                            val waitTime = MIN_DRAW_INTERVAL_MS - timeSinceLastDraw
                            if (waitTime > 0) {
                                (this as java.lang.Object).wait(waitTime)
                                continue
                            }
                        }
                        (this as java.lang.Object).wait()
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
                drawMapTiles(canvas)
                drawSearchRadius(canvas)
                drawPaths(canvas)
                drawPois(canvas)
                drawUserLocation(canvas)
                if (bearing != 0f) {
                    canvas.restore()
                }
            } finally {
                try { surface.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
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

    private fun drawSearchRadius(canvas: Canvas) {
        val radiusKm = searchRadiusKm ?: return
        val cLat = searchRadiusCenterLat ?: return
        val cLon = searchRadiusCenterLon ?: return
        if (radiusKm <= 0.0) return

        val tileSize = 256
        val mapCenterX = lonToTileX(lon, zoom)
        val mapCenterY = latToTileY(lat, zoom)
        val tileX = lonToTileX(cLon, zoom)
        val tileY = latToTileY(cLat, zoom)
        val cx = ((tileX - mapCenterX) * tileSize + centerPxX).toFloat()
        val cy = ((tileY - mapCenterY) * tileSize + centerPxY).toFloat()
        val radiusPx = AutoMapCamera.radiusPxForKm(cLat, zoom, radiusKm)
        if (radiusPx < 2f) return
        canvas.drawCircle(cx, cy, radiusPx, searchRadiusPaint)
    }

    private fun drawPaths(canvas: Canvas) {
        if (historyPoints.isEmpty() && itineraryPoints.isEmpty()) return

        val tileSize = 256
        val centerX = lonToTileX(lon, zoom)
        val centerY = latToTileY(lat, zoom)

        fun drawPoints(points: List<Pair<Double, Double>>) {
            if (points.size < 2) return
            reusablePath.reset()
            var first = true
            points.forEach { (pLat, pLon) ->
                val tileX = lonToTileX(pLon, zoom)
                val tileY = latToTileY(pLat, zoom)
                val x = ((tileX - centerX) * tileSize + centerPxX).toFloat()
                val y = ((tileY - centerY) * tileSize + centerPxY).toFloat()
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

    private fun drawMapTiles(canvas: Canvas) {
        val tileSize = 256
        val centerX = lonToTileX(lon, zoom)
        val centerY = latToTileY(lat, zoom)
        val radiusPx = tileFetchRadiusPx()

        val startTileX = floor(centerX - radiusPx / tileSize).toInt()
        val endTileX = ceil(centerX + radiusPx / tileSize).toInt()
        val startTileY = floor(centerY - radiusPx / tileSize).toInt()
        val endTileY = ceil(centerY + radiusPx / tileSize).toInt()

        for (x in startTileX..endTileX) {
            for (y in startTileY..endTileY) {
                val bitmap = getTile(x, y, zoom)
                val drawX = ((x - centerX) * tileSize + centerPxX).toFloat()
                val drawY = ((y - centerY) * tileSize + centerPxY).toFloat()
                if (bitmap != null) {
                    canvas.drawBitmap(bitmap, drawX, drawY, null)
                } else {
                    canvas.drawRect(
                        drawX,
                        drawY,
                        drawX + tileSize,
                        drawY + tileSize,
                        backgroundPaint
                    )
                }
            }
        }
    }

    private fun drawPois(canvas: Canvas) {
        val tileSize = 256
        val centerX = lonToTileX(lon, zoom)
        val centerY = latToTileY(lat, zoom)
        val bearing = mapBearingDegrees

        val markerWidthPx = POI_MARKER_WIDTH_PX

        pois.forEach { poi ->
            val tileX = lonToTileX(poi.longitude, zoom)
            val tileY = latToTileY(poi.latitude, zoom)

            val drawX = ((tileX - centerX) * tileSize + centerPxX).toFloat()
            val drawY = ((tileY - centerY) * tileSize + centerPxY).toFloat()

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
            if (drawX < -pad || drawX > width + pad || drawY < -pad || drawY > height + pad) {
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

        val tileSize = 256
        val centerX = lonToTileX(lon, zoom)
        val centerY = latToTileY(lat, zoom)

        val tileX = lonToTileX(uLon, zoom)
        val tileY = latToTileY(uLat, zoom)

        val drawX = ((tileX - centerX) * tileSize + centerPxX).toFloat()
        val drawY = ((tileY - centerY) * tileSize + centerPxY).toFloat()

        val rotation = userHeadingDegrees

        canvas.save()
        canvas.translate(drawX, drawY)
        canvas.rotate(rotation)

        canvas.drawPath(arrowPath, userLocationPaint)
        canvas.drawPath(arrowPath, userLocationStrokePaint)
        canvas.restore()
    }

    private fun getTile(x: Int, y: Int, z: Int): Bitmap? {
        val key = "$z/$x/$y"
        synchronized(tileCache) {
            tileCache.get(key)?.let { return it }
        }

        if (pendingRequests.add(key)) {
            executor.submit {
                try {
                    val urlString = tileUrlTemplate
                        .replace("{z}", z.toString())
                        .replace("{x}", x.toString())
                        .replace("{y}", y.toString())
                    val url = URL(urlString)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.setRequestProperty("User-Agent", "gaston-Android-Auto/1.0")
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.connect()
                    if (connection.responseCode == 200) {
                        val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                        if (bitmap != null) {
                            synchronized(tileCache) {
                                tileCache.put(key, bitmap)
                            }
                            scheduleTileRedraw()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch tile $key", e)
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
