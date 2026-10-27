package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a cached radar road corridor polygon stored in Room database.
 */
@Entity(
    tableName = "radar_corridor_entity",
    indices = [
        Index(value = ["updatedAtMs"])
    ]
)
data class RadarCorridorEntity(
    @PrimaryKey
    val poiId: String,
    val zoneRadiusMeters: Double,
    val fillArgb: Int,
    val strokeArgb: Int,
    /** JSON string storing array of [lat, lon] pairs. */
    val pointsJson: String,
    val updatedAtMs: Long
)
