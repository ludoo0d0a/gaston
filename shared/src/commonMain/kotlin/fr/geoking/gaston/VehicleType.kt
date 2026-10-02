package fr.geoking.gaston

import kotlinx.serialization.Serializable

/**
 * Vehicle type for POI categories and optional routing profile.
 * Drives which POIs are relevant (e.g. truck stops + fuel for truck) and, when backend supports it, routing profile.
 */
@Serializable
enum class VehicleType {
    Car,
    Truck,
    Motorcycle,
    Motorhome,
    Bicycle
}
