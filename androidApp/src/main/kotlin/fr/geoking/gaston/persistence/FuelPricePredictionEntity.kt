package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "fuel_price_predictions",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["createdDay", "fuelId", "locationKey", "horizonDays"], unique = true),
        Index(value = ["targetDay", "fuelId", "locationKey"])
    ]
)
data class FuelPricePredictionEntity(
    val id: String,
    val createdAtMs: Long,
    /** UTC yyyy-MM-dd when prediction was made */
    val createdDay: String,
    val targetDay: String,
    val horizonDays: Int,
    val fuelId: String,
    val locationKey: String,
    val predictedUp: Boolean,
    val predictedPrice: Double,
    val baselinePrice: Double,
    val marketScore: Double,
    val inputsJson: String
)
