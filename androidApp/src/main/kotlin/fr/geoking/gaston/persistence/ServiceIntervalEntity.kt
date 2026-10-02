package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "service_intervals",
    indices = [
        Index(value = ["vehicleId", "serviceKind"], unique = true),
    ]
)
data class ServiceIntervalEntity(
    @PrimaryKey
    val id: String,
    val vehicleId: String,
    /** OIL_CHANGE | TIRES | BRAKES | INSPECTION | REVISION | OTHER */
    val serviceKind: String,
    val everyKm: Int? = null,
    val everyMonths: Int? = null,
    val lastEventId: String? = null,
    val lastDoneAtMs: Long? = null,
    val lastOdometerKm: Int? = null,
)
