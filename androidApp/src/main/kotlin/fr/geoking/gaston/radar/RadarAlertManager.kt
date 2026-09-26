package fr.geoking.gaston.radar

import android.location.Location
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.shared.location.haversineKm

class RadarAlertManager(
    private val settingsManager: SettingsManager,
    private val audioNotifier: RadarAudioNotifier
) {
    private val alertedRadarIds = mutableSetOf<String>()
    private var lastLocation: Location? = null

    /**
     * Evaluates current location against nearby radar POIs and triggers appropriate alerts.
     */
    fun evaluateAndAlert(location: Location, nearbyRadarPois: List<Poi>) {
        val settings = settingsManager.settings.value
        if (!settings.radarWarningEnabled) {
            alertedRadarIds.clear()
            lastLocation = location
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
                if (distKm > 0.005) { // at least 5 meters moved
                    RadarTrajectoryHelper.calculateBearing(prev.latitude, prev.longitude, curLat, curLon)
                } else null
            }
            else -> null
        }

        val warningDistanceMeters = settings.radarWarningDistanceMeters.coerceAtLeast(100)

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
                if (radar.id !in alertedRadarIds) {
                    alertedRadarIds.add(radar.id)
                    if (eval.isOverspeed) {
                        audioNotifier.playOverSpeedBeepsAndSpeak(eval.speedLimitKmH)
                    } else {
                        audioNotifier.playOkSpeedBeeps()
                    }
                }
            } else {
                // Reset alerted radar when moved out of warning zone or passed behind
                if (radar.id in alertedRadarIds) {
                    if (eval.distanceMeters > warningDistanceMeters * 1.3 || !eval.isAhead) {
                        alertedRadarIds.remove(radar.id)
                    }
                }
            }
        }

        lastLocation = location
    }

    fun clearAlerts() {
        alertedRadarIds.clear()
        lastLocation = null
    }

    fun getAlertedRadarIds(): Set<String> = alertedRadarIds.toSet()
}
