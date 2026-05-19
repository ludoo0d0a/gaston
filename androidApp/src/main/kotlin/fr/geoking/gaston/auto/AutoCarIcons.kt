package fr.geoking.gaston.auto

import androidx.annotation.DrawableRes
import androidx.car.app.CarContext
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.gaston.R
import fr.geoking.gaston.feature.emergency.EmergencyCategory

/**
 * Tinted [CarIcon] helpers for Android Auto UI chrome (dashboard, action strips).
 * Station brand / POI list icons stay full-color via [AutoPoiUiHelper].
 */
object AutoCarIcons {
    private const val PRIMARY_LIGHT = 0xFF1E3A8A.toInt()
    private const val PRIMARY_DARK = 0xFF93C5FD.toInt()
    private const val FUEL_LIGHT = 0xFFEAB308.toInt()
    private const val FUEL_DARK = 0xFFFACC15.toInt()
    private const val EV_LIGHT = 0xFF22C55E.toInt()
    private const val EV_DARK = 0xFF4ADE80.toInt()
    private const val EMERGENCY_LIGHT = 0xFFEF4444.toInt()
    private const val EMERGENCY_DARK = 0xFFF87171.toInt()
    private const val MUTED_LIGHT = 0xFF64748B.toInt()
    private const val MUTED_DARK = 0xFF94A3B8.toInt()

    val primary: CarColor = CarColor.createCustom(PRIMARY_LIGHT, PRIMARY_DARK)
    val fuel: CarColor = CarColor.createCustom(FUEL_LIGHT, FUEL_DARK)
    val ev: CarColor = CarColor.createCustom(EV_LIGHT, EV_DARK)
    val emergency: CarColor = CarColor.createCustom(EMERGENCY_LIGHT, EMERGENCY_DARK)
    val muted: CarColor = CarColor.createCustom(MUTED_LIGHT, MUTED_DARK)
}

fun CarContext.carIcon(
    @DrawableRes resId: Int,
    tint: CarColor? = AutoCarIcons.primary,
): CarIcon {
    val builder = CarIcon.Builder(IconCompat.createWithResource(this, resId))
    if (tint != null) builder.setTint(tint)
    return builder.build()
}

fun CarContext.dashboardFuelIcon(): CarIcon = carIcon(R.drawable.ic_poi_gas, AutoCarIcons.fuel)

fun CarContext.dashboardEvIcon(): CarIcon = carIcon(R.drawable.ic_poi_electric, AutoCarIcons.ev)

fun CarContext.dashboardMyCarIcon(): CarIcon = carIcon(R.drawable.ic_directions_car, AutoCarIcons.primary)

fun CarContext.dashboardOtherIcon(): CarIcon = carIcon(R.drawable.ic_waypoint, AutoCarIcons.primary)

fun CarContext.dashboardRoutesIcon(): CarIcon = carIcon(R.drawable.ic_swap_horiz, AutoCarIcons.primary)

fun CarContext.dashboardNetworkIcon(): CarIcon = carIcon(R.drawable.ic_poi_radar, AutoCarIcons.primary)

fun CarContext.dashboardEmergencyIcon(): CarIcon = carIcon(R.drawable.ic_sos, AutoCarIcons.emergency)

fun CarContext.dashboardSettingsIcon(): CarIcon = carIcon(R.drawable.ic_settings, AutoCarIcons.primary)

fun CarContext.actionHomeIcon(): CarIcon = carIcon(R.drawable.ic_home, AutoCarIcons.primary)

fun CarContext.actionSettingsIcon(): CarIcon = carIcon(R.drawable.ic_settings, AutoCarIcons.primary)

fun CarContext.actionMapIcon(): CarIcon = carIcon(R.drawable.ic_map, AutoCarIcons.primary)

fun CarContext.actionErrorIcon(): CarIcon = carIcon(R.drawable.ic_error_outline, AutoCarIcons.emergency)

fun CarContext.actionZoomInIcon(): CarIcon = carIcon(R.drawable.ic_add, AutoCarIcons.primary)

fun CarContext.actionZoomOutIcon(): CarIcon = carIcon(R.drawable.ic_remove, AutoCarIcons.primary)

fun CarContext.actionCompassIcon(): CarIcon = carIcon(R.drawable.ic_compass, AutoCarIcons.primary)

fun CarContext.actionRecenterIcon(): CarIcon = carIcon(R.drawable.ic_gps_fixed, AutoCarIcons.primary)

fun CarContext.actionHistoryIcon(): CarIcon = carIcon(R.drawable.ic_history, AutoCarIcons.muted)

fun CarContext.emergencyCategoryIcon(category: EmergencyCategory): CarIcon = when (category) {
    EmergencyCategory.GENERAL -> carIcon(R.drawable.ic_sos, AutoCarIcons.emergency)
    EmergencyCategory.POLICE -> carIcon(R.drawable.ic_directions_car, AutoCarIcons.muted)
    EmergencyCategory.MEDICAL -> carIcon(R.drawable.ic_poi_radar, AutoCarIcons.primary)
    EmergencyCategory.FIRE -> carIcon(R.drawable.ic_poi_gas, AutoCarIcons.fuel)
    EmergencyCategory.ROADSIDE -> carIcon(R.drawable.ic_error_outline, AutoCarIcons.emergency)
    EmergencyCategory.OTHER -> carIcon(R.drawable.ic_speaker, AutoCarIcons.muted)
}

/** Full-color assets (e.g. launcher) — no tint. */
fun CarContext.carIconUntinted(@DrawableRes resId: Int): CarIcon = carIcon(resId, tint = null)
