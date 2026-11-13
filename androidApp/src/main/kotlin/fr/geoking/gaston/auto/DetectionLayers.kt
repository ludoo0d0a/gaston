package fr.geoking.gaston.auto

import androidx.car.app.CarContext
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.FeatureFlag
import fr.geoking.gaston.R
import fr.geoking.gaston.isOtherModeActive

/** Radar detection + map layer toggle (global). */
fun AppSettings.isRadarDetectionOn(): Boolean = FeatureFlag.RADAR && radarWarningEnabled

/** Traffic detection + map layer toggle (global). */
fun AppSettings.isTrafficDetectionOn(): Boolean =
    FeatureFlag.TRAFFIC && (mapTrafficEnabled || trafficAlertEnabled)

/**
 * Menu title for a radar / traffic detection map: "Radar", "Trafic", or "Radar / Trafic".
 * Null when neither layer is enabled (caller keeps the screen's default title).
 */
fun CarContext.detectionLayersMenuTitle(settings: AppSettings): String? {
    val radar = settings.isRadarDetectionOn()
    val traffic = settings.isTrafficDetectionOn()
    val radarLabel = getString(R.string.search_mode_radar)
    val trafficLabel = getString(R.string.search_mode_traffic)
    return when {
        radar && traffic -> "$radarLabel / $trafficLabel"
        radar -> radarLabel
        traffic -> trafficLabel
        else -> null
    }
}

/**
 * Title shown on AA map screens when the current filter is the exclusive radar/traffic
 * "Other" mode (opened from dashboard "Ouvrir la carte" with layers on, or Places shortcuts).
 * Falls back to [fallback] for fuel/EV/generic maps.
 */
fun CarContext.mapMenuTitleForSettings(settings: AppSettings, fallback: String): String {
    if (!settings.isOtherModeActive()) return fallback
    return detectionLayersMenuTitle(settings) ?: fallback
}
