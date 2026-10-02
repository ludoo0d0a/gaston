package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "maintenance_events",
    indices = [
        Index(value = ["vehicleId", "dateEpochMs"]),
        Index(value = ["vehicleId", "type"]),
    ]
)
data class MaintenanceEventEntity(
    @PrimaryKey
    val id: String,
    val vehicleId: String,
    /** SERVICE | FUEL | CHARGE | ODOMETER | EXPENSE */
    val type: String,
    val dateEpochMs: Long,
    val odometerKm: Int? = null,
    val title: String = "",
    val notes: String = "",
    val amountEur: Double? = null,
    val volumeLiters: Double? = null,
    val energyKwh: Double? = null,
    /** OIL_CHANGE | TIRES | BRAKES | INSPECTION | REVISION | OTHER */
    val serviceKind: String? = null,
    val stationName: String = "",
)
