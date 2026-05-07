package fr.geoking.gaston.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NationalFuelPriceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(price: NationalFuelPriceEntity)

    @Query(
        """
        SELECT * FROM national_fuel_prices
        WHERE countryCode = :countryCode AND fuelId = :fuelId AND day = :day
        LIMIT 1
        """
    )
    suspend fun getPrice(countryCode: String, fuelId: String, day: String): NationalFuelPriceEntity?

    @Query(
        """
        SELECT * FROM national_fuel_prices
        WHERE countryCode = :countryCode AND fuelId = :fuelId AND day >= :fromDay
        ORDER BY day DESC
        """
    )
    suspend fun getPricesSince(countryCode: String, fuelId: String, fromDay: String): List<NationalFuelPriceEntity>

    @Query("DELETE FROM national_fuel_prices WHERE updatedAtMs < :beforeMs")
    suspend fun deleteOldPrices(beforeMs: Long)
}
