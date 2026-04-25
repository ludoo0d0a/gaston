package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "fuel_price_prediction_scores",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["predictionId"], unique = true)
    ]
)
data class FuelPricePredictionScoreEntity(
    val id: String,
    val predictionId: String,
    val scoredAtMs: Long,
    val actualPrice: Double,
    val baselinePrice: Double,
    val directionCorrect: Boolean,
    val absError: Double
)
