package fr.geoking.gaston.api.ecomovement

import fr.geoking.gaston.api.belib.BorneAvailabilityProvider
import fr.geoking.gaston.api.belib.PdcAvailability
import fr.geoking.gaston.api.common.OcpiEvseAvailability
import fr.geoking.gaston.shared.location.haversineKm
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Global [BorneAvailabilityProvider] backed by Eco-Movement OCPI locations (EVSE status).
 * Used as fallback when no country-specific feed (QualiCharge, Belib, Belgium NAP) applies.
 *
 * Eco-Movement `/locations` is a bulk catalog (no geo filter). Never retain the full dataset:
 * paginate with a hard cap and keep only locations inside the query radius.
 */
class EcoMovementAvailabilityProvider(
    private val client: EcoMovementOcpiClient,
    private val radiusKm: Int = 15,
    private val limit: Int = 100,
    private val cacheTtlMs: Long = 120_000L,
    private val maxFetch: Int = DEFAULT_MAX_FETCH,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : BorneAvailabilityProvider {

    private val mutex = Mutex()
    private var cache: CachedQuery? = null

    override suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
    ): List<PdcAvailability> {
        val effectiveRadius = if (radiusKm > 0) radiusKm else this.radiusKm
        val locations = getOrFetchNearby(latitude, longitude, effectiveRadius)
        if (locations.isEmpty()) return emptyList()

        return locations.asSequence()
            .flatMap { loc ->
                val lat = loc.coordinates?.latitude?.toDoubleOrNull() ?: return@flatMap emptySequence()
                val lon = loc.coordinates?.longitude?.toDoubleOrNull() ?: return@flatMap emptySequence()
                val dist = haversineKm(latitude, longitude, lat, lon)
                if (dist > effectiveRadius) return@flatMap emptySequence()
                (loc.evses.orEmpty()).asSequence().mapNotNull { evse ->
                    val id = evse.evseId?.takeIf { it.isNotBlank() }
                        ?: evse.uid?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    if (OcpiEvseAvailability.isRemoved(evse.status)) return@mapNotNull null
                    Triple(
                        PdcAvailability(
                            id = id,
                            status = OcpiEvseAvailability.mapStatus(evse.status),
                            latitude = lat,
                            longitude = lon,
                            stationId = loc.id,
                        ),
                        dist,
                        Unit,
                    )
                }
            }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    private suspend fun getOrFetchNearby(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
    ): List<EcoMovementOcpiLocation> = mutex.withLock {
        val key = CacheKey(
            lat = (latitude * 100).toInt(),
            lon = (longitude * 100).toInt(),
            radiusKm = radiusKm,
        )
        val cached = cache
        val now = nowMs()
        if (cached != null && cached.key == key && now - cached.atMs < cacheTtlMs) {
            return@withLock cached.locations
        }
        val locations = fetchNearbyLocations(latitude, longitude, radiusKm)
        if (locations.isNotEmpty()) {
            cache = CachedQuery(key, locations, now)
            locations
        } else {
            cached?.locations ?: emptyList()
        }
    }

    private suspend fun fetchNearbyLocations(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
    ): List<EcoMovementOcpiLocation> {
        val pageSize = EcoMovementOcpiClient.MAX_PAGE
        val nearby = ArrayList<EcoMovementOcpiLocation>()
        var offset = 0
        while (offset < maxFetch) {
            val page = try {
                client.listLocations(limit = pageSize, offset = offset)
            } catch (_: Exception) {
                break
            }
            if (page.isEmpty()) break
            for (loc in page) {
                val lat = loc.coordinates?.latitude?.toDoubleOrNull() ?: continue
                val lon = loc.coordinates?.longitude?.toDoubleOrNull() ?: continue
                if (haversineKm(latitude, longitude, lat, lon) <= radiusKm) {
                    nearby.add(loc)
                    if (nearby.size >= limit) return nearby
                }
            }
            if (page.size < pageSize) break
            offset += pageSize
        }
        return nearby
    }

    companion object {
        /** Hard cap on catalog scan size — full Europe sync OOMs on mobile. */
        const val DEFAULT_MAX_FETCH = 500
    }

    private data class CacheKey(val lat: Int, val lon: Int, val radiusKm: Int)
    private data class CachedQuery(
        val key: CacheKey,
        val locations: List<EcoMovementOcpiLocation>,
        val atMs: Long,
    )
}
