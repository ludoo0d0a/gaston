package fr.geoking.gaston

import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.util.Date

/**
 * Where a garage vehicle was last parked ("remember my car").
 * One entry per [vehicleId]; latest save wins.
 */
@Serializable
data class ParkedVehiclePosition(
    val vehicleId: String,
    val latitude: Double,
    val longitude: Double,
    /** Wall-clock time when the pin was saved (epoch ms). */
    val savedAtEpochMs: Long,
) {
    /** Locale-aware date + time for UI (phone + Android Auto). */
    fun formattedTimestamp(
        dateStyle: Int = DateFormat.SHORT,
        timeStyle: Int = DateFormat.SHORT,
    ): String = DateFormat.getDateTimeInstance(dateStyle, timeStyle).format(Date(savedAtEpochMs))

    /** Locale-aware clock time only (e.g. dashboard “Stationné” teaser). */
    fun formattedTimeOnly(timeStyle: Int = DateFormat.SHORT): String =
        DateFormat.getTimeInstance(timeStyle).format(Date(savedAtEpochMs))
}

object ParkedCarIntents {
    const val ACTION_REMEMBER = "fr.geoking.gaston.action.REMEMBER_PARKED_CAR"
    /** Open parked-car UI to review an already-saved pin (phone walk-away case 1). */
    const val ACTION_VIEW_PARKED = "fr.geoking.gaston.action.VIEW_PARKED_CAR"

    const val EXTRA_VEHICLE_ID = "fr.geoking.gaston.extra.PARKED_VEHICLE_ID"
    const val EXTRA_LATITUDE = "fr.geoking.gaston.extra.PARKED_LATITUDE"
    const val EXTRA_LONGITUDE = "fr.geoking.gaston.extra.PARKED_LONGITUDE"
}

/** Deep-link payload to open the parked-car screen (optional frozen candidate coords). */
data class RememberParkedRequest(
    val vehicleId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

fun AppSettings.parkedPositionFor(vehicleId: String): ParkedVehiclePosition? =
    parkedPositions.firstOrNull { it.vehicleId == vehicleId }

/** Most recently saved parking pin across the garage (for dashboard teasers). */
fun AppSettings.latestParkedPosition(): ParkedVehiclePosition? =
    parkedPositions.maxByOrNull { it.savedAtEpochMs }

fun AppSettings.upsertParkedPosition(position: ParkedVehiclePosition): AppSettings =
    copy(parkedPositions = parkedPositions.filterNot { it.vehicleId == position.vehicleId } + position)

fun AppSettings.clearParkedPosition(vehicleId: String): AppSettings =
    copy(parkedPositions = parkedPositions.filterNot { it.vehicleId == vehicleId })
