package fr.geoking.gaston.api.us

import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.api.routex.radiusKmFromMapViewport
import fr.geoking.gaston.poi.FuelPrice
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.shared.logging.log

/**
 * US fuel stations from OpenStreetMap enriched with state-level weekly retail averages
 * from the EIA Open Data API ([EiaPetroleumClient], route petroleum/pri/gnd).
 */
class UsaEiaProvider(
    private val eiaClient: EiaPetroleumClient,
    private val overpassClient: OverpassClient,
    private val apiKey: String,
    private val radiusKm: Int = 15,
    private val limit: Int = 100,
) : PoiProvider {

    private val priceCache = mutableMapOf<String, List<FuelPrice>>()

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Gas)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?,
    ): List<Poi> {
        val state = UsStateLookup.nearestState(latitude, longitude)
        if (state == null) return emptyList()

        val effectiveRadiusKm = viewport
            ?.let {
                radiusKmFromMapViewport(latitude, longitude, it.zoom, it.mapWidthPx, it.mapHeightPx)
                    .coerceIn(1, 50)
            }
            ?: radiusKm

        val fuelPrices = loadStatePrices(state)

        val elements = try {
            overpassClient.queryNodesAndWaysWithTagFilters(
                latitude = latitude,
                longitude = longitude,
                radiusKm = effectiveRadiusKm,
                tagFilters = listOf("amenity" to setOf("fuel")),
                limit = limit,
            )
        } catch (e: Exception) {
            log.w(e) { "[UsaEiaProvider] Overpass query failed" }
            emptyList()
        }

        val priceLabel = state.iso2
        val source = if (fuelPrices != null) {
            "OpenStreetMap + EIA ($priceLabel state avg, \$/gal)"
        } else {
            "OpenStreetMap"
        }

        return elements.map { el ->
            val name = el.name()?.takeIf { it.isNotBlank() } ?: "Gas station"
            Poi(
                id = "osm:us_fuel:${el.id}",
                name = name,
                address = el.address().orEmpty(),
                latitude = el.lat,
                longitude = el.lon,
                brand = el.brand()?.takeIf { it.isNotBlank() },
                poiCategory = PoiCategory.Gas,
                fuelPrices = fuelPrices,
                source = source,
            )
        }
    }

    override fun clearCache() {
        priceCache.clear()
    }

    private suspend fun loadStatePrices(state: UsStateLookup.State): List<FuelPrice>? {
        if (apiKey.isBlank()) return null
        priceCache[state.eiaDuoArea]?.let { return it }
        return try {
            val prices = eiaClient.getStateRetailPrices(state.eiaDuoArea, apiKey).takeIf { it.isNotEmpty() }
            if (prices != null) priceCache[state.eiaDuoArea] = prices
            prices
        } catch (e: Exception) {
            log.w(e) { "[UsaEiaProvider] EIA fetch failed for ${state.iso2}" }
            null
        }
    }
}
