package fr.geoking.gaston

import fr.geoking.gaston.parking.ParkingRegion
import fr.geoking.gaston.poi.EnergyFilterMode
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.anyProvidesElectric
import fr.geoking.gaston.poi.autoProvidersForCountries
import java.util.Locale

fun AppSettings.effectiveEnergyFilterMode(): EnergyFilterMode {
    if (!useVehicleFilter) return mapEnergyMode

    return when (vehicleEnergy) {
        "electric" -> EnergyFilterMode.Electric
        "hybrid" -> EnergyFilterMode.Hybrid
        else -> EnergyFilterMode.Fuel
    }
}

fun categoryFromAmenityId(id: String): PoiCategory? = when (id) {
    "toilets" -> PoiCategory.Toilet
    "drinking_water" -> PoiCategory.DrinkingWater
    "camp_site" -> PoiCategory.Camping
    "caravan_site" -> PoiCategory.CaravanSite
    "picnic_site" -> PoiCategory.PicnicSite
    "truck_stop" -> PoiCategory.TruckStop
    "rest_area" -> PoiCategory.RestArea
    "restaurant" -> PoiCategory.Restaurant
    "fast_food" -> PoiCategory.FastFood
    "speed_camera" -> PoiCategory.Radar
    "parking" -> PoiCategory.Parking
    "viewpoint" -> PoiCategory.Viewpoint
    else -> null
}

fun AppSettings.effectiveAllowedCategories(): Set<PoiCategory> {
    // Specialized "Parking only" mode for dashboard
    if (poiProviderSelectionMode == PoiProviderSelectionMode.Manual &&
        selectedPoiProviders == setOf(fr.geoking.gaston.poi.PoiProviderType.Overpass) &&
        selectedOverpassAmenityTypes == setOf("parking")
    ) {
        return setOf(PoiCategory.Parking)
    }

    val categories = mutableSetOf<PoiCategory>()

    // Amenities: strictly based on selection
    selectedOverpassAmenityTypes.mapNotNullTo(categories) { categoryFromAmenityId(it) }

    // Energy: Gas/Irve
    val mode = effectiveEnergyFilterMode()
    when (mode) {
        EnergyFilterMode.Fuel -> categories.add(PoiCategory.Gas)
        EnergyFilterMode.Electric -> categories.add(PoiCategory.Irve)
        EnergyFilterMode.Hybrid -> {
            categories.add(PoiCategory.Gas)
            categories.add(PoiCategory.Irve)
        }
    }

    // Vehicle-specific extra amenities (only when "For my car" is active)
    if (useVehicleFilter) {
        when (vehicleType) {
            VehicleType.Truck -> {
                categories.add(PoiCategory.TruckStop)
                categories.add(PoiCategory.RestArea)
            }
            VehicleType.Motorhome -> {
                categories.add(PoiCategory.CaravanSite)
                categories.add(PoiCategory.Camping)
                categories.add(PoiCategory.PicnicSite)
            }
            else -> {}
        }
    }

    return categories
}

fun AppSettings.effectiveMapEnergyFilterIds(): Set<String> {
    val useVehicle = useVehicleFilter || (selectedMapEnergyTypes.isEmpty() && vehicleBrand.isNotEmpty())
    if (useVehicle) {
        return when (vehicleEnergy) {
            "electric" -> setOf("electric")
            "hybrid" -> vehicleGasTypes + "electric"
            else -> vehicleGasTypes
        }
    }

    return when (mapEnergyMode) {
        EnergyFilterMode.Fuel -> selectedMapEnergyTypes
        EnergyFilterMode.Electric -> setOf("electric")
        EnergyFilterMode.Hybrid -> selectedMapEnergyTypes + "electric"
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

/**
 * Provider set actually used by the app.
 *
 * - [PoiProviderSelectionMode.Manual]: uses [selectedPoiProviders]
 * - [PoiProviderSelectionMode.Auto]: uses current country (GPS/network) when available
 *
 * When [countryCodes] is empty, falls back to [selectedPoiProviders] (manual override).
 */
fun AppSettings.effectiveProviders(countryCodes: List<String> = emptyList()): Set<PoiProviderType> {
    val base = if (poiProviderSelectionMode == PoiProviderSelectionMode.Manual) {
        selectedPoiProviders
    } else {
        if (countryCodes.isNotEmpty()) {
            val mode = effectiveEnergyFilterMode()
            val wantElectric = mode == EnergyFilterMode.Electric || mode == EnergyFilterMode.Hybrid
            val wantFuel = mode == EnergyFilterMode.Fuel || mode == EnergyFilterMode.Hybrid

            autoProvidersForCountries(
                countryCodes = countryCodes,
                wantFuel = wantFuel,
                wantElectric = wantElectric,
                fallbackManual = selectedPoiProviders
            )
        } else {
            selectedPoiProviders
        }
    }
    return base
}

/** ISO country codes for [latitude]/[longitude] when it falls in known [ParkingRegion]s. */
fun countryCodesAtMapPosition(latitude: Double, longitude: Double): List<String> =
    ParkingRegion.allContaining(latitude, longitude).map { it.countryCode }

/** Human-readable countries for the map position (same regions as auto provider selection). */
fun countryDisplayLabelAtMapPosition(
    latitude: Double,
    longitude: Double,
    locale: Locale = Locale.getDefault(),
): String {
    val isos = countryCodesAtMapPosition(latitude, longitude)
    if (isos.isEmpty()) return "Unknown region"

    return isos.joinToString(" / ") { iso ->
        val name = Locale("", iso).getDisplayCountry(locale).ifBlank { null }
        if (name != null && !name.equals(iso, ignoreCase = true)) "$name ($iso)" else iso
    }
}

/**
 * Resolves auto mode from the map position (same logic as [SelectorPoiProvider]).
 * Manual mode ignores coordinates and returns [selectedPoiProviders].
 */
fun AppSettings.effectiveProvidersAt(latitude: Double, longitude: Double): Set<PoiProviderType> =
    effectiveProviders(countryCodes = countryCodesAtMapPosition(latitude, longitude))

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
        var result = pois

        // Filter by allowed categories (strictly based on settings)
        val allowedCategories = settings.effectiveAllowedCategories()
        result = result.filter { poi ->
            val cat = poi.poiCategory ?: if (poi.isElectric) PoiCategory.Irve else PoiCategory.Gas
            cat in allowedCategories
        }

        if (skipWhenOnlyOverpass && providers.isOnlyOverpass()) return result

        // Filter by energy type
        val mode = settings.effectiveEnergyFilterMode()
        val fuelFilters = settings.selectedMapEnergyTypes
        result = result.filter { poi ->
            MapPoiFilter.matchesEnergyFilter(poi, mode, fuelFilters)
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
