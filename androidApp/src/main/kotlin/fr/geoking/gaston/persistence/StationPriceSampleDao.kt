package fr.geoking.gaston.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StationPriceSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<StationPriceSampleEntity>)

    @Query(
        """
        SELECT * FROM station_price_samples
        WHERE stationId = :stationId AND fuelId = :fuelId
        ORDER BY observedAtMs DESC
        LIMIT 1
        """
    )
    suspend fun latestSample(stationId: String, fuelId: String): StationPriceSampleEntity?

    @Query(
        """
        SELECT * FROM station_price_samples
        WHERE stationId = :stationId AND observedAtMs >= :fromMs
        ORDER BY observedAtMs ASC
        """
    )
    suspend fun samplesSince(stationId: String, fromMs: Long): List<StationPriceSampleEntity>
}

