package fr.geoking.gaston.auto.testutil

import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiProvider

class FakePoiProvider(private val pois: List<Poi> = emptyList()) : PoiProvider {
    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?,
    ): List<Poi> = pois
}
