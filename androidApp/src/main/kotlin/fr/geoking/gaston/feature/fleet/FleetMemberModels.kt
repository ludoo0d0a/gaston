package fr.geoking.gaston.feature.fleet

import fr.geoking.gaston.persistence.FleetMemberEntity
import fr.geoking.gaston.persistence.FleetMaintenanceEventEntity

/**
 * Represents a member of a fleet (company vehicle, rental, etc.)
 */
data class FleetMember(
    val id: String,
    val deviceId: String?,
    val make: String = "",
    val model: String = "",
    val registrationNumber: String? = null,
    val vin: String? = null,
    val preferredFuelType: String = "SP95",
    val batteryCapacityKwh: Int? = null,
    val color: String = "",
    val acquiredAtEpochMs: Long,
    val odometerKmAtAcquisition: Int = 0,
    val currentOdometerKm: Int? = null,
    val maintenancePlanId: String? = null,
    val notes: String = "",
    val isSharedVehicle: Boolean = false,
) {
    companion object {
        /**
         * Create a fleet member from the persistence entity
         */
        fun fromEntity(entity: FleetMemberEntity): FleetMember = FleetMember(
            id = entity.id,
            deviceId = entity.deviceId,
            make = entity.make,
            model = entity.model,
            registrationNumber = entity.registrationNumber,
            vin = entity.vin,
            preferredFuelType = entity.preferredFuelType,
            batteryCapacityKwh = entity.batteryCapacityKwh,
            color = entity.color,
            acquiredAtEpochMs = entity.acquiredAtMs,
            odometerKmAtAcquisition = entity.odometerKmAtAcquisition,
            currentOdometerKm = entity.currentOdometerKm,
            maintenancePlanId = entity.maintenancePlanId,
            notes = entity.notes,
            isSharedVehicle = entity.isSharedVehicle,
        )

        /**
         * Create a fleet member for acquisition/addition
         */
        fun create(
            deviceId: String?,
            make: String,
            model: String,
            registrationNumber: String? = null,
            vin: String? = null,
            preferredFuelType: String = "SP95",
            batteryCapacityKwh: Int? = null,
            color: String = "",
            odometerKmAtAcquisition: Int = 0,
            isSharedVehicle: Boolean = false,
        ): FleetMember {
            val now = System.currentTimeMillis()
            return FleetMember(
                id = "fleet_${make}_${model}_${now}", // Generate simple ID
                deviceId = deviceId,
                make = make,
                model = model,
                registrationNumber = registrationNumber,
                vin = vin,
                preferredFuelType = preferredFuelType,
                batteryCapacityKwh = batteryCapacityKwh,
                color = color,
                acquiredAtEpochMs = now,
                odometerKmAtAcquisition = odometerKmAtAcquisition,
                isSharedVehicle = isSharedVehicle,
            )
        }
    }
}

/**
 * Represents a scheduled or upcoming maintenance event for fleet members
 */
data class FleetMaintenanceEvent(
    val id: String,
    val fleetMemberId: String,
    val eventType: MaintenanceEventType,
    val expectedDateEpochMs: Long,
    val completedAtEpochMs: Long? = null,
    val odometerKmAtEvent: Int? = null,
    val serviceKind: ServiceKind? = null,
    val providerName: String? = null,
    val invoiceReference: String? = null,
    val amountEur: Double? = null,
    val notes: String = "",
)
