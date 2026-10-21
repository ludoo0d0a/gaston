package fr.geoking.gaston.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RoadCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRegion(region: RoadCacheRegionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoads(roads: List<RoadPolylineEntity>)

    @Query("""
        SELECT * FROM road_cache_region
        WHERE minLat <= :latMin AND maxLat >= :latMax
          AND minLon <= :lonMin AND maxLon >= :lonMax
          AND updatedAtMs >= :minUpdatedAtMs
        LIMIT 1
    """)
    suspend fun findCoveringRegion(
        latMin: Double,
        latMax: Double,
        lonMin: Double,
        lonMax: Double,
        minUpdatedAtMs: Long
    ): RoadCacheRegionEntity?

    @Query("""
        SELECT * FROM road_polyline_entity
        WHERE maxLat >= :latMin AND minLat <= :latMax
          AND maxLon >= :lonMin AND minLon <= :lonMax
          AND updatedAtMs >= :minUpdatedAtMs
    """)
    suspend fun getRoadsInBbox(
        latMin: Double,
        latMax: Double,
        lonMin: Double,
        lonMax: Double,
        minUpdatedAtMs: Long
    ): List<RoadPolylineEntity>

    @Query("DELETE FROM road_cache_region WHERE updatedAtMs < :thresholdMs")
    suspend fun deleteOldRegions(thresholdMs: Long)

    @Query("DELETE FROM road_polyline_entity WHERE updatedAtMs < :thresholdMs")
    suspend fun deleteOldRoads(thresholdMs: Long)

    @Query("DELETE FROM road_cache_region")
    suspend fun clearRegions()

    @Query("DELETE FROM road_polyline_entity")
    suspend fun clearRoads()
}
