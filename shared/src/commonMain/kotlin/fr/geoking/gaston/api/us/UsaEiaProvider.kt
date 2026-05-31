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
 * US fuel stations from OpenStreetMap enriched with EIA weekly retail averages
 * ([EiaPetroleumClient], route petroleum/pri/gnd) at metro or state granularity.
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
        if (!UsStateLookup.isInUnitedStates(latitude, longitude)) return emptyList()

        val effectiveRadiusKm = viewport
            ?.let {
                radiusKmFromMapViewport(latitude, longitude, it.zoom, it.mapWidthPx, it.mapHeightPx)
                    .coerceIn(1, 50)
            }
            ?: radiusKm

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

        return elements.map { el ->
            val area = UsEiaAreaLookup.resolve(el.lat, el.lon)
            val fuelPrices = area?.let { loadAreaPrices(it) }
            val source = area?.takeIf { fuelPrices != null }?.let { resolved ->
                "OpenStreetMap + EIA (${resolved.label} avg, \$/gal)"
            } ?: "OpenStreetMap"
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

    override suspend fun clearCache() {
        priceCache.clear()
    }

    private suspend fun loadAreaPrices(area: UsEiaAreaLookup.Area): List<FuelPrice>? {
        if (apiKey.isBlank()) {
            log.w { "[UsaEiaProvider] EIA_KEY is blank, EIA price data will not be available" }
            return null
        }
        priceCache[area.duoArea]?.let { return it }
        return try {
            val prices = eiaClient.getRetailPrices(area.duoArea, apiKey).takeIf { it.isNotEmpty() }
            if (prices != null) {
                priceCache[area.duoArea] = prices
                log.d { "[UsaEiaProvider] cached duoarea=${area.duoArea} label=${area.label} products=${prices.size}" }
            } else {
                log.w { "[UsaEiaProvider] EIA returned no rows for duoarea=${area.duoArea} label=${area.label}" }
            }
            prices
        } catch (e: Exception) {
            log.w(e) { "[UsaEiaProvider] EIA fetch failed for duoarea=${area.duoArea} label=${area.label}" }
            null
        }
    }
}
