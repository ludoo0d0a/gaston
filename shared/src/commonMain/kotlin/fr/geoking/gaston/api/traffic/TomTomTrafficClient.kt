package fr.geoking.gaston.api.traffic

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.math.abs
import kotlin.math.cos

/**
 * TomTom Traffic Incidents API v5 ([incidentDetails](https://developer.tomtom.com/traffic-api/documentation/traffic-incidents/incident-details)).
 * Requires a TomTom developer API key. Bbox area is clamped (API limit ~10,000 km²).
 */
class TomTomTrafficClient(
    private val client: HttpClient
) {
    suspend fun fetchIncidents(
        apiKey: String,
        latMin: Double,
        lonMin: Double,
        latMax: Double,
        lonMax: Double
    ): String? {
        if (apiKey.isBlank()) return null
        val box = clampBbox(latMin, lonMin, latMax, lonMax)
        val bboxQuery = "${box.lonMin},${box.latMin},${box.lonMax},${box.latMax}"
        return try {
            val response = client.get(INCIDENT_DETAILS_URL) {
                parameter("key", apiKey)
                parameter("bbox", bboxQuery)
                parameter(
                    "fields",
                    "{incidents{type,geometry{type,coordinates},properties{id,iconCategory,magnitudeOfDelay,events{description}}}}"
                )
                parameter("timeValidityFilter", "present")
            }
            if (response.status == HttpStatusCode.OK) response.bodyAsText() else null
        } catch (_: Exception) {
            null
        }
    }

    private data class LatLonBox(
        val latMin: Double,
        val lonMin: Double,
        val latMax: Double,
        val lonMax: Double
    )

    private fun clampBbox(latMin: Double, lonMin: Double, latMax: Double, lonMax: Double): LatLonBox {
        var la0 = latMin.coerceAtMost(latMax)
        var la1 = latMax.coerceAtLeast(latMin)
        var lo0 = lonMin.coerceAtMost(lonMax)
        var lo1 = lonMax.coerceAtLeast(lonMin)
        if (abs(la1 - la0) < 1e-5) {
            la0 -= 0.01
            la1 += 0.01
        }
        if (abs(lo1 - lo0) < 1e-5) {
            lo0 -= 0.01
            lo1 += 0.01
        }
        var area = approxAreaKm2(la0, la1, lo0, lo1)
        var guard = 0
        while (area > MAX_AREA_KM2 && guard < 24) {
            val cLat = (la0 + la1) / 2
            val cLon = (lo0 + lo1) / 2
            val hLat = (la1 - la0) * 0.45
            val hLon = (lo1 - lo0) * 0.45
            la0 = cLat - hLat
            la1 = cLat + hLat
            lo0 = cLon - hLon
            lo1 = cLon + hLon
            area = approxAreaKm2(la0, la1, lo0, lo1)
            guard++
        }
        return LatLonBox(la0, lo0, la1, lo1)
    }

    private fun approxAreaKm2(latMin: Double, latMax: Double, lonMin: Double, lonMax: Double): Double {
        val midLatRad = (latMin + latMax) / 2.0 * (kotlin.math.PI / 180.0)
        val hKm = abs(latMax - latMin) * 111.0
        val wKm = abs(lonMax - lonMin) * 111.0 * cos(midLatRad).coerceAtLeast(0.15)
        return hKm * wKm
    }

    companion object {
        private const val INCIDENT_DETAILS_URL = "https://api.tomtom.com/traffic/services/5/incidentDetails"
        private const val MAX_AREA_KM2 = 9000.0
    }
}
