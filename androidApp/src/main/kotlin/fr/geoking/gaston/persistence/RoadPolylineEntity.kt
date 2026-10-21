package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents an Overpass road way polyline cached in Room database.
 */
@Entity(
    tableName = "road_polyline_entity",
    indices = [
        Index(value = ["minLat", "maxLat", "minLon", "maxLon"]),
        Index(value = ["updatedAtMs"])
    ]
)
data class RoadPolylineEntity(
    @PrimaryKey
    val id: Long,
    val highwayType: String?,
    val name: String?,
    val pointsJson: String,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val updatedAtMs: Long
)
