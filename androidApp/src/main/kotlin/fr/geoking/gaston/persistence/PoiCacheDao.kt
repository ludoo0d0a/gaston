package fr.geoking.gaston.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PoiCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPois(pois: List<PoiCacheEntity>)

    @Query("""
        SELECT * FROM poi_cache
        WHERE latitude BETWEEN :latMin AND :latMax
          AND longitude BETWEEN :lonMin AND :lonMax
          AND updatedAtMs >= :minUpdatedAtMs
    """)
    suspend fun getPoisInRegion(
        latMin: Double,
        latMax: Double,
        lonMin: Double,
        lonMax: Double,
        minUpdatedAtMs: Long
    ): List<PoiCacheEntity>

    @Query("DELETE FROM poi_cache WHERE updatedAtMs < :thresholdMs")
    suspend fun deleteOldPois(thresholdMs: Long)

    @Query("DELETE FROM poi_cache")
    suspend fun clearCache()
}
