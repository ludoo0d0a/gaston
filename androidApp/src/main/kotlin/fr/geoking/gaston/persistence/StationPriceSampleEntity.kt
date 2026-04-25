package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "station_price_samples",
    indices = [
        Index(value = ["stationId", "fuelId", "observedAtMs"])
    ]
)
data class StationPriceSampleEntity(
    @PrimaryKey
    val id: String,
    val stationId: String,
    /** Normalized type id (e.g. "gazole", "sp98"). */
    val fuelId: String,
    /** Original label as received (e.g. "Gazole"). */
    val fuelName: String,
    val price: Double,
    val currency: String,
    val outOfStock: Boolean,
    val observedAtMs: Long
)

