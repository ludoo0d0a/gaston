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
import fr.geoking.gaston.ui.BrandHelper
import fr.geoking.gaston.ui.map.PoiMarkerHelper
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.*

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

    private var pois: List<Poi> = emptyList()
    private var effectiveEnergyTypes: Set<String> = emptySet()
    private var effectivePowerLevels: Set<Int> = emptySet()

    // Cache up to 50 tile bitmaps (approx 50 * 256*256*4 bytes ~ 12MB)
    private val tileCache = LruCache<String, Bitmap>(50)
    // Tracks active network requests to avoid duplicates
    private val pendingRequests = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    // Fixed thread pool for network I/O
    private val executor = Executors.newFixedThreadPool(4)

    private val backgroundPaint = Paint().apply { color = Color.LTGRAY }
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
    private val drawThread = Thread(::runDrawLoop, "AutoSurfaceRenderer")

    companion object {
        private val NAVIGATION_BLUE = Color.parseColor("#4285F4")
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

    fun invalidate() {
        synchronized(this) {
            needsRedraw = true
            (this as java.lang.Object).notifyAll()
        }
    }

    fun updateLocation(newLat: Double, newLon: Double, newZoom: Int = 13) {
        if (lat == newLat && lon == newLon && zoom == newZoom) return
        lat = newLat
        lon = newLon
        zoom = newZoom
        invalidate()
    }

    fun setMapOrientation(mode: MapOrientationMode, headingDegrees: Float = this.headingDegrees) {
        val normalizedHeading = AutoMapHeading.normalizeDegrees(headingDegrees)
        if (orientationMode == mode && this.headingDegrees == normalizedHeading) return
        orientationMode = mode
        this.headingDegrees = normalizedHeading
        invalidate()
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
        if (visibleArea == area) return
        visibleArea = area
        invalidate()
    }

    fun updatePois(
        newPois: List<Poi>,
        effectiveEnergyTypes: Set<String>,
        effectivePowerLevels: Set<Int>
    ) {
        if (this.pois == newPois &&
            this.effectiveEnergyTypes == effectiveEnergyTypes &&
            this.effectivePowerLevels == effectivePowerLevels
        ) return
        this.pois = newPois
        this.effectiveEnergyTypes = effectiveEnergyTypes
        this.effectivePowerLevels = effectivePowerLevels
        invalidate()
    }

    private fun runDrawLoop() {
        while (running) {
            synchronized(this) {
                while (!needsRedraw && running) {
                    try {
                        (this as java.lang.Object).wait()
                    } catch (e: InterruptedException) {
                        return
                    }
                }
                needsRedraw = false
            }
            if (!running) break

            val canvas = try { surface.lockCanvas(null) } catch (_: Exception) { null } ?: continue
            try {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
                val bearing = mapBearingDegrees
                val cx = centerPxX.toFloat()
                val cy = centerPxY.toFloat()
                if (bearing != 0f) {
                    canvas.save()
                    canvas.rotate(-bearing, cx, cy)
                }
                drawMapTiles(canvas)
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
                if (bitmap != null) {
                    val drawX = ((x - centerX) * tileSize + centerPxX).toFloat()
                    val drawY = ((y - centerY) * tileSize + centerPxY).toFloat()
                    canvas.drawBitmap(bitmap, drawX, drawY, null)
                }
            }
        }
    }

    private fun drawPois(canvas: Canvas) {
        val tileSize = 256
        val centerX = lonToTileX(lon, zoom)
        val centerY = latToTileY(lat, zoom)

        val markerWidthPx = 72

        pois.forEach { poi ->
            val tileX = lonToTileX(poi.longitude, zoom)
            val tileY = latToTileY(poi.latitude, zoom)

            val drawX = ((tileX - centerX) * tileSize + centerPxX).toFloat()
            val drawY = ((tileY - centerY) * tileSize + centerPxY).toFloat()

            val brandInfo = BrandHelper.getBrandInfo(poi.brand)
            val iconResId = PoiMarkerHelper.headDrawableResId(poi, brandInfo)
            val bitmap = PoiMarkerHelper.vectorToBitmapCached(context, iconResId, markerWidthPx) ?: return@forEach

            val bw = bitmap.width.toFloat()
            val bh = bitmap.height.toFloat()
            val pad = markerWidthPx * 2f
            if (drawX < -pad || drawX > width + pad || drawY < -pad || drawY > height + pad) {
                return@forEach
            }

            canvas.drawBitmap(bitmap, drawX - bw / 2f, drawY - bh / 2f, null)
        }
    }

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

        val bearing = mapBearingDegrees
        val rotation = userHeadingDegrees - bearing

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
                            invalidate()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AutoSurfaceRenderer", "Failed to fetch tile $key", e)
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
