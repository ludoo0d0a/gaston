package fr.geoking.gaston.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SupermarketEnrichRegionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegion(region: SupermarketEnrichRegionEntity): Long

    @Query(
        """
        SELECT * FROM supermarket_enrich_region
        WHERE loadedAtMs >= :minLoadedAtMs
        """,
    )
    suspend fun getRegionsFresherThan(minLoadedAtMs: Long): List<SupermarketEnrichRegionEntity>

    @Query("DELETE FROM supermarket_enrich_region WHERE loadedAtMs < :thresholdMs")
    suspend fun deleteOldRegions(thresholdMs: Long)

    @Query("DELETE FROM supermarket_enrich_region")
    suspend fun clearRegions()
}