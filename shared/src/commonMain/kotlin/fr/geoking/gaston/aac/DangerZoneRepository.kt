package fr.geoking.gaston.aac

import fr.geoking.gaston.api.radars.FranceRadarRecord
import fr.geoking.gaston.api.radars.FranceRadarsClient
import fr.geoking.gaston.api.radars.LufopOpenSpeedCamClient
import fr.geoking.gaston.api.radars.toDangerZone
import fr.geoking.gaston.shared.location.haversineKm
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Local zone cache around the vehicle — used by the AAC alert loop (no map POI search).
 *
 * Anchors on data.gouv France radars, optionally enriched with OSM direction via [RadarOsmEnricher].
 */
class DangerZoneRepository(
    private val franceRadarsClient: FranceRadarsClient? = null,
    private val lufopOpenSpeedCamClient: LufopOpenSpeedCamClient? = null,
    private val roadClassifier: OsmRoadClassifier? = null,
    private val radarOsmEnricher: RadarOsmEnricher? = null,
) {
    private var cachedZones: List<DangerZone> = emptyList()
    private var cacheCenterLat: Double = Double.NaN
    private var cacheCenterLon: Double = Double.NaN
    private var cacheRadiusKm: Double = 0.0

    /**
     * Returns danger zones near the vehicle, refreshing when the vehicle leaves the cached area.
     * Mixes France open-data speed-control areas (as extended zones) with non-radar samples.
     * When [roadClassifier] is set, OSM highway tags refine zone [DangerZone.roadClass] / radius.
     * When [radarOsmEnricher] is set, OSM speed_camera / enforcement enrich monitored direction.
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
            val records = lufopOpenSpeedCamClient?.getRecordsNear(latitude, longitude, radiusKm)?.map { osc ->
                FranceRadarRecord(
                    id = osc.id,
                    type = osc.type,
                    vma = osc.vma,
                    latitude = osc.latitude,
                    longitude = osc.longitude
                )
            } ?: franceRadarsClient?.getRecordsNear(latitude, longitude, radiusKm) ?: emptyList()

            val fromRadars = classifyAndEnrich(records)
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

    private suspend fun classifyAndEnrich(records: List<FranceRadarRecord>): List<DangerZone> {
        val overrides = classifyRoadOverrides(records)
        val enricher = radarOsmEnricher
        return if (enricher != null) {
            enricher.enrichToZones(records, roadClassOverrides = overrides)
        } else {
            records.map { it.toDangerZone(roadClassOverride = overrides[it.id]) }
        }
    }

    private suspend fun classifyRoadOverrides(records: List<FranceRadarRecord>): Map<String, RoadNetworkClass> {
        val classifier = roadClassifier ?: return emptyMap()
        return coroutineScope {
            records.chunked(CLASSIFY_PARALLELISM).flatMap { chunk ->
                chunk.map { record ->
                    async {
                        val ctx = classifier.classify(
                            latitude = record.latitude,
                            longitude = record.longitude,
                            speedLimitKmH = record.vma,
                        )
                        val override = ctx.roadClass.takeIf { ctx.source == RoadContextSource.Osm }
                        if (override != null) record.id to override else null
                    }
                }.awaitAll()
            }.filterNotNull().toMap()
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

    companion object {
        private const val CLASSIFY_PARALLELISM = 4
    }
}
