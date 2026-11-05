package fr.geoking.gaston.radar

import android.os.Handler
import android.os.Looper
import android.util.Log
import fr.geoking.gaston.aac.DangerZoneRepository
import fr.geoking.gaston.aac.RadarCorridorOverlay
import fr.geoking.gaston.di.MapModuleLoader
import fr.geoking.gaston.shared.location.haversineKm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Corridor polygon colours shared by phone and Android Auto maps.
 * OSM road-following = blue; synthetic bearing ribbon (no OSM) = violet.
 */
object RadarCorridorStyle {
    const val FILL_ARGB = 0x442563EB
    val STROKE_ARGB = 0xFF1D4ED8.toInt()
    const val SYNTHETIC_FILL_ARGB = 0x447C3AED
    val SYNTHETIC_STROKE_ARGB = 0xFF6D28D9.toInt()

    fun fillArgb(synthetic: Boolean): Int =
        if (synthetic) SYNTHETIC_FILL_ARGB else FILL_ARGB

    fun strokeArgb(synthetic: Boolean): Int =
        if (synthetic) SYNTHETIC_STROKE_ARGB else STROKE_ARGB
}

/**
 * Process-wide source of the road-following zone polygons drawn on phone MapLibre and all
 * Android Auto renderers. Polygons come from [DangerZoneRepository] — the same geometry the
 * alert engine hit-tests — so what is drawn is what triggers alerts.
 */
object RadarCorridorStore {
    private const val TAG = "RadarCorridorStore"
    private const val MIN_MOVE_KM = 0.5
    private const val MIN_INTERVAL_MS = 20_000L
    private const val RADIUS_KM = 8.0

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val redrawListeners = CopyOnWriteArrayList<() -> Unit>()
    private val _rings = MutableStateFlow<List<RadarCorridorOverlay>>(emptyList())
    val rings: StateFlow<List<RadarCorridorOverlay>> = _rings.asStateFlow()

    private var lastLat = Double.NaN
    private var lastLon = Double.NaN
    private var lastAtMs = 0L
    private var inFlight = false

    /** AA canvas renderers: register so a completed fetch triggers a redraw. */
    fun addRedrawListener(listener: () -> Unit) {
        redrawListeners.addIfAbsent(listener)
    }

    fun removeRedrawListener(listener: () -> Unit) {
        redrawListeners.remove(listener)
    }

    /** Cheap + throttled: call on every location / camera update. */
    @Synchronized
    fun request(lat: Double, lon: Double) {
        if (inFlight) return
        val now = System.currentTimeMillis()
        val moved = lastLat.isNaN() || haversineKm(lat, lon, lastLat, lastLon) >= MIN_MOVE_KM
        // Re-ask periodically too: each call enriches a few more zones.
        if (!moved && now - lastAtMs < MIN_INTERVAL_MS) return
        inFlight = true
        lastLat = lat
        lastLon = lon
        lastAtMs = now
        scope.launch {
            try {
                MapModuleLoader.ensureLoaded()
                val repo = GlobalContext.get().get<DangerZoneRepository>()
                val rings = repo.roadCorridorRingsNear(lat, lon, RADIUS_KM)
                Log.i(TAG, "published ${rings.size} corridor rings at ($lat,$lon)")
                publishRings(rings)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Corridor refresh failed", e)
            } finally {
                synchronized(this@RadarCorridorStore) { inFlight = false }
            }
        }
    }

    /**
     * Dev: force-recompute the corridor for the danger zone nearest to [lat]/[lon], then
     * republish rings so phone / AA maps refresh immediately.
     */
    fun recalculateAt(
        lat: Double,
        lon: Double,
        onDone: ((matched: Boolean, ringCount: Int, synthetic: Boolean, error: Boolean) -> Unit)? = null,
    ) {
        scope.launch {
            synchronized(this@RadarCorridorStore) { inFlight = true }
            try {
                MapModuleLoader.ensureLoaded()
                val repo = GlobalContext.get().get<DangerZoneRepository>()
                val result = repo.recalculateCorridorNear(lat, lon)
                val rings = repo.roadCorridorRingsNear(lat, lon, RADIUS_KM)
                Log.i(
                    TAG,
                    "recalculated zone=${result.zoneId} rings=${result.ringCount} " +
                        "synthetic=${result.synthetic}; published ${rings.size}",
                )
                publishRings(rings)
                lastLat = lat
                lastLon = lon
                lastAtMs = System.currentTimeMillis()
                mainHandler.post {
                    onDone?.invoke(result.matched, result.ringCount, result.synthetic, false)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Corridor recalculate failed", e)
                mainHandler.post { onDone?.invoke(false, 0, false, true) }
            } finally {
                synchronized(this@RadarCorridorStore) { inFlight = false }
            }
        }
    }

    private fun publishRings(rings: List<RadarCorridorOverlay>) {
        val changed = _rings.value != rings
        _rings.value = rings
        if (changed) notifyRedrawListeners()
    }

    private fun notifyRedrawListeners() {
        val listeners = redrawListeners.toList()
        if (listeners.isEmpty()) return
        mainHandler.post {
            for (listener in listeners) {
                runCatching { listener() }
            }
        }
    }
}
