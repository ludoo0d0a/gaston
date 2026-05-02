package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "poi_cache",
    indices = [
        Index(value = ["latitude", "longitude"]),
        Index(value = ["updatedAtMs"])
    ]
)
data class PoiCacheEntity(
    @PrimaryKey
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val address: String,
    val poiJson: String,
    val updatedAtMs: Long
)
