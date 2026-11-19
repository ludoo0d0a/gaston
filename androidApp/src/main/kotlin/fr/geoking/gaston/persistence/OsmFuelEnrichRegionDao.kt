package fr.geoking.gaston.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OsmFuelEnrichRegionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegion(region: OsmFuelEnrichRegionEntity): Long

    @Query(
        """
        SELECT * FROM osm_fuel_enrich_region
        WHERE loadedAtMs >= :minLoadedAtMs
        """,
    )
    suspend fun getRegionsFresherThan(minLoadedAtMs: Long): List<OsmFuelEnrichRegionEntity>

    @Query("DELETE FROM osm_fuel_enrich_region WHERE loadedAtMs < :thresholdMs")
    suspend fun deleteOldRegions(thresholdMs: Long)

    @Query("DELETE FROM osm_fuel_enrich_region")
    suspend fun clearRegions()
}
