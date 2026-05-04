package fr.geoking.gaston.api.geocoding

import kotlinx.serialization.Serializable

@Serializable
data class GeocodedPlace(
    val label: String,
    val latitude: Double,
    val longitude: Double
)

interface GeocodingClient {
    /**
     * Forward geocoding / place search.
     *
     * @param biasLatitude when set with [biasLongitude], providers that support it rank or constrain
     *        results near this point (typically the user’s current location).
     */
    suspend fun geocode(
        query: String,
        limit: Int = 1,
        biasLatitude: Double? = null,
        biasLongitude: Double? = null
    ): List<GeocodedPlace>
}

