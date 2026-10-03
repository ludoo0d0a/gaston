package fr.geoking.gaston.toll

import fr.geoking.gaston.VehicleType
import fr.geoking.gaston.api.toll.OpenTollDataModel
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Estimates French highway toll for a route using OpenTollData.
 * Uses a data source (e.g. file path resolved by platform) so shared code stays platform-agnostic.
 *
 * Closed networks: one matrix lookup entry→exit per consecutive run of booths on the same network
 * (not a sum of consecutive hops). Open booths: flat fee when on route.
 */
class TollCalculator(
    private val dataSource: () -> OpenTollDataModel?
) {

    /** Maximum distance (meters) from route polyline to consider a toll booth as "on route". */
    private val boothProximityThresholdMeters = 1500.0

    /**
     * Estimates toll for the given route points and vehicle type.
     * Returns null if no data, vehicle is Bicycle, or no booths match the route.
     */
    fun estimateToll(
        routePoints: List<Pair<Double, Double>>,
        vehicleType: VehicleType
    ): TollEstimate? {
        if (routePoints.size < 2) return null
        val priceClass = vehicleTypeToPriceClass(vehicleType) ?: return null
        val data = dataSource() ?: return null

        val boothsWithPosition = mutableListOf<Pair<String, Double>>()
        val desc = data.tollDescription
        val segmentLengths = segmentLengths(routePoints)
        val cumulativeLengths = cumulativeLengths(segmentLengths)

        for ((name, booth) in desc) {
            val lat = booth.lat.toDoubleOrNull() ?: continue
            val lon = booth.lon.toDoubleOrNull() ?: continue
            var minDist = Double.MAX_VALUE
            var positionAlongRoute = 0.0
            for (i in 0 until routePoints.size - 1) {
                val a = routePoints[i]
                val b = routePoints[i + 1]
                val (dist, t) = distanceAndT(lat, lon, a.first, a.second, b.first, b.second)
                if (dist < minDist) {
                    minDist = dist
                    positionAlongRoute = cumulativeLengths[i] + t * segmentLengths[i]
                }
            }
            if (minDist <= boothProximityThresholdMeters) {
                boothsWithPosition.add(name to positionAlongRoute)
            }
        }

        boothsWithPosition.sortBy { it.second }
        if (boothsWithPosition.isEmpty()) return null

        val boothOrder = boothsWithPosition.map { it.first }
        val tollToNetwork = mutableMapOf<String, String>()
        for (net in data.networks) {
            for (t in net.tolls) {
                tollToNetwork[t] = net.networkName
            }
        }

        var totalEur = 0.0
        val classKey = "class_$priceClass"
        var i = 0
        while (i < boothOrder.size) {
            val name = boothOrder[i]
            val descEntry = desc[name]
            if (descEntry == null) {
                i++
                continue
            }
            if (descEntry.type == "open") {
                val openPrice = data.openTollPrice[name]?.price?.get(classKey)?.toDoubleOrNull()
                if (openPrice != null) totalEur += openPrice
                i++
                continue
            }

            // Closed booth: collect consecutive close booths on the same network.
            val net = tollToNetwork[name]
            if (net == null) {
                i++
                continue
            }
            val runStart = i
            var runEnd = i
            while (runEnd + 1 < boothOrder.size) {
                val nextName = boothOrder[runEnd + 1]
                val nextDesc = desc[nextName] ?: break
                if (nextDesc.type == "open") break
                if (tollToNetwork[nextName] != net) break
                runEnd++
            }
            val entry = boothOrder[runStart]
            val exit = boothOrder[runEnd]
            if (entry != exit) {
                val conn = data.networks.find { it.networkName == net }?.connection
                val price = conn?.get(entry)?.get(exit)?.price?.get(classKey)?.toDoubleOrNull()
                if (price != null) totalEur += price
            }
            i = runEnd + 1
        }

        return if (totalEur > 0) TollEstimate(amountEur = totalEur) else null
    }

    private fun vehicleTypeToPriceClass(vehicleType: VehicleType): Int? = when (vehicleType) {
        VehicleType.Car -> 1
        VehicleType.Motorhome -> 2
        VehicleType.Truck -> 3
        VehicleType.Motorcycle -> 5
        VehicleType.Bicycle -> null
    }

    private fun segmentLengths(points: List<Pair<Double, Double>>): DoubleArray {
        val len = DoubleArray(points.size - 1)
        for (i in 0 until points.size - 1) {
            val (lat1, lon1) = points[i]
            val (lat2, lon2) = points[i + 1]
            len[i] = haversineMeters(lat1, lon1, lat2, lon2)
        }
        return len
    }

    private fun cumulativeLengths(segmentLengths: DoubleArray): DoubleArray {
        val cum = DoubleArray(segmentLengths.size + 1)
        for (i in segmentLengths.indices) {
            cum[i + 1] = cum[i] + segmentLengths[i]
        }
        return cum
    }

    /**
     * Distance from point (plat, plon) to segment A->B, and parameter t in [0,1] for closest point on segment.
     */
    private fun distanceAndT(
        plat: Double, plon: Double,
        latA: Double, lonA: Double,
        latB: Double, lonB: Double
    ): Pair<Double, Double> {
        val ax = (lonA - plon) * cos(plat * PI_180) * METERS_PER_DEGREE_LON
        val ay = (latA - plat) * METERS_PER_DEGREE_LAT
        val bx = (lonB - plon) * cos(plat * PI_180) * METERS_PER_DEGREE_LON
        val by = (latB - plat) * METERS_PER_DEGREE_LAT
        val px = 0.0
        val py = 0.0
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        val t = if (lenSq <= 1e-20) 0.0 else ((px - ax) * dx + (py - ay) * dy) / lenSq
        val tClamped = t.coerceIn(0.0, 1.0)
        val projX = ax + tClamped * dx
        val projY = ay + tClamped * dy
        val dist = sqrt(projX * projX + projY * projY)
        return dist to tClamped
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = (lat2 - lat1) * PI_180
        val dLon = (lon2 - lon1) * PI_180
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1 * PI_180) * cos(lat2 * PI_180) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt((1 - a).coerceAtLeast(0.0)))
        return r * c
    }

    companion object {
        private const val PI_180 = PI / 180.0
        private const val METERS_PER_DEGREE_LAT = 111_320.0
        private const val METERS_PER_DEGREE_LON = 85_000.0
    }
}
