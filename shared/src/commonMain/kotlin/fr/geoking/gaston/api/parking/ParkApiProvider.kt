package fr.geoking.gaston.api.parking

import fr.geoking.gaston.parking.ParkingPoi
import fr.geoking.gaston.parking.ParkingProvider
import fr.geoking.gaston.parking.ParkingRegion
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * City coverage: slug used in API + bbox so we only call when (lat,lon) is in that city area.
 */
data class ParkApiCityConfig(
    val citySlug: String,
    val latMin: Double,
    val latMax: Double,
    val lonMin: Double,
    val lonMax: Double
)

/**
 * Parking provider using [ParkApiClient] (parkendd.de). Covers configured cities;
 * when (lat, lon) falls in a city bbox, fetches that city's lots and filters by distance.
 * Serves regions where we have city configs (Germany, Switzerland, Denmark).
 */
class ParkApiProvider(
    private val api: ParkApiClient,
    private val cityConfigs: List<ParkApiCityConfig>,
    private val servedRegions: Set<ParkingRegion> = setOf(ParkingRegion.Germany, ParkingRegion.Switzerland, ParkingRegion.Denmark)
) : ParkingProvider {

    override val id: String = "parkapi"

    override fun covers(lat: Double, lon: Double): Boolean =
        cityConfigs.any { c ->
            lat in c.latMin..c.latMax && lon in c.lonMin..c.lonMax
        }

    override fun servedRegions(): Set<ParkingRegion> = this.servedRegions

    override suspend fun getParkingNearby(lat: Double, lon: Double, radiusMeters: Int): List<ParkingPoi> {
        val config = cityConfigs.firstOrNull { c ->
            lat in c.latMin..c.latMax && lon in c.lonMin..c.lonMax
        } ?: return emptyList()
        val radiusKm = radiusMeters / 1000.0
        val lots = api.getLots(config.citySlug)
        return lots
            .filter { it.lat != null && it.lon != null }
            .filter { haversineKm(lat, lon, it.lat!!, it.lon!!) <= radiusKm }
            .map { lot ->
                ParkingPoi(
                    id = "parkapi_${lot.id}",
                    name = lot.name,
                    latitude = lot.lat!!,
                    longitude = lot.lon!!,
                    capacity = lot.total,
                    available = lot.free,
                    openingHours = null,
                    priceInfo = null,
                    providerId = id,
                    address = lot.address,
                    state = lot.state,
                    distanceKm = null
                )
            }
    }

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
