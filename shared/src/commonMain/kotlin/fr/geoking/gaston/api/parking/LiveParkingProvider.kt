package fr.geoking.gaston.api.parking

import fr.geoking.gaston.parking.ParkingPoi
import fr.geoking.gaston.parking.ParkingProvider
import fr.geoking.gaston.parking.ParkingRegion
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Parking provider using [LiveParkingClient]. Covers areas where LiveParking has data
 * (e.g. Berlin, Cologne). Provides capacity and availability; no prices or opening hours.
 * Serves [ParkingRegion.Germany] only.
 */
class LiveParkingProvider(
    private val api: LiveParkingClient,
    /** Bounding box (latMin, latMax, lonMin, lonMax) for coverage (e.g. Germany cities served by LiveParking). */
    private val latMin: Double = 50.85,
    private val latMax: Double = 52.70,
    private val lonMin: Double = 6.80,
    private val lonMax: Double = 13.80
) : ParkingProvider {

    override val id: String = "liveparking"

    override fun covers(lat: Double, lon: Double): Boolean =
        lat in latMin..latMax && lon in lonMin..lonMax

    override fun servedRegions(): Set<ParkingRegion> = setOf(ParkingRegion.Germany)

    override suspend fun getParkingNearby(lat: Double, lon: Double, radiusMeters: Int): List<ParkingPoi> {
        val radiusKm = radiusMeters / 1000.0
        val locations = api.getLocations(lat, lon, limit = 100)
        return locations
            .filter { haversineKm(lat, lon, it.lat, it.lon) <= radiusKm }
            .map { loc ->
                ParkingPoi(
                    id = "liveparking_${loc.id}",
                    name = loc.name,
                    latitude = loc.lat,
                    longitude = loc.lon,
                    capacity = loc.capacity,
                    available = loc.available,
                    openingHours = null,
                    priceInfo = null,
                    providerId = id,
                    address = null,
                    state = loc.status,
                    distanceKm = loc.distanceKm
                )
            }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = kotlin.math.PI * (lat2 - lat1) / 180.0
        val dLon = kotlin.math.PI * (lon2 - lon1) / 180.0
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(kotlin.math.PI * lat1 / 180.0) * cos(kotlin.math.PI * lat2 / 180.0) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }
}
