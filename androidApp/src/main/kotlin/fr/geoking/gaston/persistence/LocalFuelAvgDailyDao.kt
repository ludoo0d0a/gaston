package fr.geoking.gaston.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocalFuelAvgDailyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: LocalFuelAvgDailyEntity)

    @Query(
        """
        SELECT * FROM local_fuel_avg_daily
        WHERE locationKey = :locationKey AND fuelId = :fuelId AND day >= :fromDay
        ORDER BY day ASC
        """
    )
    suspend fun series(locationKey: String, fuelId: String, fromDay: String): List<LocalFuelAvgDailyEntity>

    @Query(
        """
        SELECT * FROM local_fuel_avg_daily
        WHERE locationKey = :locationKey AND fuelId = :fuelId AND day = :day
        LIMIT 1
        """
    )
    suspend fun getDay(locationKey: String, fuelId: String, day: String): LocalFuelAvgDailyEntity?
}
