package fr.geoking.gaston.feature.maintenance

import fr.geoking.gaston.persistence.MaintenanceEventDao
import fr.geoking.gaston.persistence.ServiceIntervalDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class MaintenanceRepository(
    private val eventDao: MaintenanceEventDao,
    private val intervalDao: ServiceIntervalDao,
) {
    fun observeEvents(vehicleId: String): Flow<List<MaintenanceEvent>> =
        eventDao.observeForVehicle(vehicleId).map { list -> list.map { it.toDomain() } }

    fun observeIntervals(vehicleId: String): Flow<List<ServiceInterval>> =
        intervalDao.observeForVehicle(vehicleId).map { list -> list.map { it.toDomain() } }

    suspend fun getEvent(id: String): MaintenanceEvent? =
        eventDao.getById(id)?.toDomain()

    suspend fun listEvents(vehicleId: String): List<MaintenanceEvent> =
        eventDao.listForVehicle(vehicleId).map { it.toDomain() }

    suspend fun listEventsSince(vehicleId: String, fromMs: Long): List<MaintenanceEvent> =
        eventDao.listSince(vehicleId, fromMs).map { it.toDomain() }

    suspend fun latestService(vehicleId: String): MaintenanceEvent? =
        eventDao.latestOfType(vehicleId, MaintenanceEventType.SERVICE.name)?.toDomain()

    suspend fun latestOdometerKm(vehicleId: String): Int? =
        eventDao.latestWithOdometer(vehicleId)?.odometerKm

    suspend fun upsertEvent(event: MaintenanceEvent) {
        eventDao.upsert(event.toEntity())
        if (event.type == MaintenanceEventType.SERVICE && event.serviceKind != null) {
            syncIntervalAfterService(event)
        }
    }

    suspend fun deleteEvent(id: String) {
        eventDao.deleteById(id)
    }

    suspend fun deleteAllForVehicle(vehicleId: String) {
        eventDao.deleteAllForVehicle(vehicleId)
        intervalDao.deleteAllForVehicle(vehicleId)
    }

    suspend fun listIntervals(vehicleId: String): List<ServiceInterval> =
        intervalDao.listForVehicle(vehicleId).map { it.toDomain() }

    suspend fun listAllIntervals(): List<ServiceInterval> =
        intervalDao.listAll().map { it.toDomain() }

    suspend fun upsertInterval(interval: ServiceInterval) {
        intervalDao.upsert(interval.toEntity())
    }

    suspend fun deleteInterval(id: String) {
        intervalDao.deleteById(id)
    }

    /**
     * Ensures default intervals exist for [vehicleId] (idempotent per service kind).
     */
    suspend fun ensureDefaultIntervals(vehicleId: String) {
        if (vehicleId.isBlank()) return
        val existing = intervalDao.listForVehicle(vehicleId).map { it.serviceKind }.toSet()
        DEFAULT_INTERVALS.forEach { (kind, everyKm, everyMonths) ->
            if (kind.name !in existing) {
                intervalDao.upsert(
                    ServiceInterval(
                        id = newMaintenanceId(),
                        vehicleId = vehicleId,
                        serviceKind = kind,
                        everyKm = everyKm,
                        everyMonths = everyMonths,
                    ).toEntity()
                )
            }
        }
    }

    private suspend fun syncIntervalAfterService(event: MaintenanceEvent) {
        val kind = event.serviceKind ?: return
        val intervals = intervalDao.listForVehicle(event.vehicleId)
        val match = intervals.firstOrNull { it.serviceKind == kind.name }
        if (match != null) {
            intervalDao.upsert(
                match.copy(
                    lastEventId = event.id,
                    lastDoneAtMs = event.dateEpochMs,
                    lastOdometerKm = event.odometerKm ?: match.lastOdometerKm,
                )
            )
        }
    }

    companion object {
        private val DEFAULT_INTERVALS = listOf(
            Triple(ServiceKind.OIL_CHANGE, 15_000, 12),
            Triple(ServiceKind.INSPECTION, null, 24),
            Triple(ServiceKind.TIRES, 40_000, 48),
            Triple(ServiceKind.BRAKES, 30_000, 36),
            Triple(ServiceKind.REVISION, 20_000, 24),
        )
    }
}

fun newMaintenanceId(): String = UUID.randomUUID().toString()
