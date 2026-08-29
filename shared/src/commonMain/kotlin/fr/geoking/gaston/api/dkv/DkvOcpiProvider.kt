package fr.geoking.gaston.api.dkv

import fr.geoking.gaston.api.common.OcpiEvseAvailability
import fr.geoking.gaston.api.fastned.mapOcpiStandard
import fr.geoking.gaston.poi.radiusKmFromMapViewport
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
 * [PoiProvider] backed by DKV Mobility's OCPI endpoints on `api.dkv-mobility.com`.
 *
 * OCPI Locations are not queryable by radius. Never retain the full catalog: scan with a hard
 * [maxFetch] cap, keep only stations inside the map viewport (or radius fallback), and stop once
 * [limit] matches are found.
 */
class DkvOcpiProvider(
    private val client: DkvOcpiClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 100,
    private val cacheMaxAgeMs: Long = 6 * 60 * 60_000L,
    private val maxFetch: Int = DEFAULT_MAX_FETCH,
) : PoiProvider {

    private var cache: CachedQuery? = null

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

        val nearby = ensureNearbyCached(latitude, longitude, effectiveRadiusKm, viewport)

        return nearby
            .mapNotNull { loc ->
                val lat = loc.coordinates?.latitude?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = loc.coordinates.longitude?.toDoubleOrNull() ?: return@mapNotNull null
                if (!inMapScope(lat, lon, latitude, longitude, effectiveRadiusKm, viewport)) {
                    return@mapNotNull null
                }
                loc to haversineKm(latitude, longitude, lat, lon)
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
                val (availableConnectors, totalConnectors) = OcpiEvseAvailability.counts(evses.map { it.status })

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
                    chargePointCount = totalConnectors.takeIf { it > 0 } ?: evses.size.takeIf { it > 0 },
                    fuelPrices = null,
                    irveDetails = IrveDetails(
                        connectorTypes = connectorTypes,
                        tarification = null,
                        availableConnectors = availableConnectors.takeIf { totalConnectors > 0 },
                        totalConnectors = totalConnectors.takeIf { it > 0 },
                    ),
                    source = "DKV"
                )
            }
    }

    override suspend fun clearCache() {
        cache = null
    }

    private suspend fun ensureNearbyCached(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        viewport: MapViewport?,
    ): List<DkvOcpiLocation> {
        val key = CacheKey.from(latitude, longitude, radiusKm, viewport)
        val now = System.currentTimeMillis()
        val hit = cache
        if (hit != null && hit.key == key && now - hit.atMs < cacheMaxAgeMs) {
            return hit.locations
        }
        val nearby = fetchNearbyLocations(latitude, longitude, radiusKm, viewport)
        cache = CachedQuery(key, nearby, System.currentTimeMillis())
        return nearby
    }

    private suspend fun fetchNearbyLocations(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        viewport: MapViewport?,
    ): List<DkvOcpiLocation> {
        val pageSize = 200
        val nearby = ArrayList<DkvOcpiLocation>(limit.coerceAtMost(100))
        var offset = 0
        while (offset < maxFetch) {
            val page = client.listLocations(limit = pageSize, offset = offset)
            if (page.isEmpty()) break
            for (loc in page) {
                val lat = loc.coordinates?.latitude?.toDoubleOrNull() ?: continue
                val lon = loc.coordinates.longitude?.toDoubleOrNull() ?: continue
                if (!inMapScope(lat, lon, latitude, longitude, radiusKm, viewport)) continue
                nearby.add(loc)
                if (nearby.size >= limit) return nearby
            }
            if (page.size < pageSize) break
            offset += pageSize
        }
        return nearby
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

    companion object {
        const val DEFAULT_MAX_FETCH = 5_000

        internal fun inMapScope(
            locLat: Double,
            locLon: Double,
            centerLat: Double,
            centerLon: Double,
            radiusKm: Int,
            viewport: MapViewport?,
        ): Boolean {
            if (viewport != null &&
                viewport.minLat != null &&
                viewport.maxLat != null &&
                viewport.minLng != null &&
                viewport.maxLng != null
            ) {
                return viewport.contains(locLat, locLon)
            }
            val r = 6371.0
            val rad = PI / 180.0
            val dLat = (locLat - centerLat) * rad
            val dLon = (locLon - centerLon) * rad
            val a = sin(dLat / 2).pow(2) +
                cos(centerLat * rad) * cos(locLat * rad) * sin(dLon / 2).pow(2)
            val dist = 2 * r * atan2(sqrt(a), sqrt(1 - a))
            return dist <= radiusKm
        }
    }

    private data class CacheKey(
        val lat: Int,
        val lon: Int,
        val radiusKm: Int,
        val minLatE4: Int?,
        val maxLatE4: Int?,
        val minLngE4: Int?,
        val maxLngE4: Int?,
    ) {
        companion object {
            fun from(
                latitude: Double,
                longitude: Double,
                radiusKm: Int,
                viewport: MapViewport?,
            ): CacheKey = CacheKey(
                lat = (latitude * 100).toInt(),
                lon = (longitude * 100).toInt(),
                radiusKm = radiusKm,
                minLatE4 = viewport?.minLat?.let { (it * 10_000).toInt() },
                maxLatE4 = viewport?.maxLat?.let { (it * 10_000).toInt() },
                minLngE4 = viewport?.minLng?.let { (it * 10_000).toInt() },
                maxLngE4 = viewport?.maxLng?.let { (it * 10_000).toInt() },
            )
        }
    }

    private data class CachedQuery(
        val key: CacheKey,
        val locations: List<DkvOcpiLocation>,
        val atMs: Long,
    )
}
