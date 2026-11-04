package fr.geoking.gaston.radar

import android.os.Handler
import android.os.Looper
import android.util.Log
import fr.geoking.gaston.aac.DangerZoneRepository
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

/** Blue road-corridor polygon colours shared by phone and Android Auto maps. */
object RadarCorridorStyle {
    const val FILL_ARGB = 0x442563EB
    val STROKE_ARGB = 0xFF1D4ED8.toInt()
}

/**
 * Process-wide source of the road-following zone polygons drawn in blue (phone MapLibre and all
 * Android Auto renderers). Polygons come from [DangerZoneRepository] — the same geometry the
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
    private val _rings = MutableStateFlow<List<List<Pair<Double, Double>>>>(emptyList())
    val rings: StateFlow<List<List<Pair<Double, Double>>>> = _rings.asStateFlow()

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
                val changed = _rings.value != rings
                _rings.value = rings
                if (changed) notifyRedrawListeners()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Corridor refresh failed", e)
            } finally {
                synchronized(this@RadarCorridorStore) { inFlight = false }
            }
        }
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
