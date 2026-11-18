package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Geo coverage for Overpass supermarket fetches used only for gas brand enrich.
 * Empty regions are stored too (negative cache) so we do not re-query Overpass.
 */
@Entity(
    tableName = "supermarket_enrich_region",
    indices = [
        Index(value = ["centerLat", "centerLng"]),
        Index(value = ["loadedAtMs"]),
    ],
)
data class SupermarketEnrichRegionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val centerLat: Double,
    val centerLng: Double,
    val maxRadiusKmLoaded: Int,
    val loadedAtMs: Long,
)
