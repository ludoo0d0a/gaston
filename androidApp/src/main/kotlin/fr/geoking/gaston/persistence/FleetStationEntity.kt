package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a fleet station/partner specifically for fleet operations
 * Can track which stations are preferred/contracted by the fleet.
 */
@Entity(
    tableName = "fleet_stations",
    indices = [
        Index(value = ["stationId"]),
        Index(value = ["locationKey"])
    ]
)
data class FleetStationEntity(
    @PrimaryKey
    val id: String,
    
    /**
     * Identifier for this station in the fleet system
     */
    val stationId: String,
    
    /**
     * The fleet managing/using this station preference
     */
    val fleetId: String,
    
    /**
     * Station name (e.g., "Shell Distribution Center Paris")
     */
    val stationName: String,
    
    /**
     * Address of the station
     */
    val address: String,
    
    /**
     * Geographic location for mapping
     */
    val latitude: Double,
    val longitude: Double,
    
    /**
     * Types of fuels/services available here (SP95, SP98, GAZOILE, E10, CHARGE, etc.)
     */
    val availableFuelTypes: List<String> = emptyList(),
    
    /**
     * Whether this station is a preferred/contracted location
     */
    val isPreferredStation: Boolean = false,
    
    /**
     * Priority level for routing to this station
     */
    val priorityLevel: Int = 1,
    
    /**
     * Any restrictions (e.g., card required, commercial vehicle only)
     */
    val restrictions: String? = null,
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) { }