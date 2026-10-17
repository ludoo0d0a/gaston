package fr.geoking.gaston.entitlements

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DefaultEntitlements(
    initialPlan: Plan = Plan.FREE
) : Entitlements {

    private val _plan = MutableStateFlow(initialPlan)
    override val plan: StateFlow<Plan> = _plan.asStateFlow()

    fun setPlan(newPlan: Plan) {
        _plan.value = newPlan
    }

    override fun can(feature: Feature): Boolean = when (plan.value) {
        Plan.PREMIUM -> true
        Plan.FREE -> when (feature) {
            Feature.FavoritesUnlimited -> false
            Feature.RouteSearchUnlimited -> false
            Feature.PriceAlerts -> false
            Feature.AutoFullMap -> false
            Feature.AutoStationList -> false
            Feature.FullForecast -> false
            Feature.MultiVehicle -> false
            Feature.EvRealtimeAvailability -> false
            Feature.MaintenanceExport -> false
            Feature.ParkingAlertsAndHistory -> false
            Feature.FuelCards -> false
            Feature.NoAds -> false
            Feature.FullRadarAlerts -> false
        }
    }

    override fun limit(feature: Feature): Int = when (plan.value) {
        Plan.PREMIUM -> Int.MAX_VALUE
        Plan.FREE -> when (feature) {
            Feature.FavoritesUnlimited -> 3
            Feature.RouteSearchUnlimited -> 1
            Feature.AutoStationList -> 5
            Feature.MultiVehicle -> 1
            Feature.FullForecast -> 7
            else -> if (can(feature)) Int.MAX_VALUE else 0
        }
    }
}
