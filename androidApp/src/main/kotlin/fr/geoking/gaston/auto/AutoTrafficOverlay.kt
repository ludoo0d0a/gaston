package fr.geoking.gaston.auto

import android.util.Log
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.api.traffic.TrafficEvent
import fr.geoking.gaston.api.traffic.TrafficProviderFactory
import fr.geoking.gaston.api.traffic.TrafficRequest
import fr.geoking.gaston.api.traffic.toMapPois
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi

/**
 * Loads traffic overlays for Android Auto map screens (CITA / TIPI / TomTom).
 */
object AutoTrafficOverlay {

    suspend fun loadEvents(
        factory: TrafficProviderFactory?,
        settings: AppSettings,
        centerLat: Double,
        centerLon: Double,
        viewport: MapViewport?,
    ): List<TrafficEvent> {
        if (!settings.mapTrafficEnabled || factory == null) return emptyList()
        return try {
            val providers = factory.getProviders(
                centerLat,
                centerLon,
                selectedProviders = settings.selectedPoiProviders,
            )
            if (providers.isEmpty()) return emptyList()
            val latMin = viewport?.minLat ?: (centerLat - 0.15)
            val latMax = viewport?.maxLat ?: (centerLat + 0.15)
            val lonMin = viewport?.minLng ?: (centerLon - 0.15)
            val lonMax = viewport?.maxLng ?: (centerLon + 0.15)
            val info = TrafficProviderFactory.mergeTraffic(
                providers,
                TrafficRequest.Bbox(
                    latMin = latMin,
                    lonMin = lonMin,
                    latMax = latMax,
                    lonMax = lonMax,
                ),
            )
            info?.events.orEmpty()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "Traffic overlay fetch failed", e)
            emptyList()
        }
    }

    fun toMapPois(events: List<TrafficEvent>): List<Poi> =
        fr.geoking.gaston.api.traffic.TrafficInfo(events = events, providerId = "aa_traffic")
            .toMapPois()

    private const val TAG = "AutoTrafficOverlay"
}
