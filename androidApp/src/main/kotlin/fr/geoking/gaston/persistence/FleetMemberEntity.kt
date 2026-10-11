package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a member of a fleet (company vehicle, rental, etc.)
 * Different from UserVehicle which represents an individual user's primary vehicle.
 */
@Entity(
    tableName = "fleet_members",
    indices = [
        Index(value = ["fleetId"], unique = false), // Multiple members per fleet
        Index(value = ["deviceId"])]  // Optional device tracking
)
data class FleetMemberEntity(
    @PrimaryKey
    val id: String,
    
    /**
     * The fleet this member belongs to (e.g., "company_fleet_001", "rental_group_europe")
     */
    val fleetId: String,
    
    /**
     * Unique device identifier for this fleet vehicle
     */
    val deviceId: String? = null,
    
    /**
     * Model/Make of the fleet vehicle
     */
    val make: String = "",
    
    /**
     * Model name (e.g., "Clio IV", "A3")
     */
    val model: String = "",
    
    /**
     * Registration/license plate number
     */
    val registrationNumber: String? = null,
    
    /**
     * VIN/Chassis number for fleet tracking
     */
    val vin: String? = null,
    
    /**
     * Fuel type preference (SP95, SP98, GAZOILE, E10, E85, H24)
     */
    val preferredFuelType: String = "SP95",
    
    /**
     * EV battery capacity in kWh (if applicable)
     */
    val batteryCapacityKwh: Int? = null,
    
    /**
     * Vehicle color for fleet identification
     */
    val color: String = "",
    
    /**
     * Acquisition date of the vehicle for the fleet
     */
    val acquiredAtMs: Long,
    
    /**
     * Mileage/odometer reading at acquisition time
     */
    val odometerKmAtAcquisition: Int = 0,
    
    /**
     * Current odometer/mileage (optional - can be updated)
     */
    val currentOdometerKm: Int? = null,
    
    /**
     * Maintenance plan ID for this vehicle type
     */
    val maintenancePlanId: String? = null,
    
    /**
     * Additional fleet-specific notes or attributes
     */
    val notes: String = "",
    
    /**
     * Whether this is a shared/rental vehicle (true) vs company-owned (false)
     */
    val isSharedVehicle: Boolean = false,
    
    val createdAt: Long = System.currentTimeMillis(),

    /**
     * Timestamp when this record was last updated
     */
    val updatedAt: Long = System.currentTimeMillis()
) { }