package fr.geoking.gaston.radar

import android.location.Location
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.aac.DangerZone
import fr.geoking.gaston.aac.DangerZoneEvaluator
import fr.geoking.gaston.aac.RoadSafetyMessages
import fr.geoking.gaston.feature.notification.NotificationHelper
import fr.geoking.gaston.shared.location.haversineKm
import kotlinx.coroutines.flow.StateFlow

/**
 * AAC alert engine: evaluates local [DangerZone]s (no map POI search / network each tick).
 *
 * Always: beep + TTS.
 * Presence indicator ([hudStore] / [hudState]): zone de danger + VMA (phone Compose + AA map badge).
 * Temporary heads-up: when [notificationHelper] is set (phone shade + AA HUN via [NotificationHelper]).
 *
 * Owned by [DangerZoneAlertCoordinator] (process-wide). Fires once per zone on:
 * - zone entry (extended radius)
 * - approach ≤ [NEAR_RADAR_METERS] of zone center
 */
class DangerZoneAlertManager(
    private val settingsManager: SettingsManager,
    private val audioNotifier: RadarAudioNotifier,
    private val notificationHelper: NotificationHelper? = null,
    private val hudStore: DangerZoneHudStore = DangerZoneHudStore(),
) {
    companion object {
        /** Distance to zone center at which the "near radar" HUN fires (AA). */
        const val NEAR_RADAR_METERS = 100.0
        const val TEST_ALERT_SPEED_LIMIT_KMH = 100
    }

    private val alertedZoneIds = mutableSetOf<String>()
    private val nearAlertedZoneIds = mutableSetOf<String>()
    private var lastLocation: Location? = null
    private var safetyTipSpokenAtMs: Long = 0L

    val hudState: StateFlow<DangerZoneHudState> = hudStore.hudState

    fun evaluateAndAlert(location: Location, zones: List<DangerZone>) {
        val settings = settingsManager.settings.value
        if (!settings.radarWarningEnabled) {
            alertedZoneIds.clear()
            nearAlertedZoneIds.clear()
            lastLocation = location
            hudStore.clear()
            return
        }

        val curLat = location.latitude
        val curLon = location.longitude

        val speedKmH = when {
            location.hasSpeed() && location.speed >= 0 -> (location.speed * 3.6)
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
                    playAlertAudio(eval.isOverspeed, eval.speedLimitKmH)
                    notificationHelper?.showDangerZoneNotification(eval.speedLimitKmH)
                    maybeSpeakSafetyTip(location.time)
                }
                if (
                    eval.distanceToCenterMeters <= NEAR_RADAR_METERS &&
                    zone.id !in nearAlertedZoneIds
                ) {
                    nearAlertedZoneIds.add(zone.id)
                    playAlertAudio(eval.isOverspeed, eval.speedLimitKmH)
                    notificationHelper?.showNearRadarNotification(eval.speedLimitKmH)
                }
            } else {
                if (zone.id in alertedZoneIds) {
                    // Left the extended zone (or opposite carriageway filter)
                    if (eval.distanceToCenterMeters > zone.radiusMeters * 1.2 || !eval.isAhead) {
                        alertedZoneIds.remove(zone.id)
                        nearAlertedZoneIds.remove(zone.id)
                    }
                }
            }
        }

        hudStore.set(
            DangerZoneHudState(
                active = anyActive,
                speedLimitKmH = activeHudLimit,
            ),
        )
        lastLocation = location
    }

    /**
     * Dev test: beep + TTS + presence HUD; HUN when [notificationHelper] is set.
     */
    fun triggerTestAlert(speedLimitKmH: Int? = TEST_ALERT_SPEED_LIMIT_KMH) {
        playAlertAudio(isOverspeed = false, speedLimitKmH = speedLimitKmH)
        hudStore.set(
            DangerZoneHudState(
                active = true,
                speedLimitKmH = speedLimitKmH,
            ),
        )
        notificationHelper?.showDangerZoneNotification(speedLimitKmH)
    }

    /** Clears HUD only (used after a phone test alert when the GPS loop is idle). */
    fun dismissHud() {
        hudStore.clear()
    }

    private fun playAlertAudio(isOverspeed: Boolean, speedLimitKmH: Int?) {
        if (isOverspeed) {
            audioNotifier.playOverSpeedBeepsAndSpeak(speedLimitKmH)
        } else {
            audioNotifier.playOkSpeedBeeps()
            audioNotifier.speakDangerZone(speedLimitKmH)
        }
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
        nearAlertedZoneIds.clear()
        lastLocation = null
        hudStore.clear()
    }

    fun getAlertedZoneIds(): Set<String> = alertedZoneIds.toSet()

    fun getNearAlertedZoneIds(): Set<String> = nearAlertedZoneIds.toSet()
}
