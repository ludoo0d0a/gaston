package fr.geoking.gaston.api.openchargemap

import fr.geoking.gaston.api.routex.radiusKmFromMapViewport
import fr.geoking.gaston.poi.IrveDetails
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider

/**
 * [PoiProvider] that fetches EV charging stations from Open Charge Map (open data, CC BY 4.0).
 * Requires [OpenChargeMapClient]; optional API key may be needed for production use.
 */
class OpenChargeMapProvider(
    private val client: OpenChargeMapClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 50
) : PoiProvider {

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Irve)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val effectiveRadiusKm = viewport
            ?.let {
                radiusKmFromMapViewport(latitude, longitude, it.zoom, it.mapWidthPx, it.mapHeightPx)
                    .coerceIn(1, 50)
            }
            ?: radiusKm

        val stations = client.getStations(
            latitude = latitude,
            longitude = longitude,
            distanceKm = effectiveRadiusKm,
            maxResults = limit
        )
        return stations.map { s ->
            Poi(
                id = s.id,
                name = s.name,
                address = s.address,
                latitude = s.latitude,
                longitude = s.longitude,
                brand = null,
                isElectric = true,
                powerKw = s.powerKw,
                operator = s.operator,
                isOnHighway = false,
                chargePointCount = null,
                fuelPrices = null,
                isClosed = s.isClosed,
                irveDetails = IrveDetails(connectorTypes = s.connectorTypes),
                source = "OpenChargeMap"
            )
        }
    }
}
