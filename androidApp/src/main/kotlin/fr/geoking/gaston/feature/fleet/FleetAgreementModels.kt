package fr.geoking.gaston.feature.fleet

import fr.geoking.gaston.persistence.FleetMemberEntity
import fr.geoking.gaston.persistence.FleetMaintenanceEventEntity
import fr.geoking.gaston.persistence.FleetStationEntity
import fr.geoking.gaston.persistence.FleetFuelPriceSampleEntity
import fr.geoking.gaston.persistence.FleetPricingAgreementEntity
import fr.geoking.gaston.persistence.FleetServiceIntervalEntity

/**
 * Represents service interval configuration for fleet vehicles
 */
data class FleetServiceInterval(
    val id: String,
    val fleetId: String,
    val serviceKind: ServiceKind,
    val everyKm: Int? = null,
    val everyMonths: Int? = null,
    val thresholdPercentage: Double? = null,
    val priority: MaintenancePriority = MaintenancePriority.MEDIUM,
    val providerReferenceUrl: String? = null,
)

/**
 * Represents a fleet station/partner for fleet operations
 */
data class FleetStation(
    val id: String,
    val stationId: String,
    val fleetId: String,
    val stationName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val availableFuelTypes: List<String> = emptyList(),
    val isPreferredStation: Boolean = false,
    val priorityLevel: Int = 1,
    val restrictions: String? = null,
) {
    companion object {
        /**
         * Create from persistence entity
         */
        fun fromEntity(entity: FleetStationEntity): FleetStation = FleetStation(
            id = entity.id,
            stationId = entity.stationId,
            fleetId = entity.fleetId,
            stationName = entity.stationName,
            address = entity.address,
            latitude = entity.latitude,
            longitude = entity.longitude,
            availableFuelTypes = entity.availableFuelTypes,
            isPreferredStation = entity.isPreferredStation,
            priorityLevel = entity.priorityLevel,
            restrictions = entity.restrictions,
        )
    }
}

/**
 * Represents fuel price data points for fleet cost analysis
 */
data class FleetFuelPriceSample(
    val id: String,
    val stationId: String,
    val fuelId: String,
    val fuelName: String,
    val price: Double,
    val currency: String = "EUR",
    val volumeLiters: Double? = null,
    val outOfStock: Boolean = false,
    val observedAtEpochMs: Long,
) {
    companion object {
        /**
         * Create from persistence entity
         */
        fun fromEntity(entity: FleetFuelPriceSampleEntity): FleetFuelPriceSample = FleetFuelPriceSample(
            id = entity.id,
            stationId = entity.stationId,
            fuelId = entity.fuelId,
            fuelName = entity.fuelName,
            price = entity.price,
            currency = entity.currency,
            volumeLiters = entity.volumeLiters,
            outOfStock = entity.outOfStock,
            observedAtEpochMs = entity.observedAtMs,
        )
    }
}

/**
 * Represents corporate pricing agreements for fleet fuel purchases
 */
data class FleetPricingAgreement(
    val id: String,
    val fleetId: String,
    val fuelId: String,
    val discountTier: String = "TIER_1",
    val basePrice: Double,
    val discountPercentage: Int = 0,
    val minVolumeRequirement: String? = null,
    val providerName: String,
    val validFromEpochMs: Long,
    val validToEpochMs: Long? = null,
    val termsJson: String? = null,
) {
    companion object {
        /**
         * Create from persistence entity
         */
        fun fromEntity(entity: FleetPricingAgreementEntity): FleetPricingAgreement = FleetPricingAgreement(
            id = entity.id,
            fleetId = entity.fleetId,
            fuelId = entity.fuelId,
            discountTier = entity.discountTier,
            basePrice = entity.basePrice,
            discountPercentage = entity.discountPercentage,
            minVolumeRequirement = entity.minVolumeRequirement,
            providerName = entity.providerName,
            validFromEpochMs = entity.validFromMs,
            validToEpochMs = entity.validToMs,
            termsJson = entity.termsJson,
        )
    }
}
