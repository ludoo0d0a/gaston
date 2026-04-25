package fr.geoking.gaston.api.geocoding

import kotlinx.serialization.Serializable

@Serializable
data class GeocodedPlace(
    val label: String,
    val latitude: Double,
    val longitude: Double
)

interface GeocodingClient {
    suspend fun geocode(query: String, limit: Int = 1): List<GeocodedPlace>
}

