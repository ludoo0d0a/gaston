package fr.geoking.gaston

import fr.geoking.gaston.parking.ParkingRegion
import fr.geoking.gaston.poi.EnergyFilterMode
import fr.geoking.gaston.poi.MapPoiFilter
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProviderType
import fr.geoking.gaston.poi.autoProvidersForCountries
import fr.geoking.gaston.poi.calculateBoundsFromMapViewport
import java.util.Locale

fun AppSettings.effectiveEnergyFilterMode(): EnergyFilterMode {
    if (!useVehicleFilter) return mapEnergyMode

    return when (vehicleEnergy) {
        "electric" -> EnergyFilterMode.Electric
        "hybrid" -> EnergyFilterMode.Hybrid
        else -> EnergyFilterMode.Fuel
    }
}

fun AppSettings.isSwapExclusive(): Boolean =
    !useVehicleFilter && selectedMapEnergyTypes.contains("swap")

/** True when the user explicitly selected "Other" (amenities) mode. */
fun AppSettings.isOtherModeActive(): Boolean =
    poiProviderSelectionMode == PoiProviderSelectionMode.Manual &&
        selectedPoiProviders == setOf(PoiProviderType.Overpass)

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
    "post_box" -> PoiCategory.PostBox
    "water" -> PoiCategory.WaterBody
    "cafe" -> PoiCategory.Cafe
    "supermarket" -> PoiCategory.Supermarket
    else -> null
}

fun AppSettings.effectiveAllowedCategories(): Set<PoiCategory> {
    val categories = mutableSetOf<PoiCategory>()

    // Amenities: strictly based on selection
    selectedOverpassAmenityTypes.mapNotNullTo(categories) { categoryFromAmenityId(it) }

    // In "Other" mode, we ONLY want the selected amenities.
    if (isOtherModeActive()) {
        return categories
    }

    // When Swap is exclusive, we ONLY want battery swap (plus any selected amenities above)
    if (isSwapExclusive()) {
        categories.add(PoiCategory.BatterySwap)
        return categories
    }

    // Energy: Gas/Irve/Swap
    val mode = effectiveEnergyFilterMode()
    when (mode) {
        EnergyFilterMode.Fuel -> {
            categories.add(PoiCategory.Gas)
            // Show swap stations in Fuel mode if explicitly requested
            if (selectedMapEnergyTypes.contains("swap")) {
                categories.add(PoiCategory.BatterySwap)
            }
        }
        EnergyFilterMode.Electric -> {
            categories.add(PoiCategory.Irve)
            categories.add(PoiCategory.BatterySwap)
        }
        EnergyFilterMode.Hybrid -> {
            categories.add(PoiCategory.Gas)
            categories.add(PoiCategory.Irve)
            categories.add(PoiCategory.BatterySwap)
        }
    }

    // Vehicle-specific extra amenities (only when "For my vehicle" is active)
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
            VehicleType.Motorcycle -> {
                // For motorcycles, battery swap is often relevant
                categories.add(PoiCategory.BatterySwap)
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
            // When Routex card is active, we rely on the RoutexProvider and source filtering.
            // Returning an empty set here prevents over-filtering by brand name (which can be inconsistent).
            emptySet()
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

fun AppSettings.wantsFuelProviders(): Boolean {
    if (isOtherModeActive() || isSwapExclusive()) return false
    val mode = effectiveEnergyFilterMode()
    return mode == EnergyFilterMode.Fuel || mode == EnergyFilterMode.Hybrid
}

fun AppSettings.wantsElectricProviders(): Boolean {
    if (isOtherModeActive()) return false
    if (isSwapExclusive()) return true
    val mode = effectiveEnergyFilterMode()
    return mode == EnergyFilterMode.Electric || mode == EnergyFilterMode.Hybrid
}

fun Set<PoiProviderType>.filterProvidersForEnergy(
    wantFuel: Boolean,
    wantElectric: Boolean,
): Set<PoiProviderType> {
    if (wantFuel && wantElectric) return this
    return filter { type ->
        (wantFuel && type.providesFuel) ||
            (wantElectric && (type.providesElectric || type.providesSwap))
    }.toSet()
}

/**
 * Provider set actually used by the app.
 *
 * - [PoiProviderSelectionMode.Manual]: uses [selectedPoiProviders]
 * - [PoiProviderSelectionMode.Auto]: uses current country (GPS/network) when available
 *
 * Fuel / electric sources follow the energy selector: fuel mode loads fuel providers only,
 * electric (or swap-only) loads electric providers only, hybrid loads both.
 *
 * When [countryCodes] is empty, falls back to [selectedPoiProviders] (manual override).
 */
fun AppSettings.effectiveProviders(countryCodes: List<String> = emptyList()): Set<PoiProviderType> {
    val wantFuel = wantsFuelProviders()
    val wantElectric = wantsElectricProviders()

    val base = if (poiProviderSelectionMode == PoiProviderSelectionMode.Manual) {
        selectedPoiProviders
    } else {
        if (countryCodes.isNotEmpty()) {
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

    val energyFiltered = if (isOtherModeActive()) {
        base
    } else {
        base.filterProvidersForEnergy(wantFuel, wantElectric)
    }

    if (useVehicleFilter && fuelCard == FuelCard.Routex) {
        return energyFiltered + setOf(PoiProviderType.Routex, PoiProviderType.Overpass)
    }
    return energyFiltered
}

/** ISO country codes for [latitude]/[longitude] when it falls in known [ParkingRegion]s or within 10km of their borders. */
fun countryCodesAtMapPosition(latitude: Double, longitude: Double): List<String> {
    val regions = ParkingRegion.entries.filter { region ->
        region.subBoxes.any { box ->
            box.distanceToKm(latitude, longitude) <= 10.0
        }
    }
    return regions.map { it.countryCode }.distinct()
}

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

fun Poi.matchesAnyCategory(allowed: Set<PoiCategory>): Boolean {
    val primary = poiCategory ?: if (isElectric) PoiCategory.Irve else PoiCategory.Gas
    if (primary in allowed) return true
    return extraCategories.any { it in allowed }
}

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
        result = result.filter { it.matchesAnyCategory(allowedCategories) }

        // In "Other" mode, we've already filtered by the selected amenities.
        // We skip energy-specific filtering to allow display of stations that are also amenities
        // (the merger logic will later ensure they show brand info if available).
        if (settings.isOtherModeActive()) {
            return result
        }

        if (skipWhenOnlyOverpass && providers.isOnlyOverpass()) return result

        // Filter by energy type
        val mode = settings.effectiveEnergyFilterMode()
        val fuelFilters = settings.selectedMapEnergyTypes
        result = result.filter { poi ->
            if (poi.poiCategory == PoiCategory.BatterySwap) {
                // If Swap filter is explicitly selected, or if we are in My Vehicle mode with a Motorcycle
                val swapExplicitlySelected = fuelFilters.contains("swap")
                val isMotorcycle = settings.useVehicleFilter && settings.vehicleType == VehicleType.Motorcycle

                // Show swap stations if explicitly requested OR if in My Vehicle mode (any vehicle, but user said "show them all" unless motorcycle filter applies)
                // Actually, user said: "filter if "my vehicle" is selected and configured as 2 wheels."
                if (settings.useVehicleFilter) {
                    val brand = poi.brand?.lowercase() ?: ""
                    val isCarSwap = brand.contains("nio") || brand.contains("ample")
                    val is2WheelSwap = brand.contains("gogoro") || brand.contains("zeway")

                    if (settings.vehicleType == VehicleType.Motorcycle || settings.vehicleType == VehicleType.Bicycle) {
                        // 2 wheels: show if it's a known 2-wheel brand OR if it's NOT a known car brand
                        is2WheelSwap || !isCarSwap
                    } else {
                        // Cars/Trucks: only show if it's a known car brand.
                        // We avoid showing unknown swap stations to cars as they are often for 2-wheels.
                        isCarSwap
                    }
                } else {
                    // Manual mode: show if "swap" chip is selected
                    swapExplicitlySelected
                }
            } else {
                MapPoiFilter.matchesEnergyFilter(poi, mode, fuelFilters)
            }
        }

        // Filter by power range (IRVE)
        if (!settings.isSwapExclusive()) {
            val powerFilters = settings.effectiveIrvePowerLevels()
            if (powerFilters.isNotEmpty()) {
                result = result.filter { poi ->
                    val power = poi.powerKw
                    !poi.isElectric || power == null || MapPoiFilter.powerMatchesAnyLevel(power, powerFilters)
                }
            }
        }

        // Filter by operator (IRVE)
        if (!settings.isSwapExclusive()) {
            val operatorFilters = settings.effectiveIrveOperatorFilter()
            if (operatorFilters.isNotEmpty()) {
                val operatorIds = operatorFilters.map { it.lowercase() }.toSet()
                result = result.filter { poi ->
                    val op = poi.operator
                    !poi.isElectric || op == null || operatorIds.any { id -> op.lowercase().contains(id) }
                }
            }
        }

        // Filter by connector type (IRVE)
        if (!settings.isSwapExclusive()) {
            val connectorFilters = settings.selectedMapConnectorTypes
            if (connectorFilters.isNotEmpty()) {
                result = result.filter { poi ->
                    val types = poi.irveDetails?.connectorTypes
                    !poi.isElectric || types == null || types.any { it in connectorFilters }
                }
            }
        }

        // Filter by highway (autoroute)
        if (settings.filterOnlyHighwayStations) {
            result = result.filter { it.isOnHighway }
        }

        // Fuel card: Routex only
        if (settings.useVehicleFilter && settings.fuelCard == FuelCard.Routex) {
            result = result.filter { poi ->
                val isEnergy = poi.poiCategory == PoiCategory.Gas || poi.poiCategory == PoiCategory.Irve || poi.isElectric
                !isEnergy || (poi.source?.contains("Routex", ignoreCase = true) == true)
            }
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

fun filterPoisByViewport(
    pois: List<Poi>,
    lat: Double,
    lon: Double,
    zoom: Float,
    widthPx: Int,
    heightPx: Int
): List<Poi> {
    if (widthPx <= 0 || heightPx <= 0) return pois
    val viewport = calculateBoundsFromMapViewport(lat, lon, zoom, widthPx, heightPx)
    return pois.filter { viewport.contains(it.latitude, it.longitude) }
}
