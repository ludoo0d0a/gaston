package fr.geoking.gaston.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FuelPricePredictionScoreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: FuelPricePredictionScoreEntity)

    @Query(
        """
        SELECT
          AVG(CASE WHEN s.directionCorrect THEN 1.0 ELSE 0.0 END) AS hitRate,
          AVG(s.absError) AS mae
        FROM fuel_price_prediction_scores s
        INNER JOIN fuel_price_predictions p ON p.id = s.predictionId
        WHERE p.fuelId = :fuelId AND p.locationKey = :locationKey AND p.targetDay >= :fromDay
        """
    )
    suspend fun accuracySince(fuelId: String, locationKey: String, fromDay: String): ForecastAccuracyStats?

    @Query(
        """
        SELECT s.* FROM fuel_price_prediction_scores s
        INNER JOIN fuel_price_predictions p ON p.id = s.predictionId
        WHERE p.fuelId = :fuelId AND p.locationKey = :locationKey
        ORDER BY s.scoredAtMs DESC
        LIMIT 1
        """
    )
    suspend fun latestScoreForLocation(fuelId: String, locationKey: String): FuelPricePredictionScoreEntity?
}
