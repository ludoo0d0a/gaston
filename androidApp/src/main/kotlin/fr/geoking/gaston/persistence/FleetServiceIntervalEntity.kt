package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents service intervals/mileage thresholds for fleet vehicles
 * Fleet managers can configure these centrally.
 */
@Entity(
    tableName = "fleet_service_intervals",
    indices = [
        Index(value = ["fleetId", "serviceKind"], unique = true)
    ]
)
data class FleetServiceIntervalEntity(
    @PrimaryKey
    val id: String,
    
    /**
     * The fleet this service interval configuration belongs to
     */
    val fleetId: String,
    
    /**
     * Type of service (OIL_CHANGE | TIRES | BRAKES | INSPECTION | REVISION | OTHER)
     */
    val serviceKind: String,
    
    /**
     * Interval in kilometers before service is due
     null = not applicable for this service type
     */
    val everyKm: Int? = null,
    
    /**
     * Interval in months (e.g., every 12 months)
     null = not applicable for this service type
     */
    val everyMonths: Int? = null,
    
    /**
     * Optional threshold percentage (e.g., 0.8 means trigger at 80% of interval)
     */
    val thresholdPercentage: Double? = null,
    
    /**
     * Priority level for this service (HIGH | MEDIUM | LOW)
     */
    val priority: String = "MEDIUM",
    
    /**
     * Provider recommendation URL or notes
     */
    val providerReferenceUrl: String? = null,
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) { }