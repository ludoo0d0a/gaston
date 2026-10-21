package fr.geoking.gaston.traffic

import android.location.Location
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.traffic.TrafficAlertCopy
import fr.geoking.gaston.api.traffic.TrafficEvent
import fr.geoking.gaston.feature.notification.NotificationHelper
import fr.geoking.gaston.radar.RadarAudioNotifier
import fr.geoking.gaston.shared.location.haversineKm

/**
 * Approach alerts for traffic incidents: beep + TTS + HUN at 2 km / 1 km / 500 m / 200 m.
 *
 * Owned by [TrafficAlertCoordinator]. One alert per event per threshold; cold-start mid-approach
 * speaks only the closest already-reached threshold (avoids spamming farther bands).
 */
class TrafficAlertManager(
    private val settingsManager: SettingsManager,
    private val audioNotifier: RadarAudioNotifier,
    private val notificationHelper: NotificationHelper? = null,
) {
    companion object {
        /** Warn distances, farthest → closest. */
        val ALERT_THRESHOLDS_METERS: List<Double> = listOf(2000.0, 1000.0, 500.0, 200.0)
        private const val CLEAR_HYSTERESIS_FACTOR = 1.2
    }

    /** Per event id → thresholds already fired this approach. */
    private val firedThresholdsByEventId = mutableMapOf<String, MutableSet<Double>>()
    private val knownEventIds = mutableSetOf<String>()

    fun evaluateAndAlert(location: Location, events: List<TrafficEvent>) {
        val settings = settingsManager.settings.value
        if (!settings.trafficAlertEnabled) {
            clearAlerts()
            return
        }
        val isDevMode = settings.isDevMode
        val curLat = location.latitude
        val curLon = location.longitude
        val seenThisTick = mutableSetOf<String>()

        for (event in events) {
            if (!TrafficAlertCopy.isAlertable(event.severity)) continue
            val lat = event.poiLatitude ?: continue
            val lon = event.poiLongitude ?: continue
            val eventId = eventAlertId(event) ?: continue
            seenThisTick.add(eventId)

            val distanceMeters = haversineKm(curLat, curLon, lat, lon) * 1000.0
            val fired = firedThresholdsByEventId.getOrPut(eventId) { mutableSetOf() }

            if (eventId !in knownEventIds) {
                knownEventIds.add(eventId)
                for (threshold in ALERT_THRESHOLDS_METERS) {
                    if (distanceMeters <= threshold) fired.add(threshold)
                }
                val closestReached = fired.minOrNull()
                if (closestReached != null) {
                    fireAlert(event, closestReached, isDevMode)
                }
                continue
            }

            if (distanceMeters > ALERT_THRESHOLDS_METERS.first() * CLEAR_HYSTERESIS_FACTOR) {
                // Far again — allow re-alert on next approach.
                fired.clear()
                continue
            }

            for (threshold in ALERT_THRESHOLDS_METERS) {
                if (threshold in fired) continue
                if (distanceMeters <= threshold) {
                    fired.add(threshold)
                    fireAlert(event, threshold, isDevMode)
                    break
                }
            }
        }

        // Drop state for events no longer in the feed.
        val stale = knownEventIds.filter { it !in seenThisTick }
        for (id in stale) {
            knownEventIds.remove(id)
            firedThresholdsByEventId.remove(id)
        }
    }

    private fun fireAlert(event: TrafficEvent, thresholdMeters: Double, isDevMode: Boolean) {
        val phrase = TrafficAlertCopy.frAlert(event.severity, thresholdMeters) ?: return
        val spoken = if (isDevMode) "[Gaston] $phrase" else phrase
        audioNotifier.playOkSpeedBeeps(thresholdMeters)
        audioNotifier.speakPhrase(spoken)
        notificationHelper?.showTrafficAlertNotification(
            message = phrase,
            latitude = event.poiLatitude,
            longitude = event.poiLongitude,
            isDevMode = isDevMode,
        )
    }

    fun clearAlerts() {
        firedThresholdsByEventId.clear()
        knownEventIds.clear()
    }

    fun getFiredThresholds(eventId: String): Set<Double> =
        firedThresholdsByEventId[eventId]?.toSet().orEmpty()

    fun getKnownEventIds(): Set<String> = knownEventIds.toSet()

    private fun eventAlertId(event: TrafficEvent): String? {
        val sourceId = event.sourceId?.takeIf { it.isNotBlank() }
        if (sourceId != null) return sourceId
        val lat = event.poiLatitude ?: return null
        val lon = event.poiLongitude ?: return null
        return "traffic_${event.severity}_${event.roadRef}_${"%.4f".format(lat)}_${"%.4f".format(lon)}"
    }
}
