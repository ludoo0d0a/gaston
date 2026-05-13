package fr.geoking.gaston.auto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import android.util.LruCache
import android.view.Surface
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.ui.BrandHelper
import fr.geoking.gaston.ui.map.MarkerStyle
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
    private var lat: Double = initialLat
    private var lon: Double = initialLon
    private var zoom: Int = 13
    private var userLat: Double? = null
    private var userLon: Double? = null
    private var visibleArea: Rect? = null

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
    private val drawThread = Thread(::runDrawLoop, "AutoSurfaceRenderer")

    fun start() {
        if (!drawThread.isAlive) drawThread.start()
    }

    fun stop() {
        running = false
        executor.shutdownNow()
        try { drawThread.join(500) } catch (_: Exception) {}
    }

    fun updateLocation(newLat: Double, newLon: Double, newZoom: Int = 13) {
        lat = newLat
        lon = newLon
        zoom = newZoom
    }

    fun setTileUrlTemplate(template: String) {
        if (tileUrlTemplate != template) {
            tileUrlTemplate = template
            synchronized(tileCache) {
                tileCache.evictAll()
            }
        }
    }

    fun updateUserLocation(newLat: Double, newLon: Double) {
        userLat = newLat
        userLon = newLon
    }

    fun updateVisibleArea(area: Rect) {
        visibleArea = area
    }

    fun updatePois(
        newPois: List<Poi>,
        effectiveEnergyTypes: Set<String>,
        effectivePowerLevels: Set<Int>
    ) {
        this.pois = newPois
        this.effectiveEnergyTypes = effectiveEnergyTypes
        this.effectivePowerLevels = effectivePowerLevels
    }

    private fun runDrawLoop() {
        while (running) {
            val canvas = try { surface.lockCanvas(null) } catch (_: Exception) { null } ?: break
            try {
                drawMap(canvas)
                drawPois(canvas)
                drawUserLocation(canvas)
            } finally {
                try { surface.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
            }
            try { Thread.sleep(100) } catch (_: InterruptedException) { break }
        }
    }

    private fun drawMap(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val tileSize = 256
        val centerX = lonToTileX(lon, zoom)
        val centerY = latToTileY(lat, zoom)

        val startTileX = floor(centerX - centerPxX / tileSize).toInt()
        val endTileX = ceil(centerX + (width - centerPxX) / tileSize).toInt()
        val startTileY = floor(centerY - centerPxY / tileSize).toInt()
        val endTileY = ceil(centerY + (height - centerPxY) / tileSize).toInt()

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

            // Draw bitmap centered on POI.
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

        val radius = 16f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        canvas.drawCircle(drawX, drawY, radius, paint)
        canvas.drawCircle(drawX, drawY, radius, strokePaint)
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
