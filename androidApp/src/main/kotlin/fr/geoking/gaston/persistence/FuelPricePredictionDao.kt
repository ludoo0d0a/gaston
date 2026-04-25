package fr.geoking.gaston.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FuelPricePredictionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: FuelPricePredictionEntity)

    @Query(
        """
        SELECT * FROM fuel_price_predictions p
        WHERE p.targetDay < :todayUtc
          AND NOT EXISTS (
            SELECT 1 FROM fuel_price_prediction_scores s WHERE s.predictionId = p.id
          )
        """
    )
    suspend fun pendingScoring(todayUtc: String): List<FuelPricePredictionEntity>

    @Query(
        """
        SELECT * FROM fuel_price_predictions
        WHERE createdDay = :createdDay AND fuelId = :fuelId AND locationKey = :locationKey
        ORDER BY horizonDays ASC
        """
    )
    suspend fun forCreationDay(createdDay: String, fuelId: String, locationKey: String): List<FuelPricePredictionEntity>

    @Query(
        """
        SELECT EXISTS(
          SELECT 1 FROM fuel_price_predictions
          WHERE createdDay = :createdDay AND fuelId = :fuelId AND locationKey = :locationKey AND horizonDays = :horizon
        )
        """
    )
    suspend fun existsForHorizon(createdDay: String, fuelId: String, locationKey: String, horizon: Int): Boolean
}
