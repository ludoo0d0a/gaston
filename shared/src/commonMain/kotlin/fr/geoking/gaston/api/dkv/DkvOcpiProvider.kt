package fr.geoking.gaston.api.dkv

import fr.geoking.gaston.api.fastned.mapOcpiStandard
import fr.geoking.gaston.api.routex.radiusKmFromMapViewport
import fr.geoking.gaston.poi.IrveDetails
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * [PoiProvider] backed by DKV Mobility's OCPI endpoints exposed via `api-portal.dkv-mobility.com`.
 *
 * OCPI Locations are not queryable by radius in the base spec; in practice, most deployments
 * expect bulk sync with pagination. This provider therefore caches the full dataset for
 * [cacheMaxAgeMs] and does in-memory distance filtering.
 */
class DkvOcpiProvider(
    private val client: DkvOcpiClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 150,
    private val cacheMaxAgeMs: Long = 6 * 60 * 60_000L
) : PoiProvider {

    private var cachedLocations: List<DkvOcpiLocation> = emptyList()
    private var cacheTimestampMs: Long = 0L

    override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.Irve)

    override suspend fun getGasStations(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport?
    ): List<Poi> {
        val effectiveRadiusKm = viewport
            ?.let {
                radiusKmFromMapViewport(latitude, longitude, it.zoom, it.mapWidthPx, it.mapHeightPx)
                    .coerceIn(1, 50)
            }
            ?: radiusKm

        ensureCachePopulated()

        return cachedLocations
            .mapNotNull { loc ->
                val lat = loc.coordinates?.latitude?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = loc.coordinates.longitude?.toDoubleOrNull() ?: return@mapNotNull null
                val dist = haversineKm(latitude, longitude, lat, lon)
                if (dist > effectiveRadiusKm) return@mapNotNull null
                loc to dist
            }
            .sortedBy { it.second }
            .take(limit)
            .map { (loc, _) ->
                val lat = loc.coordinates!!.latitude!!.toDouble()
                val lon = loc.coordinates.longitude!!.toDouble()
                val evses = loc.evses.orEmpty()
                val connectors = evses.flatMap { it.connectors.orEmpty() }
                val connectorTypes = connectors.mapNotNull { it.standard?.let(::mapOcpiStandard) }.toSet()
                val maxPowerKw = connectors
                    .mapNotNull { it.maxElectricPower }
                    .maxOrNull()
                    ?.let { it / 1000.0 }
                    ?: connectors.mapNotNull { powerKwFromAmpsVolts(it) }.maxOrNull()

                val address = buildString {
                    if (!loc.address.isNullOrBlank()) append(loc.address)
                    if (!loc.city.isNullOrBlank()) {
                        if (isNotEmpty()) append(", ")
                        append(loc.city)
                    }
                    if (!loc.postalCode.isNullOrBlank()) {
                        if (isNotEmpty()) append(" ")
                        append(loc.postalCode)
                    }
                    if (!loc.countryCode.isNullOrBlank()) {
                        if (isNotEmpty()) append(" ")
                        append(loc.countryCode)
                    }
                }.ifBlank { loc.countryCode ?: "EU" }

                Poi(
                    id = "dkv-${loc.id}",
                    name = loc.name?.ifBlank { null } ?: "DKV Mobility",
                    address = address,
                    latitude = lat,
                    longitude = lon,
                    brand = "dkv",
                    isElectric = true,
                    poiCategory = PoiCategory.Irve,
                    powerKw = maxPowerKw,
                    operator = loc.operator?.name ?: "DKV Mobility",
                    isOnHighway = false,
                    chargePointCount = evses.size.takeIf { it > 0 },
                    fuelPrices = null,
                    irveDetails = IrveDetails(
                        connectorTypes = connectorTypes,
                        tarification = null
                    ),
                    source = "DKV"
                )
            }
    }

    override fun clearCache() {
        cachedLocations = emptyList()
        cacheTimestampMs = 0L
    }

    private suspend fun ensureCachePopulated() {
        val now = System.currentTimeMillis()
        if (cachedLocations.isNotEmpty() && now - cacheTimestampMs < cacheMaxAgeMs) return
        cachedLocations = fetchAllLocations()
        cacheTimestampMs = now
    }

    private suspend fun fetchAllLocations(): List<DkvOcpiLocation> {
        val pageSize = 200
        val result = mutableListOf<DkvOcpiLocation>()
        var offset = 0
        while (true) {
            val page = client.listLocations(limit = pageSize, offset = offset)
            result.addAll(page)
            if (page.size < pageSize) break
            offset += pageSize
        }
        return result
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val rad = PI / 180.0
        val dLat = (lat2 - lat1) * rad
        val dLon = (lon2 - lon1) * rad
        val a = sin(dLat / 2).pow(2) + cos(lat1 * rad) * cos(lat2 * rad) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun powerKwFromAmpsVolts(connector: DkvOcpiConnector): Double? {
        val v = connector.maxVoltage ?: return null
        val a = connector.maxAmperage ?: return null
        return (v * a) / 1000.0
    }
}

