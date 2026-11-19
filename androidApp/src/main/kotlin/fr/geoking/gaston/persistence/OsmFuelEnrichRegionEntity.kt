package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Geo coverage for Overpass `amenity=fuel` fetches used for on-demand gas detail enrich.
 * Empty regions are stored too (negative cache).
 */
@Entity(
    tableName = "osm_fuel_enrich_region",
    indices = [
        Index(value = ["centerLat", "centerLng"]),
        Index(value = ["loadedAtMs"]),
    ],
)
data class OsmFuelEnrichRegionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val centerLat: Double,
    val centerLng: Double,
    val maxRadiusKmLoaded: Int,
    val loadedAtMs: Long,
)
