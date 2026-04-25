package fr.geoking.gaston.api.parking

import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.api.overpass.OverpassElement
import fr.geoking.gaston.parking.ParkingPoi
import fr.geoking.gaston.parking.ParkingProvider
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Parking provider using [OverpassClient] (OpenStreetMap). Returns amenity=parking POIs
 * with name, address, opening_hours when tagged. No live capacity or prices.
 * Used as fallback only when no country-specific provider (LiveParking, ParkAPI) serves the user's region.
 */
class OsmParkingProvider(
    private val overpass: OverpassClient,
    /** Search radius in km for Overpass query. */
    private val radiusKm: Int = 5,
    /** Max elements from Overpass. */
    private val limit: Int = 80
) : ParkingProvider {

    override val id: String = "osm"

    override fun covers(lat: Double, lon: Double): Boolean = true

    override fun isFallbackOnly(): Boolean = true

    override suspend fun getParkingNearby(lat: Double, lon: Double, radiusMeters: Int): List<ParkingPoi> {
        val radiusKmFilter = radiusMeters / 1000.0
        val elements = runCatching {
            overpass.queryNodesAndWaysWithTagFilters(
                latitude = lat,
                longitude = lon,
                radiusKm = this.radiusKm,
                tagFilters = listOf("amenity" to setOf("parking")),
                limit = this.limit
            )
        }.getOrElse { emptyList() }
        return elements
            .filter { haversineKm(lat, lon, it.lat, it.lon) <= radiusKmFilter }
            .map { toParkingPoi(it) }
    }

    private fun toParkingPoi(el: OverpassElement): ParkingPoi =
        ParkingPoi(
            id = "osm_${el.id}",
            name = el.name() ?: "Parking",
            latitude = el.lat,
            longitude = el.lon,
            capacity = null,
            available = null,
            openingHours = el.openingHours(),
            priceInfo = el.tags["fee"]?.let { "fee: $it" }
                ?: el.tags["charge"]?.let { "charge: $it" },
            providerId = id,
            address = el.address(),
            state = null,
            distanceKm = null
        )

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = PI * (lat2 - lat1) / 180.0
        val dLon = PI * (lon2 - lon1) / 180.0
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(PI * lat1 / 180.0) * cos(PI * lat2 / 180.0) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }
}
