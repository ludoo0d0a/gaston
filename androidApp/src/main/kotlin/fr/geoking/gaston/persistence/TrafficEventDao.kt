package fr.geoking.gaston.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrafficEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<TrafficEventEntity>)

    @Query(
        """
        SELECT * FROM traffic_events
        WHERE latMax >= :latMin AND latMin <= :latMax
          AND lonMax >= :lonMin AND lonMin <= :lonMax
        LIMIT :limit
        """
    )
    suspend fun getEventsInBbox(
        latMin: Double,
        lonMin: Double,
        latMax: Double,
        lonMax: Double,
        limit: Int
    ): List<TrafficEventEntity>

    @Query("SELECT MAX(fetchedAtMs) FROM traffic_events WHERE sourceId = :sourceId")
    suspend fun getLastFetchTimestamp(sourceId: String): Long?

    @Query("DELETE FROM traffic_events WHERE sourceId = :sourceId")
    suspend fun deleteEventsForSource(sourceId: String)

    @Query("UPDATE traffic_events SET zonePointsJson = :zonePointsJson WHERE primaryKey = :primaryKey")
    suspend fun updateZonePoints(primaryKey: String, zonePointsJson: String)

    @Query("DELETE FROM traffic_events")
    suspend fun clearAll()
}
