package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "national_fuel_prices",
    indices = [
        Index(value = ["countryCode", "fuelId", "day"], unique = true)
    ]
)
data class NationalFuelPriceEntity(
    @PrimaryKey
    val id: String,
    val countryCode: String,
    /** Normalized type id (e.g. "gazole", "sp98"). */
    val fuelId: String,
    /** ISO date string (YYYY-MM-DD). */
    val day: String,
    val avgPrice: Double,
    val updatedAtMs: Long
)
