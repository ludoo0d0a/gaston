package fr.geoking.gaston.api.parking

import fr.geoking.gaston.parking.ParkingPoi
import fr.geoking.gaston.parking.ParkingProvider
import fr.geoking.gaston.parking.ParkingRegion
import fr.geoking.gaston.shared.location.haversineKm

/**
 * Parking provider using [LuxembourgParkingClient].
 * Serves [ParkingRegion.Luxembourg] with real-time car park availability.
 */
class LuxembourgParkingProvider(
    private val client: LuxembourgParkingClient
) : ParkingProvider {

    override val id: String = "luxembourg_vdl"

    override fun covers(lat: Double, lon: Double): Boolean =
        ParkingRegion.Luxembourg.contains(lat, lon)

    override fun servedRegions(): Set<ParkingRegion> = setOf(ParkingRegion.Luxembourg)

    override suspend fun getParkingNearby(lat: Double, lon: Double, radiusMeters: Int): List<ParkingPoi> {
        val radiusKm = radiusMeters / 1000.0
        val parkings = client.getParkings()
        return parkings
            .map { loc -> loc to haversineKm(lat, lon, loc.latitude, loc.longitude) }
            .filter { it.second <= radiusKm }
            .sortedBy { it.second }
            .map { (loc, dist) ->
                ParkingPoi(
                    id = "luxembourg_vdl_${loc.id}",
                    name = loc.title,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    capacity = loc.totalCapacity,
                    available = loc.availableSpaces,
                    openingHours = loc.openingHours,
                    priceInfo = loc.priceInfo,
                    providerId = id,
                    address = loc.address ?: loc.quartier,
                    state = loc.status,
                    distanceKm = dist
                )
            }
    }
}
