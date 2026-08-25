package fr.geoking.gaston.api.chargyuk

import fr.geoking.gaston.poi.IrveDetails
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import fr.geoking.gaston.poi.radiusKmFromMapViewport
import io.ktor.client.HttpClient
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * [PoiProvider] for char.gy (UK open-street EV charging network).
 * Fetches real-time EVSE status and connector details from char.gy's open OCPI 2.2.1 feed.
 */
class CharGyUkProvider(
    private val client: HttpClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 50
) : PoiProvider {

    private val chargyClient = CharGyUkClient(client)

    /** UK bounding box (approximate). */
    private val ukBbox = object {
        val latMin = 49.5
        val lonMin = -8.8
        val latMax = 61.0
        val lonMax = 2.0
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

        // Only return results if coordinates are within/near the UK
        if (latitude < ukBbox.latMin || latitude > ukBbox.latMax ||
            longitude < ukBbox.lonMin || longitude > ukBbox.lonMax) {
            return emptyList()
        }

        val locations = try {
            chargyClient.getLocations(limit = 200)
        } catch (_: Exception) {
            emptyList()
        }

        val results = mutableListOf<Poi>()

        for (loc in locations) {
            val lat = loc.coordinates?.latitude?.toDoubleOrNull() ?: continue
            val lon = loc.coordinates?.longitude?.toDoubleOrNull() ?: continue

            val dist = haversineKm(latitude, longitude, lat, lon)
            if (dist > effectiveRadiusKm) continue

            var totalConnectors = 0
            var availableConnectors = 0
            var maxPowerKw = 0.0
            val connectorTypes = mutableSetOf<String>()

            val evses = loc.evses ?: emptyList()
            for (evse in evses) {
                val isAvailable = evse.status?.equals("AVAILABLE", ignoreCase = true) == true ||
                        evse.status?.equals("FREE", ignoreCase = true) == true ||
                        evse.status?.equals("IDLE", ignoreCase = true) == true

                val connectors = evse.connectors ?: emptyList()
                if (connectors.isEmpty()) {
                    totalConnectors++
                    if (isAvailable) availableConnectors++
                } else {
                    for (conn in connectors) {
                        totalConnectors++
                        if (isAvailable) availableConnectors++

                        val rawPower = conn.maxElectricPower ?: 0.0
                        val kw = if (rawPower > 100) rawPower / 1000.0 else rawPower
                        if (kw > maxPowerKw) maxPowerKw = kw

                        val standard = conn.standard?.uppercase() ?: ""
                        when {
                            standard.contains("COMBO") || standard.contains("CCS") -> connectorTypes.add("combo_ccs")
                            standard.contains("CHADEMO") -> connectorTypes.add("chademo")
                            standard.contains("T2") || standard.contains("TYPE_2") -> connectorTypes.add("type_2")
                            standard.contains("T1") || standard.contains("TYPE_1") -> connectorTypes.add("type_1")
                            standard.contains("DOMESTIC") || standard.contains("SCHUKO") -> connectorTypes.add("domestic")
                            else -> if (standard.isNotBlank()) connectorTypes.add(standard.lowercase())
                        }
                    }
                }
            }

            if (connectorTypes.isEmpty() && totalConnectors > 0) {
                connectorTypes.add("type_2")
            }

            val stationName = loc.name?.ifBlank { null }
                ?: loc.address?.ifBlank { null }
                ?: "char.gy Charge Point"

            val addressStr = listOfNotNull(loc.address, loc.city, loc.postalCode)
                .filter { it.isNotBlank() }
                .joinToString(", ")

            val displayName = if (totalConnectors > 0) {
                if (availableConnectors > 0) {
                    "$stationName ($availableConnectors/$totalConnectors free)"
                } else {
                    "$stationName (FULL)"
                }
            } else {
                stationName
            }

            results.add(
                Poi(
                    id = "chargy-uk-${loc.id}",
                    name = displayName,
                    address = addressStr,
                    latitude = lat,
                    longitude = lon,
                    brand = "char.gy",
                    isElectric = true,
                    powerKw = maxPowerKw,
                    operator = "char.gy",
                    isOnHighway = false,
                    chargePointCount = totalConnectors,
                    fuelPrices = null,
                    irveDetails = IrveDetails(
                        connectorTypes = connectorTypes,
                        availableConnectors = availableConnectors,
                        totalConnectors = totalConnectors
                    ),
                    source = "char.gy (UK)"
                )
            )
        }

        return results.sortedBy { haversineKm(latitude, longitude, it.latitude, it.longitude) }.take(limit)
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
