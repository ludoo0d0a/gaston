package fr.geoking.gaston.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceIntervalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(interval: ServiceIntervalEntity)

    @Query("SELECT * FROM service_intervals WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ServiceIntervalEntity?

    @Query(
        """
        SELECT * FROM service_intervals
        WHERE vehicleId = :vehicleId
        ORDER BY serviceKind ASC
        """
    )
    fun observeForVehicle(vehicleId: String): Flow<List<ServiceIntervalEntity>>

    @Query(
        """
        SELECT * FROM service_intervals
        WHERE vehicleId = :vehicleId
        ORDER BY serviceKind ASC
        """
    )
    suspend fun listForVehicle(vehicleId: String): List<ServiceIntervalEntity>

    @Query("SELECT * FROM service_intervals")
    suspend fun listAll(): List<ServiceIntervalEntity>

    @Query("DELETE FROM service_intervals WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM service_intervals WHERE vehicleId = :vehicleId")
    suspend fun deleteAllForVehicle(vehicleId: String)
}
