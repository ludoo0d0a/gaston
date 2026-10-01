package fr.geoking.gaston.aac

import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.api.radars.FranceRadarRecord
import fr.geoking.gaston.api.radars.FranceRadarsClient
import fr.geoking.gaston.api.radars.LufopOpenSpeedCamClient
import fr.geoking.gaston.api.radars.LuxembourgRadarsClient
import fr.geoking.gaston.api.radars.toDangerZone
import fr.geoking.gaston.shared.location.haversineKm
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.cos
import kotlin.math.PI

/**
 * Local zone cache around the vehicle — used by the AAC alert loop (no map POI search).
 *
 * Prefers Lufop + Luxembourg open data; FranceRadars remains a fallback (provider disabled in UI).
 * When [useOsmRadarsFallback] (no Lufop API key), OSM `speed_camera` fills empty coverage.
 * Optionally enriched with OSM direction via [RadarOsmEnricher].
 */
class DangerZoneRepository(
    private val franceRadarsClient: FranceRadarsClient? = null,
    private val lufopOpenSpeedCamClient: LufopOpenSpeedCamClient? = null,
    private val luxembourgRadarsClient: LuxembourgRadarsClient? = null,
    private val overpassClient: OverpassClient? = null,
    private val useOsmRadarsFallback: Boolean = false,
    private val roadClassifier: OsmRoadClassifier? = null,
    private val radarOsmEnricher: RadarOsmEnricher? = null,
) {
    private var cachedZones: List<DangerZone> = emptyList()
    private var cacheCenterLat: Double = Double.NaN
    private var cacheCenterLon: Double = Double.NaN
    private var cacheRadiusKm: Double = 0.0

    /**
     * Returns danger zones near the vehicle, refreshing when the vehicle leaves the cached area.
     * Mixes open-data speed-control areas (as extended zones) with non-radar samples.
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
            val records = collectRecords(latitude, longitude, radiusKm)
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

    private suspend fun collectRecords(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ): List<FranceRadarRecord> {
        val records = mutableListOf<FranceRadarRecord>()
        lufopOpenSpeedCamClient?.getRecordsNear(latitude, longitude, radiusKm)?.forEach { osc ->
            records.add(
                FranceRadarRecord(
                    id = osc.id,
                    type = osc.type,
                    vma = osc.vma,
                    latitude = osc.latitude,
                    longitude = osc.longitude,
                )
            )
        }
        luxembourgRadarsClient?.getRecordsNear(latitude, longitude, radiusKm)?.forEach { lu ->
            records.add(lu.toFranceRadarRecord())
        }
        if (records.isEmpty()) {
            franceRadarsClient?.getRecordsNear(latitude, longitude, radiusKm)?.let { records.addAll(it) }
        }
        if (records.isEmpty() && useOsmRadarsFallback && overpassClient != null) {
            records.addAll(fetchOsmSpeedCameras(latitude, longitude, radiusKm))
        }
        return records
    }

    private suspend fun fetchOsmSpeedCameras(
        latitude: Double,
        longitude: Double,
        radiusKm: Double,
    ): List<FranceRadarRecord> {
        val client = overpassClient ?: return emptyList()
        val latDelta = radiusKm / 111.0
        val lonDelta = radiusKm / (111.0 * cos(latitude * PI / 180.0).coerceAtLeast(0.2))
        return try {
            val bundle = client.querySpeedCamerasInBbox(
                minLat = latitude - latDelta,
                minLon = longitude - lonDelta,
                maxLat = latitude + latDelta,
                maxLon = longitude + lonDelta,
            )
            bundle.cameras.mapNotNull { cam ->
                if (haversineKm(latitude, longitude, cam.lat, cam.lon) > radiusKm) return@mapNotNull null
                val vma = cam.tags["maxspeed"]?.filter { it.isDigit() }?.toIntOrNull()
                FranceRadarRecord(
                    id = "osm_${cam.id}",
                    type = "OSM",
                    vma = vma,
                    latitude = cam.lat,
                    longitude = cam.lon,
                )
            }
        } catch (_: Exception) {
            emptyList()
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
