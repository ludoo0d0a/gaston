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
 */
class EcoMovementAvailabilityProvider(
    private val client: EcoMovementOcpiClient,
    private val radiusKm: Int = 15,
    private val limit: Int = 200,
    private val cacheTtlMs: Long = 120_000L,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) : BorneAvailabilityProvider {

    private val mutex = Mutex()
    private var cache: CachedLocations? = null

    override suspend fun getAvailability(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
    ): List<PdcAvailability> {
        val effectiveRadius = if (radiusKm > 0) radiusKm else this.radiusKm
        val locations = getOrFetchLocations()
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

    private suspend fun getOrFetchLocations(): List<EcoMovementOcpiLocation> = mutex.withLock {
        val cached = cache
        val now = nowMs()
        if (cached != null && now - cached.atMs < cacheTtlMs) return@withLock cached.locations
        val locations = fetchAllLocations()
        cache = CachedLocations(locations, now)
        locations
    }

    private suspend fun fetchAllLocations(): List<EcoMovementOcpiLocation> {
        val pageSize = 1000
        val result = mutableListOf<EcoMovementOcpiLocation>()
        var offset = 0
        while (true) {
            val page = try {
                client.listLocations(limit = pageSize, offset = offset)
            } catch (_: Exception) {
                break
            }
            result.addAll(page)
            if (page.size < pageSize) break
            offset += pageSize
        }
        return result
    }

    private data class CachedLocations(val locations: List<EcoMovementOcpiLocation>, val atMs: Long)
}
