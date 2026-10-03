package fr.geoking.gaston.toll

import fr.geoking.gaston.VehicleType
import fr.geoking.gaston.api.toll.GastonTollApiClient

/**
 * Prefers backend [GastonTollApiClient] when enabled and configured; falls back to local [TollCalculator].
 */
class TollEstimateService(
    private val localCalculator: TollCalculator,
    private val backendClient: GastonTollApiClient?,
    private val useBackend: () -> Boolean,
) {
    suspend fun estimateToll(
        routePoints: List<Pair<Double, Double>>,
        vehicleType: VehicleType,
    ): TollEstimate? {
        val priceClass = vehicleTypeToPriceClass(vehicleType) ?: return null
        if (useBackend() && backendClient?.isConfigured == true) {
            val remote = backendClient.estimateToll(routePoints, priceClass)
            if (remote != null) return remote
        }
        return localCalculator.estimateToll(routePoints, vehicleType)
    }

    private fun vehicleTypeToPriceClass(vehicleType: VehicleType): Int? = when (vehicleType) {
        VehicleType.Car -> 1
        VehicleType.Motorhome -> 2
        VehicleType.Truck -> 3
        VehicleType.Motorcycle -> 5
        VehicleType.Bicycle -> null
    }
}
