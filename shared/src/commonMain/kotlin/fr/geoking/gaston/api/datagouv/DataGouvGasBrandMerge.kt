package fr.geoking.gaston.api.datagouv

import fr.geoking.gaston.api.gas.GasApiStation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Merges station brands from [GasApiStation] into [DataGouvStation] when the economy.gouv
 * quotidien dataset omits enseigne fields (nearest match within [maxDistanceM]).
 */
object DataGouvGasBrandMerge {

    private const val EARTH_RADIUS_M = 6371000.0

    fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rLat1 = kotlin.math.PI / 180.0 * lat1
        val rLat2 = kotlin.math.PI / 180.0 * lat2
        val dLat = kotlin.math.PI / 180.0 * (lat2 - lat1)
        val dLon = kotlin.math.PI / 180.0 * (lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    fun mergeGasApiBrands(
        stations: List<DataGouvStation>,
        gasStations: List<GasApiStation>,
        maxDistanceM: Double = 120.0
    ): List<DataGouvStation> {
        if (gasStations.isEmpty()) return stations
        return stations.map { s ->
            if (!s.brand.isNullOrBlank()) return@map s
            var best: GasApiStation? = null
            var bestD = Double.MAX_VALUE
            for (g in gasStations) {
                val d = haversineM(s.latitude, s.longitude, g.latitude, g.longitude)
                if (d < bestD) {
                    bestD = d
                    best = g
                }
            }
            val name = best?.brand?.trim()?.takeIf { it.isNotEmpty() }
            if (name != null && bestD <= maxDistanceM) s.copy(brand = name) else s
        }
    }
}
