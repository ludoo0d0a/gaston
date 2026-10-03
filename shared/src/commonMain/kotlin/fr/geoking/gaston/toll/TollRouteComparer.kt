package fr.geoking.gaston.toll

import fr.geoking.gaston.VehicleType
import fr.geoking.gaston.api.routing.RouteResult

/**
 * One route variant with toll estimate for comparison (time vs €).
 */
data class TollRouteVariant(
    val label: String,
    val route: RouteResult,
    val toll: TollEstimate?,
)

/**
 * Compares several route geometries by estimated toll (and duration from [RouteResult]).
 */
class TollRouteComparer(
    private val estimateService: TollEstimateService,
) {
    suspend fun compare(
        routes: List<RouteResult>,
        vehicleType: VehicleType,
    ): List<TollRouteVariant> {
        if (routes.isEmpty()) return emptyList()
        return routes.mapIndexed { index, route ->
            val toll = estimateService.estimateToll(route.points, vehicleType)
            TollRouteVariant(label = (index + 1).toString(), route = route, toll = toll)
        }.sortedWith(
            // Prefer lower known toll; unknown (null) sorts after priced routes.
            compareBy<TollRouteVariant> { it.toll?.amountEur ?: Double.POSITIVE_INFINITY }
                .thenBy { it.route.durationSeconds }
        )
    }
}
