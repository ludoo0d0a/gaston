package fr.geoking.gaston.api.ecomovement

import fr.geoking.gaston.api.common.OcpiEvseAvailability
import fr.geoking.gaston.api.fastned.mapOcpiStandard
import fr.geoking.gaston.poi.radiusKmFromMapViewport
import fr.geoking.gaston.poi.IrveDetails
import fr.geoking.gaston.poi.MapViewport
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * [PoiProvider] backed by Eco-Movement's OCPI 2.2.1 endpoints.
 *
 * `/locations` has no geo filter. Never cache the full European catalog: scan with a hard
 * [maxFetch] cap, keep only stations inside the map viewport (or radius fallback), and stop
 * once [limit] matches are found.
 */
class EcoMovementOcpiProvider(
    private val client: EcoMovementOcpiClient,
    private val radiusKm: Int = 10,
    private val limit: Int = 100,
    private val cacheMaxAgeMs: Long = 6 * 60 * 60_000L,
    private val maxFetch: Int = DEFAULT_MAX_FETCH,
) : PoiProvider {

    private var cache: CachedQuery? = null
    private val mutex = Mutex()

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
                val coords = loc.coordinates ?: return@mapNotNull null
                val locLat = coords.latitude?.toDoubleOrNull() ?: return@mapNotNull null
                val locLon = coords.longitude?.toDoubleOrNull() ?: return@mapNotNull null
                if (!inMapScope(locLat, locLon, latitude, longitude, effectiveRadiusKm, viewport)) {
                    return@mapNotNull null
                }
                Triple(loc, locLat, locLon) to haversineKm(latitude, longitude, locLat, locLon)
            }
            .sortedBy { it.second }
            .take(limit)
            .map { (data, _) ->
                val (loc, locLat, locLon) = data
                val evses = loc.evses.orEmpty()
                val connectors = evses.flatMap { it.connectors.orEmpty() }
                val connectorTypes = connectors.mapNotNull { it.standard?.let(::mapOcpiStandard) }.toSet()
                val maxPowerKw = connectors
                    .mapNotNull { it.maxElectricPower }
                    .maxOrNull()
                    ?.let { it / 1000.0 }
                    ?: connectors.mapNotNull { powerKwFromAmpsVolts(it) }.maxOrNull()
                val (availableConnectors, totalConnectors) = OcpiEvseAvailability.counts(evses.map { it.status })
                val pdcIds = evses.mapNotNull { it.evseId?.takeIf { id -> id.isNotBlank() } ?: it.uid?.takeIf { id -> id.isNotBlank() } }.toSet()

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
                    id = "ecomovement-${loc.id}",
                    name = loc.name?.ifBlank { null } ?: "Charging Station",
                    address = address,
                    latitude = locLat,
                    longitude = locLon,
                    brand = "ecomovement",
                    isElectric = true,
                    poiCategory = PoiCategory.Irve,
                    powerKw = maxPowerKw,
                    operator = loc.operator?.name ?: "Eco-Movement",
                    isOnHighway = false,
                    chargePointCount = totalConnectors.takeIf { it > 0 } ?: evses.size.takeIf { it > 0 },
                    fuelPrices = null,
                    irveDetails = IrveDetails(
                        connectorTypes = connectorTypes,
                        tarification = null,
                        availableConnectors = availableConnectors.takeIf { totalConnectors > 0 },
                        totalConnectors = totalConnectors.takeIf { it > 0 },
                        pdcIds = pdcIds,
                    ),
                    source = "Eco-Movement"
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
    ): List<EcoMovementOcpiLocation> {
        val key = CacheKey.from(latitude, longitude, radiusKm, viewport)
        val now = currentTimeMs()
        val hit = cache
        if (hit != null && hit.key == key && now - hit.atMs < cacheMaxAgeMs) {
            return hit.locations
        }

        return mutex.withLock {
            val again = cache
            if (again != null && again.key == key && currentTimeMs() - again.atMs < cacheMaxAgeMs) {
                return@withLock again.locations
            }
            val nearby = fetchNearbyLocations(latitude, longitude, radiusKm, viewport)
            cache = CachedQuery(key, nearby, currentTimeMs())
            nearby
        }
    }

    private suspend fun fetchNearbyLocations(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
        viewport: MapViewport?,
    ): List<EcoMovementOcpiLocation> {
        val pageSize = EcoMovementOcpiClient.MAX_PAGE
        val nearby = ArrayList<EcoMovementOcpiLocation>(limit.coerceAtMost(100))
        var offset = 0
        while (offset < maxFetch) {
            val page = client.listLocations(limit = pageSize, offset = offset)
            if (page.isEmpty()) break
            for (loc in page) {
                val coords = loc.coordinates ?: continue
                val locLat = coords.latitude?.toDoubleOrNull() ?: continue
                val locLon = coords.longitude?.toDoubleOrNull() ?: continue
                if (!inMapScope(locLat, locLon, latitude, longitude, radiusKm, viewport)) continue
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

    private fun powerKwFromAmpsVolts(connector: EcoMovementOcpiConnector): Double? {
        val v = connector.maxVoltage ?: return null
        val a = connector.maxAmperage ?: return null
        return (v * a) / 1000.0
    }

    private fun currentTimeMs(): Long = System.currentTimeMillis()

    companion object {
        const val DEFAULT_MAX_FETCH = 500

        /** Prefer map bbox when present; otherwise radius circle around the search center. */
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
        val locations: List<EcoMovementOcpiLocation>,
        val atMs: Long,
    )
}
