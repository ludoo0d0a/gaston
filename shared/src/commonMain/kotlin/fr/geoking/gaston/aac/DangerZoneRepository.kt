package fr.geoking.gaston.aac

import fr.geoking.gaston.api.radars.FranceRadarsClient
import fr.geoking.gaston.api.radars.toDangerZone
import fr.geoking.gaston.shared.location.haversineKm

/**
 * Local zone cache around the vehicle — used by the AAC alert loop (no map POI search).
 */
class DangerZoneRepository(
    private val franceRadarsClient: FranceRadarsClient,
) {
    private var cachedZones: List<DangerZone> = emptyList()
    private var cacheCenterLat: Double = Double.NaN
    private var cacheCenterLon: Double = Double.NaN
    private var cacheRadiusKm: Double = 0.0

    /**
     * Returns danger zones near the vehicle, refreshing when the vehicle leaves the cached area.
     * Mixes France open-data speed-control areas (as extended zones) with non-radar samples.
     */
    suspend fun zonesNear(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 25.0,
    ): List<DangerZone> {
        val needRefresh = cachedZones.isEmpty() ||
            cacheCenterLat.isNaN() ||
            haversineKm(latitude, longitude, cacheCenterLat, cacheCenterLon) > cacheRadiusKm * 0.4

        if (needRefresh) {
            val records = franceRadarsClient.getRecordsNear(latitude, longitude, radiusKm)
            val fromRadars = records.map { it.toDangerZone() }
            val nonRadar = StaticNonRadarDangerZones.near(latitude, longitude, radiusKm)
            cachedZones = fromRadars + nonRadar
            cacheCenterLat = latitude
            cacheCenterLon = longitude
            cacheRadiusKm = radiusKm
        }

        return cachedZones.filter { zone ->
            zone.distanceMetersFrom(latitude, longitude) <= radiusKm * 1000.0 + zone.radiusMeters
        }
    }

    fun clear() {
        cachedZones = emptyList()
        cacheCenterLat = Double.NaN
        cacheCenterLon = Double.NaN
        cacheRadiusKm = 0.0
    }

    /** For tests / diagnostics: is the mix not 100% radar-derived? */
    fun hasNonRadarZones(zones: List<DangerZone>): Boolean =
        zones.any { it.kind != DangerZoneKind.SpeedControlArea || it.source == StaticNonRadarDangerZones.SOURCE }
}
