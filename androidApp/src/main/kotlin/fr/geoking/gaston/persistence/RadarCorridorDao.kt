package fr.geoking.gaston.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RadarCorridorDao {
    @Query("SELECT * FROM radar_corridor_entity WHERE poiId = :poiId LIMIT 1")
    suspend fun getCorridor(poiId: String): RadarCorridorEntity?

    @Query("SELECT * FROM radar_corridor_entity WHERE poiId IN (:poiIds)")
    suspend fun getCorridorsForPois(poiIds: List<String>): List<RadarCorridorEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorridors(corridors: List<RadarCorridorEntity>)

    @Query("DELETE FROM radar_corridor_entity WHERE updatedAtMs < :thresholdMs")
    suspend fun deleteOlderThan(thresholdMs: Long)

    @Query("DELETE FROM radar_corridor_entity")
    suspend fun clear()
}
