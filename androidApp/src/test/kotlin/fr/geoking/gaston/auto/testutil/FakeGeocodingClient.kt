package fr.geoking.gaston.auto.testutil

import fr.geoking.gaston.api.geocoding.GeocodedPlace
import fr.geoking.gaston.api.geocoding.GeocodingClient

class FakeGeocodingClient : GeocodingClient {
    override suspend fun geocode(
        query: String,
        limit: Int,
        biasLatitude: Double?,
        biasLongitude: Double?,
    ): List<GeocodedPlace> = emptyList()
}
