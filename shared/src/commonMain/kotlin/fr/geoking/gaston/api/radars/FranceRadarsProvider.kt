package fr.geoking.gaston.api.radars

import fr.geoking.gaston.poi.AbstractPoiProvider
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProviderRules
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.poi.radiusKmFromMapViewport

class FranceRadarsProvider(
    private val client: FranceRadarsClient,
    private val defaultRadiusKm: Double = 25.0
) : AbstractPoiProvider() {

    override val usageRules: PoiProviderRules = PoiProviderRules(
        countries = setOf("FR")
    )

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Radar)

    override suspend fun search(request: PoiSearchRequest): List<Poi> {
        if (!shouldQuery(request.latitude, request.longitude, request.viewport)) {
            return emptyList()
        }
        // Level A (FR): do not expose exact control points as map POIs for alerts.
        // Zones are built via FranceRadarsClient.getRecordsNear → DangerZoneFactory.
        return emptyList()
    }

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        return search(PoiSearchRequest(latitude, longitude, viewport, categories = setOf(PoiCategory.Radar)))
    }

    override suspend fun clearCache() {
        client.clearCache()
    }
}
