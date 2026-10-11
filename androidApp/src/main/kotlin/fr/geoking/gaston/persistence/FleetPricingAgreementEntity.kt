package fr.geoking.gaston.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents corporate pricing agreements for fleet fuel purchases
 * Stores negotiated rates and discount tiers.
 */
@Entity(
    tableName = "fleet_pricing_agreements",
    indices = [
        Index(value = ["fleetId", "fuelId", "discountTier"], unique = true)
    ]
)
data class FleetPricingAgreementEntity(
    @PrimaryKey
    val id: String,

    /**
     * The fleet this agreement applies to
     */
    val fleetId: String,

    /**
     * Fuel type covered by this agreement (e.g., "sp98", "gazole")
     */
    val fuelId: String,

    /**
     * Discount tier level (e.g., "TIER_1", "TIER_2", "PREMIUM")
     */
    val discountTier: String = "TIER_1",

    /**
     * Base price per unit at this tier
     */
    val basePrice: Double,

    /**
     * Discount percentage off base price
     */
    val discountPercentage: Int = 0,

    /**
     * Minimum volume or frequency requirement for this tier
     */
    val minVolumeRequirement: String? = null, // e.g., "1000 liters/month"

    /**
     * Provider/partner name (e.g., "TotalEnergies Corporate")
     */
    val providerName: String,

    /**
     * Contract start date
     */
    val validFromMs: Long,

    /**
     * Contract end date (null if ongoing)
     */
    val validToMs: Long? = null,

    /**
     * Additional terms or conditions (JSON string for flexibility)
     */
    val termsJson: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) { }