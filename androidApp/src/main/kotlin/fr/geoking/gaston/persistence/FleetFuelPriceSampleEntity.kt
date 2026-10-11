package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents fuel price data points specifically for fleet cost analysis
 * Can track corporate pricing agreements and volume discounts.
 */
@Entity(
    tableName = "fleet_fuel_price_samples",
    indices = [
        Index(value = ["stationId", "fuelId", "observedAtMs"])
    ]
)
data class FleetFuelPriceSampleEntity(
    @PrimaryKey
    val id: String,
    
    /**
     * Identifier for this station in the fleet system
     */
    val stationId: String,
    
    /**
     * Fuel type identifier (e.g., "sp98", "gazole", "e10")
     */
    val fuelId: String,
    
    /**
     * Original label as received from source
     */
    val fuelName: String,
    
    /**
     * Price per unit (liter for liquid fuels, kWh for electricity)
     */
    val price: Double,
    
    /**
     * Currency code (EUR, USD, etc.)
     */
    val currency: String = "EUR",
    
    /**
     * Volume purchased in this transaction (for tracking fuel card usage)
     */
    val volumeLiters: Double? = null,
    
    /**
     * Whether the station is out of stock for this fuel type
     */
    val outOfStock: Boolean = false,
    
    /**
     * When this price was observed
     */
    val observedAtMs: Long,
    
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) { }