package fr.geoking.gaston.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: MaintenanceEventEntity)

    @Query("SELECT * FROM maintenance_events WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MaintenanceEventEntity?

    @Query(
        """
        SELECT * FROM maintenance_events
        WHERE vehicleId = :vehicleId
        ORDER BY dateEpochMs DESC
        """
    )
    fun observeForVehicle(vehicleId: String): Flow<List<MaintenanceEventEntity>>

    @Query(
        """
        SELECT * FROM maintenance_events
        WHERE vehicleId = :vehicleId
        ORDER BY dateEpochMs DESC
        """
    )
    suspend fun listForVehicle(vehicleId: String): List<MaintenanceEventEntity>

    @Query(
        """
        SELECT * FROM maintenance_events
        WHERE vehicleId = :vehicleId AND type = :type
        ORDER BY dateEpochMs DESC
        """
    )
    suspend fun listForVehicleByType(vehicleId: String, type: String): List<MaintenanceEventEntity>

    @Query(
        """
        SELECT * FROM maintenance_events
        WHERE vehicleId = :vehicleId AND type = :type
        ORDER BY dateEpochMs DESC
        LIMIT 1
        """
    )
    suspend fun latestOfType(vehicleId: String, type: String): MaintenanceEventEntity?

    @Query(
        """
        SELECT * FROM maintenance_events
        WHERE vehicleId = :vehicleId AND odometerKm IS NOT NULL
        ORDER BY dateEpochMs DESC
        LIMIT 1
        """
    )
    suspend fun latestWithOdometer(vehicleId: String): MaintenanceEventEntity?

    @Query(
        """
        SELECT * FROM maintenance_events
        WHERE vehicleId = :vehicleId AND dateEpochMs >= :fromMs
        ORDER BY dateEpochMs ASC
        """
    )
    suspend fun listSince(vehicleId: String, fromMs: Long): List<MaintenanceEventEntity>

    @Query("DELETE FROM maintenance_events WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM maintenance_events WHERE vehicleId = :vehicleId")
    suspend fun deleteAllForVehicle(vehicleId: String)
}
