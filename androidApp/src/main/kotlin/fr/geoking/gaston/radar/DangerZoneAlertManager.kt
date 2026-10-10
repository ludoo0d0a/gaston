package fr.geoking.gaston.radar

import android.location.Location
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.aac.DangerZone
import fr.geoking.gaston.aac.DangerZoneEvaluation
import fr.geoking.gaston.aac.DangerZoneEvaluator
import fr.geoking.gaston.aac.RoadSafetyMessages
import fr.geoking.gaston.feature.notification.NotificationHelper
import fr.geoking.gaston.shared.location.haversineKm
import fr.geoking.gaston.shared.logging.RadarDetectionLog
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
        val debugLogs = settings.debugLoggingEnabled || settings.debugBarEnabled

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
                    logDetection(RadarDetectionLog.Event.ENTRY, zone, eval, debugLogs)
                    playAlertAudio(eval.isOverspeed, eval.speedLimitKmH)
                    notificationHelper?.showDangerZoneNotification(
                        eval.speedLimitKmH,
                        latitude = zone.centerLatitude,
                        longitude = zone.centerLongitude,
                    )
                    maybeSpeakSafetyTip(location.time)
                }
                if (
                    eval.distanceToCenterMeters <= NEAR_RADAR_METERS &&
                    zone.id !in nearAlertedZoneIds
                ) {
                    nearAlertedZoneIds.add(zone.id)
                    logDetection(RadarDetectionLog.Event.NEAR, zone, eval, debugLogs)
                    playAlertAudio(eval.isOverspeed, eval.speedLimitKmH)
                    notificationHelper?.showNearRadarNotification(
                        eval.speedLimitKmH,
                        latitude = zone.centerLatitude,
                        longitude = zone.centerLongitude,
                    )
                }
            } else {
                if (zone.id in alertedZoneIds) {
                    // Trapezoid: leave as soon as outside the corridor.
                    // Circle fallback: hysteresis at 1.2× radius or no longer ahead.
                    val leftZone = if (zone.usesApproachTrapezoid) {
                        true
                    } else {
                        eval.distanceToCenterMeters > zone.radiusMeters * 1.2 || !eval.isAhead
                    }
                    if (leftZone) {
                        logDetection(RadarDetectionLog.Event.EXIT, zone, eval, debugLogs)
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
        val settings = settingsManager.settings.value
        val debugLogs = settings.debugLoggingEnabled || settings.debugBarEnabled
        RadarDetectionLog.log(
            event = RadarDetectionLog.Event.TEST,
            zoneId = "test",
            kind = "TEST",
            roadClass = "-",
            radiusMeters = 0.0,
            distanceMeters = 0.0,
            vmaKmH = speedLimitKmH,
            speedKmH = 0.0,
            isAhead = true,
            isOverspeed = false,
            source = "dev",
            enabled = debugLogs,
        )
        playAlertAudio(isOverspeed = false, speedLimitKmH = speedLimitKmH)
        hudStore.set(
            DangerZoneHudState(
                active = true,
                speedLimitKmH = speedLimitKmH,
            ),
        )
        notificationHelper?.showDangerZoneNotification(speedLimitKmH)
    }

    private fun logDetection(
        event: RadarDetectionLog.Event,
        zone: DangerZone,
        eval: DangerZoneEvaluation,
        enabled: Boolean,
    ) {
        RadarDetectionLog.log(
            event = event,
            zoneId = zone.id,
            kind = zone.kind.name,
            roadClass = zone.roadClass.name,
            radiusMeters = zone.radiusMeters,
            distanceMeters = eval.distanceToCenterMeters,
            vmaKmH = eval.speedLimitKmH,
            speedKmH = eval.currentSpeedKmH,
            isAhead = eval.isAhead,
            isOverspeed = eval.isOverspeed,
            source = zone.source,
            enabled = enabled,
        )
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
