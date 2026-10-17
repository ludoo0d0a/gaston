package fr.geoking.gaston.entitlements

import kotlinx.coroutines.flow.StateFlow

interface Entitlements {
    val plan: StateFlow<Plan>
    fun can(feature: Feature): Boolean
    fun limit(feature: Feature): Int
}
