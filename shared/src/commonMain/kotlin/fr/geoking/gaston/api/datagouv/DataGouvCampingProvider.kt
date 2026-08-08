package fr.geoking.gaston.api.datagouv

import fr.geoking.gaston.poi.radiusKmFromMapViewport
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.shared.location.haversineKm

/**
 * [PoiProvider] that fetches aires de camping-car from data.gouv.fr–linked Opendatasoft APIs
 * (e.g. Hérault Data). Complements Overpass OSM data with official regional aires.
 * No API key. Licence: Licence Ouverte 2.0 (Etalab).
 */
class DataGouvCampingProvider(
    private val client: DataGouvCampingClient,
    private val radiusKm: Int = 15,
    private val limit: Int = 50
) : PoiProvider {

    companion object {
        private const val HERAULT_CENTER_LAT = 43.5795
        private const val HERAULT_CENTER_LON = 3.3684
        private const val HERAULT_RADIUS_KM = 80.0
    }

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.CaravanSite)

    override suspend fun search(request: PoiSearchRequest): List<Poi> {
        val wanted = request.categories.ifEmpty { supportedCategories() }
        if (PoiCategory.CaravanSite !in wanted) return emptyList()

        val effectiveRadiusKm = request.viewport
            ?.let {
                radiusKmFromMapViewport(request.latitude, request.longitude, it.zoom, it.mapWidthPx, it.mapHeightPx)
                    .coerceIn(1, 50)
            }
            ?: radiusKm

        // Check if the search area overlaps with the Hérault department circle
        val distToHerault = haversineKm(request.latitude, request.longitude, HERAULT_CENTER_LAT, HERAULT_CENTER_LON)
        if (distToHerault > HERAULT_RADIUS_KM + effectiveRadiusKm) {
            return emptyList()
        }

        val aires = client.getAires(
            latitude = request.latitude,
            longitude = request.longitude,
            radiusKm = effectiveRadiusKm,
            limit = limit
        )
        return aires.map { r ->
            Poi(
                id = "dgouv:${r.id}",
                name = r.name,
                address = r.address,
                latitude = r.latitude,
                longitude = r.longitude,
                poiCategory = PoiCategory.CaravanSite,
                siteName = r.typeAire,
                source = "DataGouv"
            )
        }
    }

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> = emptyList()
}
