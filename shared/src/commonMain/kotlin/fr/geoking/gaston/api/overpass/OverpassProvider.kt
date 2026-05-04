package fr.geoking.gaston.api.overpass

import fr.geoking.gaston.api.routex.radiusKmFromMapViewport
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.PoiSearchRequest
import fr.geoking.gaston.poi.RestaurantDetails

/**
 * [PoiProvider] that fetches amenities (toilets, water, camping, caravan, picnic, etc.) from OpenStreetMap
 * via the [Overpass API](https://wiki.openstreetmap.org/wiki/Overpass_API).
 * No API key required. Data © OpenStreetMap contributors, ODbL.
 */
class OverpassProvider(
    private val client: OverpassClient,
    private val radiusKm: Int = 5,
    private val limit: Int = 200
) : PoiProvider {

    override fun supportedCategories(): Set<PoiCategory> = setOf(
        PoiCategory.Toilet,
        PoiCategory.DrinkingWater,
        PoiCategory.Camping,
        PoiCategory.CaravanSite,
        PoiCategory.PicnicSite,
        PoiCategory.TruckStop,
        PoiCategory.RestArea,
        PoiCategory.Restaurant,
        PoiCategory.FastFood,
        PoiCategory.Radar,
        PoiCategory.Parking,
        PoiCategory.Gas,
        PoiCategory.Irve
    )

    override suspend fun search(request: PoiSearchRequest): List<Poi> {
        val effectiveRadiusKm = request.viewport
            ?.let { radiusKmFromMapViewport(request.latitude, request.longitude, it.zoom, it.mapWidthPx, it.mapHeightPx).coerceIn(1, 50) }
            ?: radiusKm

        val cat = request.categories.ifEmpty { supportedCategories() }
        val wanted = cat.filter { it in supportedCategories() }.toSet()
        if (wanted.isEmpty()) return emptyList()
        val amenityValues = wanted.mapNotNull { categoryToOsmAmenity(it) }.toSet()
        val tourismValues = wanted.mapNotNull { categoryToOsmTourism(it) }.toSet()
        val highwayValues = wanted.mapNotNull { categoryToOsmHighway(it) }.toSet()
        val tagFilters = buildList {
            if (amenityValues.isNotEmpty()) add("amenity" to amenityValues)
            if (tourismValues.isNotEmpty()) add("tourism" to tourismValues)
            if (highwayValues.isNotEmpty()) add("highway" to highwayValues)
        }
        if (tagFilters.isEmpty()) return emptyList()
        val needsWays = PoiCategory.TruckStop in wanted || PoiCategory.RestArea in wanted ||
            PoiCategory.Restaurant in wanted || PoiCategory.FastFood in wanted ||
            PoiCategory.Parking in wanted || PoiCategory.Gas in wanted || PoiCategory.Irve in wanted
        val elements = if (needsWays) {
            client.queryNodesAndWaysWithTagFilters(
                latitude = request.latitude,
                longitude = request.longitude,
                radiusKm = effectiveRadiusKm,
                tagFilters = tagFilters,
                limit = limit
            )
        } else {
            client.queryNodesWithTagFilters(
                latitude = request.latitude,
                longitude = request.longitude,
                radiusKm = effectiveRadiusKm,
                tagFilters = tagFilters,
                limit = limit
            )
        }
        return elements.mapNotNull { el ->
            val category = PoiCategory.fromOsmTags(el.tags) ?: return@mapNotNull null
            if (category !in wanted) return@mapNotNull null
            val restaurantDetails = when (category) {
                PoiCategory.Restaurant, PoiCategory.FastFood -> RestaurantDetails(
                    openingHours = el.openingHours()?.takeIf { it.isNotBlank() },
                    cuisine = el.cuisine()?.takeIf { it.isNotBlank() },
                    brand = el.brand()?.takeIf { it.isNotBlank() },
                    isFastFood = category == PoiCategory.FastFood
                )
                else -> null
            }
            val name = if (category == PoiCategory.Radar) {
                el.tags["maxspeed"]?.let { "Radar $it km/h" } ?: el.name()
            } else {
                el.name()
            }
            Poi(
                id = "osm:${el.id}",
                name = name?.takeIf { it.isNotBlank() } ?: categoryDisplayName(category),
                address = el.address() ?: "",
                latitude = el.lat,
                longitude = el.lon,
                poiCategory = category,
                brand = el.brand()?.takeIf { it.isNotBlank() },
                restaurantDetails = restaurantDetails,
                source = "OpenStreetMap"
            )
        }
    }

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> = emptyList()

    private fun categoryToOsmAmenity(c: PoiCategory): String? = when (c) {
        PoiCategory.Toilet -> "toilets"
        PoiCategory.DrinkingWater -> "drinking_water"
        PoiCategory.TruckStop -> "truck_stop"
        PoiCategory.Restaurant -> "restaurant"
        PoiCategory.FastFood -> "fast_food"
        PoiCategory.Parking -> "parking"
        PoiCategory.Gas -> "fuel"
        PoiCategory.Irve -> "charging_station"
        else -> null
    }

    private fun categoryToOsmTourism(c: PoiCategory): String? = when (c) {
        PoiCategory.Camping -> "camp_site"
        PoiCategory.CaravanSite -> "caravan_site"
        PoiCategory.PicnicSite -> "picnic_site"
        else -> null
    }

    private fun categoryToOsmHighway(c: PoiCategory): String? = when (c) {
        PoiCategory.RestArea -> "rest_area"
        PoiCategory.Radar -> "speed_camera"
        else -> null
    }

    private fun categoryDisplayName(c: PoiCategory): String = when (c) {
        PoiCategory.Toilet -> "Toilets"
        PoiCategory.DrinkingWater -> "Drinking water"
        PoiCategory.Camping -> "Camping"
        PoiCategory.CaravanSite -> "Aire camping-car"
        PoiCategory.PicnicSite -> "Picnic area"
        PoiCategory.TruckStop -> "Truck stop"
        PoiCategory.RestArea -> "Rest area"
        PoiCategory.Restaurant -> "Restaurant"
        PoiCategory.FastFood -> "Fast food"
        PoiCategory.Radar -> "Radar"
        PoiCategory.Parking -> "Parking"
        PoiCategory.Gas -> "Gas station"
        PoiCategory.Irve -> "Charging station"
        else -> c.name
    }
}
