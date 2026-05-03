package fr.geoking.gaston.api.routex

import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.shared.logging.log
import io.ktor.client.HttpClient

/**
 * [PoiProvider] implementation that fetches gas stations from the Routex (Wigeogis) SiteFinder API.
 * When [viewport] is provided, the API radius is derived from the visible map (zoom + size);
 * otherwise [radiusKm] is used.
 */
class RoutexProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 5
) : PoiProvider {

    private val routexClient = RoutexClient(client)

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val radius = if (viewport != null) {
            radiusKmFromMapViewport(
                latitude,
                longitude,
                viewport.zoom,
                viewport.mapWidthPx,
                viewport.mapHeightPx
            )
        } else {
            radiusKm
        }
        val sites = routexClient.getResults(latitude, longitude, radius)
        log.d { "[RoutexProvider] getGasStations lat=$latitude lon=$longitude radius=$radius -> ${sites.size} sites" }
        return sites.map { site ->
            Poi(
                id = site.id,
                name = site.name,
                address = site.address,
                latitude = site.latitude,
                longitude = site.longitude,
                brand = site.brand,
                isOnHighway = site.details?.motorwayIndicator ?: false,
                siteName = site.siteName,
                postcode = site.postcode,
                addressLocal = site.addressLocal,
                countryLocal = site.countryLocal,
                townLocal = site.townLocal,
                routexDetails = site.details,
                source = "Routex"
            )
        }
    }
}
