package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents scheduled or upcoming maintenance events for fleet members
 * Similar to MaintenanceEventEntity but scoped to fleet operations.
 */
@Entity(
    tableName = "fleet_maintenance_events",
    indices = [
        Index(value = ["fleetId", "deviceId", "eventDateMs"]),
        Index(value = ["eventType"])
    ]
)
data class FleetMaintenanceEventEntity(
    @PrimaryKey
    val id: String,
    
    /**
     * Reference to the fleet member this maintenance event is for
     */
    val fleetMemberId: String,
    
    /**
     * Type of maintenance (SERVICE | FUEL | CHARGE | ODOMETER | INSPECTION | RECALL | OTHER)
     */
    val eventType: String,
    
    /**
     * Scheduled/expected date for the event
     */
    val expectedDateMs: Long,
    
    /**
     * Actual completion date (null if not completed yet)
     */
    val completedAtMs: Long? = null,
    
    /**
     * Odometer reading at time of event
     */
    val odometerKmAtEvent: Int? = null,
    
    /**
     * Maintenance category (e.g., "BRAKE_CHANGE", "OIL_CHANGE", "TIRE_ROTATION")
     */
    val serviceKind: String? = null,
    
    /**
     * Provider/technician who performed the maintenance
     */
    val providerName: String? = null,
    
    /**
     * Invoice/receipt reference number
     */
    val invoiceReference: String? = null,
    
    /**
     * Cost of this maintenance event (if any)
     */
    val amountEur: Double? = null,
    
    /**
     * Detailed notes about the maintenance performed
     */
    val notes: String = "",
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) { }