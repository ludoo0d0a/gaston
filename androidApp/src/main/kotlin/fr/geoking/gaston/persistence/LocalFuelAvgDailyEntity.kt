package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "local_fuel_avg_daily",
    primaryKeys = ["day", "fuelId", "locationKey"],
    indices = [Index(value = ["locationKey", "fuelId", "day"])]
)
data class LocalFuelAvgDailyEntity(
    val day: String,
    val fuelId: String,
    /** Rounded lat/lon key, e.g. "48.86_2.35" */
    val locationKey: String,
    val avgPrice: Double,
    val stationCount: Int,
    val updatedAtMs: Long
)
