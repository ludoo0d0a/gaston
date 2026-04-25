package fr.geoking.gaston.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MarketDailyQuoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<MarketDailyQuoteEntity>)

    @Query(
        """
        SELECT * FROM market_daily_quotes
        WHERE symbol = :symbol AND day >= :fromDay
        ORDER BY day ASC
        """
    )
    suspend fun since(symbol: String, fromDay: String): List<MarketDailyQuoteEntity>

    @Query("SELECT MAX(fetchedAtMs) FROM market_daily_quotes WHERE symbol = :symbol")
    suspend fun latestFetchMs(symbol: String): Long?
}
