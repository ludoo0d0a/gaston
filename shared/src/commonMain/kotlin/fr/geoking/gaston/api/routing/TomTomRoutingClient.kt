package fr.geoking.gaston.api.routing

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

/**
 * TomTom Routing API - Calculate Route.
 *
 * This client returns raw JSON (string). Higher-level code can parse as needed.
 * Docs: https://docs.tomtom.com/routing-api/documentation/routing/calculate-route
 */
class TomTomRoutingClient(
    private val client: HttpClient
) {
    /**
     * @param routePlanningLocations "{lat},{lon}:{lat},{lon}[:...]" (origin:destination:via...)
     */
    suspend fun calculateRouteJson(
        apiKey: String,
        routePlanningLocations: String,
        travelMode: String = "car"
    ): String? {
        if (apiKey.isBlank()) return null
        return try {
            val response = client.get("$BASE_URL/$VERSION/calculateRoute/$routePlanningLocations/json") {
                parameter("key", apiKey)
                parameter("travelMode", travelMode)
                // Expose toll sections when present (useful even when costs aren't available).
                parameter("sectionType", "toll")
                parameter("sectionType", "tollVignette")
                // When returning TOLL sections, include payment types (if available).
                parameter("includeTollPaymentTypes", "all")
            }
            if (response.status == HttpStatusCode.OK) response.bodyAsText() else null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val BASE_URL = "https://api.tomtom.com/routing"
        private const val VERSION = "1"
    }
}

