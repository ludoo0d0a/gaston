package fr.geoking.gaston.api.routing

/**
 * Decode a Google-encoded polyline string into a list of (latitude, longitude) pairs.
 * OSRM returns geometry in this format with precision 5.
 */
object PolylineUtils {
    fun decode(encoded: String): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lon = 0
        val len = encoded.length
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20 && index < len)
            val dlat = if (result and 1 != 0) -(result shr 1) - 1 else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20 && index < len)
            val dlon = if (result and 1 != 0) -(result shr 1) - 1 else result shr 1
            lon += dlon

            points.add(lat / 1e5 to lon / 1e5)
        }
        return points
    }
}
