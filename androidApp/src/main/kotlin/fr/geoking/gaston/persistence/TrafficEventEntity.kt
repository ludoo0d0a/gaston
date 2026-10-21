package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "traffic_events",
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["latMin", "latMax"]),
        Index(value = ["lonMin", "lonMax"]),
        Index(value = ["fetchedAtMs"])
    ]
)
data class TrafficEventEntity(
    @PrimaryKey
    val primaryKey: String, // e.g. "sourceId_id" or generated
    val sourceId: String,
    val eventId: String?,
    val roadRef: String,
    val direction: String?,
    val severity: String,
    val message: String?,
    val travelTimeSeconds: Int?,
    val latMin: Double,
    val lonMin: Double,
    val latMax: Double,
    val lonMax: Double,
    val latitude: Double?,
    val longitude: Double?,
    val zonePointsJson: String?, // Serialized List<Pair<Double, Double>>
    val updatedAtMs: Long?,
    val fetchedAtMs: Long
)
