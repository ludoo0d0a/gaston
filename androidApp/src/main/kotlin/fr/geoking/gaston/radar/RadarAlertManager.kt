package fr.geoking.gaston.radar

import android.location.Location
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.feature.notification.NotificationHelper
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.shared.location.haversineKm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Legacy POI-based alert path (unit tests / transitional).
 * Production AAC loop uses [DangerZoneAlertManager] + [fr.geoking.gaston.aac.DangerZoneRepository].
 */
class RadarAlertManager(
    private val settingsManager: SettingsManager,
    private val audioNotifier: RadarAudioNotifier,
    private val notificationHelper: NotificationHelper? = null,
) {
    private val alertedRadarIds = mutableSetOf<String>()
    private var lastLocation: Location? = null

    private val _hudState = MutableStateFlow(DangerZoneHudState())
    val hudState: StateFlow<DangerZoneHudState> = _hudState.asStateFlow()

    fun evaluateAndAlert(location: Location, nearbyRadarPois: List<Poi>) {
        val settings = settingsManager.settings.value
        if (!settings.radarWarningEnabled) {
            alertedRadarIds.clear()
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
                    RadarTrajectoryHelper.calculateBearing(prev.latitude, prev.longitude, curLat, curLon)
                } else null
            }
            else -> null
        }

        val warningDistanceMeters = settings.radarWarningDistanceMeters.coerceIn(300, 4000)

        var activeHudLimit: Int? = null
        var anyActive = false

        for (radar in nearbyRadarPois) {
            val eval = RadarTrajectoryHelper.evaluateRadar(
                vehLat = curLat,
                vehLon = curLon,
                vehSpeedKmH = speedKmH,
                vehBearing = bearing,
                radar = radar,
                warningDistanceMeters = warningDistanceMeters
            )

            if (eval.isWithinWarningDistance) {
                anyActive = true
                if (activeHudLimit == null && eval.speedLimitKmH != null) {
                    activeHudLimit = eval.speedLimitKmH
                }
                if (radar.id !in alertedRadarIds) {
                    alertedRadarIds.add(radar.id)
                    if (eval.isOverspeed) {
                        audioNotifier.playOverSpeedBeepsAndSpeak(eval.speedLimitKmH)
                    } else {
                        audioNotifier.playOkSpeedBeeps()
                        audioNotifier.speakDangerZone(eval.speedLimitKmH)
                    }
                    notificationHelper?.showDangerZoneNotification(eval.speedLimitKmH)
                }
            } else {
                if (radar.id in alertedRadarIds) {
                    if (eval.distanceMeters > warningDistanceMeters * 1.3 || !eval.isAhead) {
                        alertedRadarIds.remove(radar.id)
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

    fun clearAlerts() {
        alertedRadarIds.clear()
        lastLocation = null
        _hudState.value = DangerZoneHudState()
    }

    fun getAlertedRadarIds(): Set<String> = alertedRadarIds.toSet()
}
