package fr.geoking.gaston

import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.anyProvidesElectric

fun AppSettings.effectiveMapEnergyFilterIds(): Set<String> {
    val useVehicle = useVehicleFilter || (selectedMapEnergyTypes.isEmpty() && vehicleBrand.isNotEmpty())
    return if (useVehicle) {
        when (vehicleEnergy) {
            "electric" -> setOf("electric")
            "hybrid" -> vehicleGasTypes + "electric"
            else -> vehicleGasTypes
        }
    } else {
        selectedMapEnergyTypes
    }
}

fun AppSettings.effectiveFuelBrandFilterIds(): Set<String> {
    val useVehicle = useVehicleFilter || (mapBrands.isEmpty() && vehicleBrand.isNotEmpty())
    return if (useVehicle) {
        if (fuelCard == FuelCard.Routex && (vehicleEnergy == "gas" || vehicleEnergy == "hybrid")) {
            // Official Routex alliance partners and common partners
            setOf("esso", "eni", "total", "shell", "aral", "totalenergies", "bp", "omv", "circle k", "texaco", "g&v", "avia")
        } else {
            emptySet()
        }
    } else {
        mapBrands
    }
}

fun AppSettings.effectiveIrvePowerLevels(): Set<Int> {
    val useVehicle = useVehicleFilter || (mapPowerLevels.isEmpty() && vehicleBrand.isNotEmpty())
    return if (useVehicle && (vehicleEnergy == "electric" || vehicleEnergy == "hybrid")) {
        vehiclePowerLevels
    } else {
        mapPowerLevels
    }
}

fun AppSettings.effectiveIrveOperatorFilter(): Set<String> {
    val useVehicle = useVehicleFilter || (mapIrveOperators.isEmpty() && vehicleBrand.isNotEmpty())
    return if (useVehicle && (vehicleEnergy == "electric" || vehicleEnergy == "hybrid")) {
        emptySet()
    } else {
        mapIrveOperators
    }
}

fun AppSettings.effectiveProviders(): Set<PoiProviderType> = selectedPoiProviders

fun Set<PoiProviderType>.isOnlyOverpass(): Boolean =
    isNotEmpty() && all { it == PoiProviderType.Overpass }

/**
 * Energy / brand / IRVE filters for station POIs. When [skipWhenOnlyOverpass] is true and
 * [providers] is only Overpass, returns [pois] unchanged (OSM amenity results).
 */
object StationMapFilters {

    fun apply(
        settings: AppSettings,
        pois: List<Poi>,
        providers: Set<PoiProviderType>,
        skipWhenOnlyOverpass: Boolean,
    ): List<Poi> {
        if (skipWhenOnlyOverpass && providers.isOnlyOverpass()) return pois

        var result = pois

        // Filter by energy type
        val energyFilters = settings.effectiveMapEnergyFilterIds()
        if (energyFilters.isNotEmpty()) {
            result = result.filter { MapPoiFilter.matchesEnergyFilter(it, energyFilters) }
        }

        // Filter by power range (IRVE)
        val powerFilters = settings.effectiveIrvePowerLevels()
        if (powerFilters.isNotEmpty()) {
            result = result.filter { poi ->
                val power = poi.powerKw
                !poi.isElectric || power == null || MapPoiFilter.powerMatchesAnyLevel(power, powerFilters)
            }
        }

        // Filter by operator (IRVE)
        val operatorFilters = settings.effectiveIrveOperatorFilter()
        if (operatorFilters.isNotEmpty()) {
            val operatorIds = operatorFilters.map { it.lowercase() }.toSet()
            result = result.filter { poi ->
                val op = poi.operator
                !poi.isElectric || op == null || operatorIds.any { id -> op.lowercase().contains(id) }
            }
        }

        // Filter by connector type (IRVE)
        val connectorFilters = settings.selectedMapConnectorTypes
        if (connectorFilters.isNotEmpty()) {
            result = result.filter { poi ->
                val types = poi.irveDetails?.connectorTypes
                !poi.isElectric || types == null || types.any { it in connectorFilters }
            }
        }

        // Filter by highway (autoroute)
        if (settings.filterOnlyHighwayStations) {
            result = result.filter { it.isOnHighway }
        }

        // Brand filter remains active as it's often used to find specific networks or for fuel card compatibility.
        val filterBrands = settings.effectiveFuelBrandFilterIds()
        if (filterBrands.isNotEmpty()) {
            val brandIds = filterBrands.map { it.lowercase() }.toSet()
            result = result.filter { poi ->
                val b = poi.brand
                // Hybrid stations (both gas and electric) should still be checked against the brand filter
                // if it's active, while pure electric stations are exempted.
                val isPureElectric = poi.isElectric && poi.fuelPrices.isNullOrEmpty()

                isPureElectric ||
                    b == null || // Don't filter out unknown brands (e.g. from OpenVanCamp / OSM)
                    brandIds.any { id -> b.lowercase().contains(id) }
            }
        }

        return result
    }

}
