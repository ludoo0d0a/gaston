package fr.geoking.gaston.auto

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import fr.geoking.gaston.R
import fr.geoking.gaston.feature.emergency.EmergencyCategory
import fr.geoking.gaston.intent.IntentNavigationHelper
import fr.geoking.gaston.poi.Poi

/**
 * Tinted [CarIcon] helpers for Android Auto UI chrome (dashboard, action strips).
 * Station brand / POI list icons stay full-color via [AutoPoiUiHelper].
 */
object AutoCarIcons {
    private const val PRIMARY_LIGHT = 0xFF1D4ED8.toInt()
    private const val PRIMARY_DARK = 0xFF93C5FD.toInt()
    private const val FUEL_LIGHT = 0xFFEAB308.toInt()
    private const val FUEL_DARK = 0xFFFACC15.toInt()
    private const val EV_LIGHT = 0xFF22C55E.toInt()
    private const val EV_DARK = 0xFF4ADE80.toInt()
    private const val EMERGENCY_LIGHT = 0xFFEF4444.toInt()
    private const val EMERGENCY_DARK = 0xFFF87171.toInt()
    private const val MUTED_LIGHT = 0xFF64748B.toInt()
    private const val MUTED_DARK = 0xFF94A3B8.toInt()

    private const val POLICE_LIGHT = 0xFF1565C0.toInt()
    private const val POLICE_DARK = 0xFF64B5F6.toInt()
    private const val MEDICAL_LIGHT = 0xFF2E7D32.toInt()
    private const val MEDICAL_DARK = 0xFF81C784.toInt()
    private const val FIRE_LIGHT = 0xFFE65100.toInt()
    private const val FIRE_DARK = 0xFFFFB74D.toInt()
    private const val ROADSIDE_LIGHT = 0xFFEF6C00.toInt()
    private const val ROADSIDE_DARK = 0xFFFFCC80.toInt()
    private const val OTHER_LIGHT = 0xFF6A1B9A.toInt()
    private const val OTHER_DARK = 0xFFBA68C8.toInt()

    private const val GAZOLE_COLOR = 0xFFEAB308.toInt()
    private const val SP95_COLOR = 0xFF22C55E.toInt()
    private const val SP98_COLOR = 0xFF15803D.toInt()
    private const val E85_COLOR = 0xFFA855F7.toInt()
    private const val GPL_COLOR = 0xFFF97316.toInt()

    val primary: CarColor = CarColor.createCustom(PRIMARY_LIGHT, PRIMARY_DARK)
    val fuel: CarColor = CarColor.createCustom(FUEL_LIGHT, FUEL_DARK)
    val ev: CarColor = CarColor.createCustom(EV_LIGHT, EV_DARK)
    val emergency: CarColor = CarColor.createCustom(EMERGENCY_LIGHT, EMERGENCY_DARK)
    val muted: CarColor = CarColor.createCustom(MUTED_LIGHT, MUTED_DARK)

    val police: CarColor = CarColor.createCustom(POLICE_LIGHT, POLICE_DARK)
    val medical: CarColor = CarColor.createCustom(MEDICAL_LIGHT, MEDICAL_DARK)
    val fire: CarColor = CarColor.createCustom(FIRE_LIGHT, FIRE_DARK)
    val roadside: CarColor = CarColor.createCustom(ROADSIDE_LIGHT, ROADSIDE_DARK)
    val other: CarColor = CarColor.createCustom(OTHER_LIGHT, OTHER_DARK)

    val gazole: CarColor = CarColor.createCustom(GAZOLE_COLOR, GAZOLE_COLOR)
    val sp95: CarColor = CarColor.createCustom(SP95_COLOR, SP95_COLOR)
    val sp98: CarColor = CarColor.createCustom(SP98_COLOR, SP98_COLOR)
    val e85: CarColor = CarColor.createCustom(E85_COLOR, E85_COLOR)
    val gpl: CarColor = CarColor.createCustom(GPL_COLOR, GPL_COLOR)

    fun fuelCarColor(fuelId: String?): CarColor = when (fuelId) {
        "gazole" -> gazole
        "sp95" -> sp95
        "sp98" -> sp98
        "e85" -> e85
        "gplc" -> gpl
        else -> primary
    }
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

fun CarContext.dashboardOtherIcon(): CarIcon = carIcon(R.drawable.ic_category, AutoCarIcons.primary)

fun CarContext.dashboardRoutesIcon(): CarIcon = carIcon(R.drawable.ic_directions, AutoCarIcons.primary)

fun CarContext.dashboardNetworkIcon(): CarIcon = carIcon(R.drawable.ic_signal_cellular, AutoCarIcons.primary)

fun CarContext.dashboardEmergencyIcon(): CarIcon = carIcon(R.drawable.ic_sos, AutoCarIcons.emergency)

fun CarContext.dashboardSettingsIcon(): CarIcon = carIcon(R.drawable.ic_settings, AutoCarIcons.primary)

fun CarContext.actionHomeIcon(): CarIcon = carIcon(R.drawable.ic_home, AutoCarIcons.primary)

fun CarContext.actionSettingsIcon(): CarIcon = carIcon(R.drawable.ic_settings, AutoCarIcons.primary)

fun CarContext.actionMapIcon(): CarIcon = carIcon(R.drawable.ic_map, AutoCarIcons.primary)

fun CarContext.actionErrorIcon(): CarIcon = carIcon(R.drawable.ic_error_outline, AutoCarIcons.emergency)

fun CarContext.actionZoomInIcon(): CarIcon = carIcon(R.drawable.ic_add, AutoCarIcons.primary)

fun CarContext.actionZoomOutIcon(): CarIcon = carIcon(R.drawable.ic_remove, AutoCarIcons.primary)

fun CarContext.actionCompassIcon(): CarIcon = carIconUntinted(R.drawable.ic_compass)

fun CarContext.actionNavigateToIcon(): CarIcon = carIcon(R.drawable.ic_navigate_to, AutoCarIcons.primary)

/**
 * Hands off to the host navigation app for [poi].
 *
 * @param withTitle when true (ActionStrip), shows [R.string.screen_navigate_to] — at most one
 * labeled strip button. Header end actions should pass false (icon-only).
 */
fun CarContext.navigateToStationAction(poi: Poi, withTitle: Boolean = true): Action {
    val navigateIntent = Intent(CarContext.ACTION_NAVIGATE).apply {
        data = IntentNavigationHelper.getNavigationUri(poi)
    }
    val builder = Action.Builder()
        .setIcon(actionNavigateToIcon())
        .setOnClickListener { startCarApp(navigateIntent) }
    if (withTitle) {
        builder.setTitle(getString(R.string.screen_navigate_to))
    }
    return builder.build()
}

fun CarContext.actionPreviousIcon(): CarIcon = carIcon(R.drawable.ic_chevron_left, AutoCarIcons.primary)

fun CarContext.actionNextIcon(): CarIcon = carIcon(R.drawable.ic_chevron_right, AutoCarIcons.primary)

fun CarContext.actionCloseIcon(): CarIcon = carIcon(R.drawable.ic_close, AutoCarIcons.primary)

fun CarContext.actionRecenterIcon(): CarIcon = carIcon(R.drawable.ic_gps_fixed, AutoCarIcons.primary)

fun CarContext.actionHistoryIcon(): CarIcon = carIcon(R.drawable.ic_history, AutoCarIcons.muted)

fun CarContext.actionRefreshIcon(): CarIcon = carIcon(R.drawable.ic_refresh, AutoCarIcons.primary)

fun CarContext.actionCheapestIcon(active: Boolean): CarIcon =
    carIcon(R.drawable.ic_cheapest_price, if (active) AutoCarIcons.fuel else AutoCarIcons.primary)

fun CarContext.emergencyCategoryIcon(category: EmergencyCategory): CarIcon = when (category) {
    EmergencyCategory.GENERAL -> carIcon(R.drawable.ic_sos, AutoCarIcons.emergency)
    EmergencyCategory.POLICE -> carIcon(R.drawable.ic_local_police, AutoCarIcons.police)
    EmergencyCategory.MEDICAL -> carIcon(R.drawable.ic_local_hospital, AutoCarIcons.medical)
    EmergencyCategory.FIRE -> carIcon(R.drawable.ic_local_fire_department, AutoCarIcons.fire)
    EmergencyCategory.ROADSIDE -> carIcon(R.drawable.ic_warning, AutoCarIcons.roadside)
    EmergencyCategory.OTHER -> carIcon(R.drawable.ic_phone, AutoCarIcons.other)
}

/** Full-color assets (e.g. launcher) — no tint. */
fun CarContext.carIconUntinted(@DrawableRes resId: Int): CarIcon = carIcon(resId, tint = null)
