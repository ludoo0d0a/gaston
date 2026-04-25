package fr.geoking.gaston.api.belgium

import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.api.routex.radiusKmFromMapViewport
import fr.geoking.gaston.parking.ParkingRegion
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.shared.logging.log

/**
 * [PoiProvider] that fetches fuel stations from OpenStreetMap and enriches them with
 * official national maximum prices from the Belgian government via [BelgiumPetrolPricesClient].
 */
class BelgiumOfficialProvider(
    private val belgiumClient: BelgiumPetrolPricesClient,
    private val overpassClient: OverpassClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 100
) : PoiProvider {

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val region = ParkingRegion.containing(latitude, longitude)
        val isBelgium = region == ParkingRegion.Belgium || region?.countryCode == "BE"

        val effectiveRadiusKm = viewport
            ?.let {
                radiusKmFromMapViewport(latitude, longitude, it.zoom, it.mapWidthPx, it.mapHeightPx).coerceIn(1, 50)
            }
            ?: radiusKm

        val fuelPrices = if (isBelgium) {
            try {
                belgiumClient.getFuelPrices().takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                log.w(e) { "[BelgiumOfficialProvider] Failed to fetch prices" }
                null
            }
        } else {
            null
        }

        val elements = try {
            overpassClient.queryNodesAndWaysWithTagFilters(
                latitude = latitude,
                longitude = longitude,
                radiusKm = effectiveRadiusKm,
                tagFilters = listOf("amenity" to setOf("fuel")),
                limit = limit
            )
        } catch (_: Exception) {
            emptyList()
        }

        return elements.map { el ->
            val name = el.name()?.takeIf { it.isNotBlank() } ?: "Gas station"
            val address = el.address().orEmpty()
            Poi(
                id = "osm:be_fuel:${el.id}",
                name = name,
                address = address,
                latitude = el.lat,
                longitude = el.lon,
                brand = el.brand()?.takeIf { it.isNotBlank() },
                poiCategory = PoiCategory.Gas,
                fuelPrices = fuelPrices,
                source = if (fuelPrices != null) "OpenStreetMap + Belgium (official price)" else "OpenStreetMap"
            )
        }
    }
}
