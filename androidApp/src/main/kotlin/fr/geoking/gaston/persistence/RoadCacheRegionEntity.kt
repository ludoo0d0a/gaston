package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a geographical bounding box area cached from Overpass API road queries.
 */
@Entity(
    tableName = "road_cache_region",
    indices = [
        Index(value = ["minLat", "maxLat", "minLon", "maxLon"]),
        Index(value = ["updatedAtMs"])
    ]
)
data class RoadCacheRegionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val updatedAtMs: Long
)
