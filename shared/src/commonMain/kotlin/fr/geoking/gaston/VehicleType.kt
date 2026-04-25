package fr.geoking.gaston

/**
 * Vehicle type for POI categories and optional routing profile.
 * Drives which POIs are relevant (e.g. truck stops + fuel for truck) and, when backend supports it, routing profile.
 */
enum class VehicleType {
    Car,
    Truck,
    Motorcycle,
    Motorhome,
    Bicycle
}
