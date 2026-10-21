package fr.geoking.gaston.radar

import android.location.Location
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.aac.DangerZone
import fr.geoking.gaston.aac.DangerZoneEvaluation
import fr.geoking.gaston.aac.DangerZoneEvaluator
import fr.geoking.gaston.aac.DangerZoneTriangle
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
 * - zone entry
 * - each km milestone toward the center (while inside)
 * - approach ≤ [NEAR_RADAR_METERS] of zone center
 */
class DangerZoneAlertManager(
    private val settingsManager: SettingsManager,
    private val audioNotifier: RadarAudioNotifier,
    private val notificationHelper: NotificationHelper? = null,
    private val hudStore: DangerZoneHudStore = DangerZoneHudStore(),
) {
    companion object {
        /** Distance to zone center at which the final "near" HUN fires. */
        const val NEAR_RADAR_METERS = DangerZoneTriangle.NEAR_ALERT_METERS
        const val TEST_ALERT_SPEED_LIMIT_KMH = 100
    }

    private val alertedZoneIds = mutableSetOf<String>()
    private val nearAlertedZoneIds = mutableSetOf<String>()
    /** Per-zone kilometre reminder radii already fired this visit. */
    private val firedKmRadiiByZoneId = mutableMapOf<String, MutableSet<Double>>()
    private var lastLocation: Location? = null
    private var safetyTipSpokenAtMs: Long = 0L

    val hudState: StateFlow<DangerZoneHudState> = hudStore.hudState

    fun evaluateAndAlert(location: Location, zones: List<DangerZone>) {
        val settings = settingsManager.settings.value
        if (!settings.radarWarningEnabled) {
            clearAlertState()
            lastLocation = location
            hudStore.clear()
            return
        }
        val debugLogs = settings.debugLoggingEnabled || settings.debugBarEnabled
        val isDevMode = settings.isDevMode

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
            location.hasBearing() -> location.bearing.toDouble()
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
                val firedKm = firedKmRadiiByZoneId.getOrPut(zone.id) { mutableSetOf() }
                val kmRadii = DangerZoneTriangle.kmReminderRadiiMeters(zone.radiusMeters)

                if (zone.id !in alertedZoneIds) {
                    alertedZoneIds.add(zone.id)
                    logDetection(RadarDetectionLog.Event.ENTRY, zone, eval, debugLogs)
                    playAlertAudio(eval.isOverspeed, eval.speedLimitKmH, eval.distanceToCenterMeters, isDevMode)
                    notificationHelper?.showDangerZoneNotification(
                        eval.speedLimitKmH,
                        latitude = zone.centerLatitude,
                        longitude = zone.centerLongitude,
                        isDevMode = isDevMode,
                    )
                    maybeSpeakSafetyTip(location.time)
                    // Mark km thresholds already behind so entry mid-zone does not spam every km.
                    for (kmRadius in kmRadii) {
                        if (eval.distanceToCenterMeters <= kmRadius) {
                            firedKm.add(kmRadius)
                        }
                    }
                } else {
                    // Subsequent ticks: remind once when distance drops to/below each remaining km.
                    for (kmRadius in kmRadii) {
                        if (kmRadius in firedKm) continue
                        if (eval.distanceToCenterMeters <= kmRadius) {
                            firedKm.add(kmRadius)
                            logDetection(RadarDetectionLog.Event.ENTRY, zone, eval, debugLogs)
                            playAlertAudio(eval.isOverspeed, eval.speedLimitKmH, eval.distanceToCenterMeters, isDevMode)
                            notificationHelper?.showDangerZoneKmReminderNotification(
                                speedLimitKmH = eval.speedLimitKmH,
                                distanceMeters = kmRadius,
                                latitude = zone.centerLatitude,
                                longitude = zone.centerLongitude,
                                isDevMode = isDevMode,
                            )
                        }
                    }
                }

                if (
                    eval.distanceToCenterMeters <= NEAR_RADAR_METERS &&
                    zone.id !in nearAlertedZoneIds
                ) {
                    nearAlertedZoneIds.add(zone.id)
                    logDetection(RadarDetectionLog.Event.NEAR, zone, eval, debugLogs)
                    playAlertAudio(eval.isOverspeed, eval.speedLimitKmH, eval.distanceToCenterMeters, isDevMode)
                    notificationHelper?.showNearRadarNotification(
                        eval.speedLimitKmH,
                        latitude = zone.centerLatitude,
                        longitude = zone.centerLongitude,
                        isDevMode = isDevMode,
                    )
                }
            } else {
                if (zone.id in alertedZoneIds) {
                    // Sector / circle: leave as soon as outside geometric zone.
                    // Circle fallback also allows hysteresis at 1.2× radius when still "ahead".
                    val leftZone = if (zone.usesApproachTrapezoid) {
                        true
                    } else {
                        eval.distanceToCenterMeters > zone.radiusMeters * 1.2 || !eval.isAhead
                    }
                    if (leftZone) {
                        logDetection(RadarDetectionLog.Event.EXIT, zone, eval, debugLogs)
                        alertedZoneIds.remove(zone.id)
                        nearAlertedZoneIds.remove(zone.id)
                        firedKmRadiiByZoneId.remove(zone.id)
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
        val isDevMode = settings.isDevMode
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
        playAlertAudio(isOverspeed = false, speedLimitKmH = speedLimitKmH, isDevMode = isDevMode)
        hudStore.set(
            DangerZoneHudState(
                active = true,
                speedLimitKmH = speedLimitKmH,
            ),
        )
        notificationHelper?.showDangerZoneNotification(speedLimitKmH, isDevMode = isDevMode)
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

    private fun playAlertAudio(
        isOverspeed: Boolean,
        speedLimitKmH: Int?,
        distanceMeters: Double? = null,
        isDevMode: Boolean = false,
    ) {
        if (isOverspeed) {
            audioNotifier.playOverSpeedBeepsAndSpeak(speedLimitKmH, distanceMeters, isDevMode)
        } else {
            audioNotifier.playOkSpeedBeeps(distanceMeters)
            audioNotifier.speakDangerZone(speedLimitKmH, distanceMeters, isDevMode)
        }
    }

    private fun maybeSpeakSafetyTip(nowMs: Long) {
        if (nowMs - safetyTipSpokenAtMs < 30 * 60 * 1000L) return
        safetyTipSpokenAtMs = nowMs
        @Suppress("UNUSED_VARIABLE")
        val tip = RoadSafetyMessages.randomTip(nowMs)
    }

    private fun clearAlertState() {
        alertedZoneIds.clear()
        nearAlertedZoneIds.clear()
        firedKmRadiiByZoneId.clear()
    }

    fun clearAlerts() {
        clearAlertState()
        lastLocation = null
        hudStore.clear()
    }

    fun getAlertedZoneIds(): Set<String> = alertedZoneIds.toSet()

    fun getNearAlertedZoneIds(): Set<String> = nearAlertedZoneIds.toSet()

    fun getFiredKmRadii(zoneId: String): Set<Double> =
        firedKmRadiiByZoneId[zoneId]?.toSet().orEmpty()
}
