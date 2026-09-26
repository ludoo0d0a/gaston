package fr.geoking.gaston.radar

import android.location.Location
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.aac.DangerZone
import fr.geoking.gaston.aac.DangerZoneEvaluator
import fr.geoking.gaston.aac.RoadSafetyMessages
import fr.geoking.gaston.shared.location.haversineKm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AAC alert engine: evaluates local [DangerZone]s (no map POI search / network each tick).
 */
class DangerZoneAlertManager(
    private val settingsManager: SettingsManager,
    private val audioNotifier: RadarAudioNotifier,
) {
    private val alertedZoneIds = mutableSetOf<String>()
    private var lastLocation: Location? = null
    private var safetyTipSpokenAtMs: Long = 0L

    private val _hudState = MutableStateFlow(DangerZoneHudState())
    val hudState: StateFlow<DangerZoneHudState> = _hudState.asStateFlow()

    fun evaluateAndAlert(location: Location, zones: List<DangerZone>) {
        val settings = settingsManager.settings.value
        if (!settings.radarWarningEnabled) {
            alertedZoneIds.clear()
            lastLocation = location
            _hudState.value = DangerZoneHudState()
            return
        }

        val curLat = location.latitude
        val curLon = location.longitude

        val speedKmH = when {
            location.hasSpeed() && location.speed >= 0 -> (location.speed * 3.6).toDouble()
            lastLocation != null -> {
                val prev = lastLocation!!
                val dtSec = (location.time - prev.time) / 1000.0
                if (dtSec in 0.5..30.0) {
                    val distKm = haversineKm(prev.latitude, prev.longitude, curLat, curLon)
                    (distKm / (dtSec / 3600.0)).coerceIn(0.0, 250.0)
                } else 0.0
            }
            else -> 0.0
        }

        val bearing: Double? = when {
            location.hasBearing() && location.bearing != 0.0f -> location.bearing.toDouble()
            lastLocation != null -> {
                val prev = lastLocation!!
                val distKm = haversineKm(prev.latitude, prev.longitude, curLat, curLon)
                if (distKm > 0.005) {
                    DangerZoneEvaluator.calculateBearing(prev.latitude, prev.longitude, curLat, curLon)
                } else null
            }
            else -> null
        }

        var activeHudLimit: Int? = null
        var anyActive = false

        for (zone in zones) {
            val eval = DangerZoneEvaluator.evaluate(
                vehLat = curLat,
                vehLon = curLon,
                vehSpeedKmH = speedKmH,
                vehBearing = bearing,
                zone = zone,
            )

            if (eval.isInside) {
                anyActive = true
                if (activeHudLimit == null && eval.speedLimitKmH != null) {
                    activeHudLimit = eval.speedLimitKmH
                }
                if (zone.id !in alertedZoneIds) {
                    alertedZoneIds.add(zone.id)
                    if (eval.isOverspeed) {
                        audioNotifier.playOverSpeedBeepsAndSpeak(eval.speedLimitKmH)
                    } else {
                        audioNotifier.playOkSpeedBeeps()
                        audioNotifier.speakDangerZone(eval.speedLimitKmH)
                    }
                    maybeSpeakSafetyTip(location.time)
                }
            } else {
                if (zone.id in alertedZoneIds) {
                    // Left the extended zone (or opposite carriageway filter)
                    if (eval.distanceToCenterMeters > zone.radiusMeters * 1.2 || !eval.isAhead) {
                        alertedZoneIds.remove(zone.id)
                    }
                }
            }
        }

        _hudState.value = DangerZoneHudState(
            active = anyActive,
            speedLimitKmH = activeHudLimit,
        )
        lastLocation = location
    }

    private fun maybeSpeakSafetyTip(nowMs: Long) {
        if (nowMs - safetyTipSpokenAtMs < 30 * 60 * 1000L) return
        safetyTipSpokenAtMs = nowMs
        // Tip is visual in settings; TTS tip reserved for overspeed path only to avoid chatter.
        // Channel exists via RoadSafetyMessages for future periodic announcement.
        @Suppress("UNUSED_VARIABLE")
        val tip = RoadSafetyMessages.randomTip(nowMs)
    }

    fun clearAlerts() {
        alertedZoneIds.clear()
        lastLocation = null
        _hudState.value = DangerZoneHudState()
    }

    fun getAlertedZoneIds(): Set<String> = alertedZoneIds.toSet()
}
