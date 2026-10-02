package fr.geoking.gaston.feature.maintenance

import fr.geoking.gaston.persistence.MaintenanceEventEntity
import fr.geoking.gaston.persistence.ServiceIntervalEntity

fun MaintenanceEventEntity.toDomain(): MaintenanceEvent = MaintenanceEvent(
    id = id,
    vehicleId = vehicleId,
    type = MaintenanceEventType.fromStorage(type),
    dateEpochMs = dateEpochMs,
    odometerKm = odometerKm,
    title = title,
    notes = notes,
    amountEur = amountEur,
    volumeLiters = volumeLiters,
    energyKwh = energyKwh,
    serviceKind = ServiceKind.fromStorage(serviceKind),
    stationName = stationName,
)

fun MaintenanceEvent.toEntity(): MaintenanceEventEntity = MaintenanceEventEntity(
    id = id,
    vehicleId = vehicleId,
    type = type.name,
    dateEpochMs = dateEpochMs,
    odometerKm = odometerKm,
    title = title,
    notes = notes,
    amountEur = amountEur,
    volumeLiters = volumeLiters,
    energyKwh = energyKwh,
    serviceKind = serviceKind?.name,
    stationName = stationName,
)

fun ServiceIntervalEntity.toDomain(): ServiceInterval = ServiceInterval(
    id = id,
    vehicleId = vehicleId,
    serviceKind = ServiceKind.fromStorage(serviceKind) ?: ServiceKind.OTHER,
    everyKm = everyKm,
    everyMonths = everyMonths,
    lastEventId = lastEventId,
    lastDoneAtMs = lastDoneAtMs,
    lastOdometerKm = lastOdometerKm,
)

fun ServiceInterval.toEntity(): ServiceIntervalEntity = ServiceIntervalEntity(
    id = id,
    vehicleId = vehicleId,
    serviceKind = serviceKind.name,
    everyKm = everyKm,
    everyMonths = everyMonths,
    lastEventId = lastEventId,
    lastDoneAtMs = lastDoneAtMs,
    lastOdometerKm = lastOdometerKm,
)
