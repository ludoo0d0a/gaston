package fr.geoking.gaston.api.chargy

import fr.geoking.gaston.poi.radiusKmFromMapViewport
import fr.geoking.gaston.poi.IrveDetails
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import io.ktor.client.HttpClient
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * [PoiProvider] that fetches EV charging stations from Chargy Luxembourg real-time KML feed.
 * Only active for requests within Luxembourg.
 */
class ChargyProvider(
    private val client: HttpClient,
    private val apiKey: String,
    private val radiusKm: Int = 10,
    private val limit: Int = 50
) : PoiProvider {

    private val chargyClient = ChargyClient(client, apiKey)

    /** Luxembourg bounding box (approximate). */
    private val luxBbox = object {
        val latMin = 49.4
        val lonMin = 5.7
        val latMax = 50.2
        val lonMax = 6.6
    }

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Irve)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val effectiveRadiusKm = viewport
            ?.let {
                radiusKmFromMapViewport(latitude, longitude, it)
                    .coerceIn(1, 50)
            }
            ?: radiusKm

        // Only return results if coordinates are within/near Luxembourg
        if (latitude < luxBbox.latMin - 0.2 || latitude > luxBbox.latMax + 0.2 ||
            longitude < luxBbox.lonMin - 0.2 || longitude > luxBbox.lonMax + 0.2) {
            return emptyList()
        }

        val stations = chargyClient.getStations()

        return stations
            .map { s -> s to haversineKm(latitude, longitude, s.latitude, s.longitude) }
            .filter { it.second <= effectiveRadiusKm }
            .sortedBy { it.second }
            .take(limit)
            .map { (s, dist) ->
                val name = if (s.availableConnectors > 0) {
                    "${s.name} (${s.availableConnectors}/${s.totalConnectors} free)"
                } else {
                    "${s.name} (FULL)"
                }

                Poi(
                    id = "chargy-${s.latitude}-${s.longitude}",
                    name = name,
                    address = s.address,
                    latitude = s.latitude,
                    longitude = s.longitude,
                    brand = "Chargy",
                    isElectric = true,
                    powerKw = s.maxPowerKw,
                    operator = "Chargy",
                    isOnHighway = false,
                    chargePointCount = s.totalConnectors,
                    fuelPrices = null,
                    irveDetails = IrveDetails(
                        connectorTypes = s.connectorTypes,
                        availableConnectors = s.availableConnectors,
                        totalConnectors = s.totalConnectors
                    ),
                    source = "Chargy"
                )
            }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val rad = PI / 180.0
        val dLat = (lat2 - lat1) * rad
        val dLon = (lon2 - lon1) * rad
        val a = sin(dLat / 2).pow(2) + cos(lat1 * rad) * cos(lat2 * rad) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
