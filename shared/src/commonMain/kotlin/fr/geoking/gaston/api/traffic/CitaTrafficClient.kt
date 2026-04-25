package fr.geoking.gaston.api.traffic

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

/**
 * HTTP client that fetches CITA DATEX II traffic data for Luxembourg motorways.
 * No API key required. Data © CITA / Administration des Ponts et Chaussées.
 *
 * See https://data.public.lu/en/datasets/cita-donnees-trafic-en-datex-ii/
 */
class CitaTrafficClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://www.cita.lu/info_trafic/datex"
) {
    /** CITA DATEX II feed URLs per road (A1, A3, A4, A6, A7, A13, B40). */
    private val roadUrls = listOf(
        "trafficstatus_a1",
        "trafficstatus_a3",
        "trafficstatus_a4",
        "trafficstatus_a6",
        "trafficstatus_a7",
        "trafficstatus_a13",
        "trafficstatus_b40"
    ).associateWith { "$baseUrl/$it" }

    /**
     * Fetches DATEX II XML for all roads. Returns map of road id (e.g. "a6") to raw XML.
     * Failed roads are omitted; partial result is still returned.
     */
    suspend fun fetchAllRoads(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for ((road, url) in roadUrls) {
            try {
                val response = client.get(url)
                if (response.status == HttpStatusCode.OK) {
                    result[road.removePrefix("trafficstatus_")] = response.bodyAsText()
                }
            } catch (_: Exception) {
                // Skip failed road, continue with others
            }
        }
        return result
    }

    /**
     * Fetches DATEX II XML for a single road (e.g. "a6", "A6"). Returns null on failure.
     */
    suspend fun fetchRoad(roadId: String): String? {
        val key = "trafficstatus_${roadId.lowercase()}"
        val url = roadUrls[key] ?: return null
        return try {
            val response = client.get(url)
            if (response.status == HttpStatusCode.OK) response.bodyAsText() else null
        } catch (_: Exception) {
            null
        }
    }
}
