package fr.geoking.gaston.aac

import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.api.radars.FranceRadarRecord
import fr.geoking.gaston.api.radars.FranceRadarsClient
import fr.geoking.gaston.api.radars.LufopOpenSpeedCamClient
import fr.geoking.gaston.api.radars.LuxembourgRadarsClient
import fr.geoking.gaston.api.radars.toDangerZone
import fr.geoking.gaston.road.RoadGeometrySource
import fr.geoking.gaston.road.RoadPolyline
import fr.geoking.gaston.shared.location.haversineKm
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.cos
import kotlin.math.PI

private val corridorLog by lazy { co.touchlab.kermit.Logger.withTag("RadarCorridor") }

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
    /** When set, speed-control zones near the vehicle follow the OSM road geometry. */
    private val roadGeometryRepository: RoadGeometrySource? = null,
) {
    private var cachedZones: List<DangerZone> = emptyList()
    private var cacheCenterLat: Double = Double.NaN
    private var cacheCenterLon: Double = Double.NaN
    private var cacheRadiusKm: Double = 0.0
    /** Zones whose road geometry was already requested (success or not) — one Overpass try each. */
    private val corridorAttemptedIds = mutableSetOf<String>()
    /** Guards [cachedZones] / [corridorAttemptedIds] updates (alert loop + map UIs share this repo). */
    private val stateLock = Mutex()

    /**
     * Returns danger zones near the vehicle, refreshing when the vehicle leaves the cached area.
     * Mixes open-data speed-control areas (as extended zones) with non-radar samples.
     * When [roadClassifier] is set, OSM highway tags refine zone [DangerZone.roadClass] / radius.
     * When [radarOsmEnricher] is set, OSM speed_camera / enforcement enrich monitored direction.
     */
    suspend fun zonesNear(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 100.0,
        corridorEnrichLimit: Int = CORRIDOR_ENRICH_PER_CALL,
    ): List<DangerZone> {
        val needRefresh = cachedZones.isEmpty() ||
            cacheCenterLat.isNaN() ||
            haversineKm(latitude, longitude, cacheCenterLat, cacheCenterLon) > cacheRadiusKm * 0.4

        if (needRefresh) {
            val records = collectRecords(latitude, longitude, radiusKm)
            val fromRadars = classifyAndEnrich(records, latitude, longitude)
            val nonRadar = StaticNonRadarDangerZones.near(latitude, longitude, radiusKm)
            stateLock.withLock {
                cachedZones = fromRadars + nonRadar
                cacheCenterLat = latitude
                cacheCenterLon = longitude
                cacheRadiusKm = radiusKm
                corridorAttemptedIds.clear()
            }
        }

        enrichNearbyWithRoadGeometry(latitude, longitude, corridorEnrichLimit)

        return cachedZones.filter { zone ->
            zone.distanceMetersFrom(latitude, longitude) <= radiusKm * 1000.0 + zone.radiusMeters
        }
    }

    /**
     * Road-following polygons (the exact alert hit-test geometry) of the zones within
     * [radiusKm] of the point, for drawing on phone / Android Auto maps. Enriches up to
     * [CORRIDOR_ENRICH_PER_MAP_CALL] more zones per call, so repeated calls fill the map.
     */
    suspend fun roadCorridorRingsNear(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 8.0,
    ): List<List<Pair<Double, Double>>> =
        zonesNear(latitude, longitude, corridorEnrichLimit = CORRIDOR_ENRICH_PER_MAP_CALL)
            .filter { it.distanceMetersFrom(latitude, longitude) <= radiusKm * 1000.0 }
            .flatMap { it.corridorRings }

    /**
     * Attaches the OSM road corridor (length = zone radius along the road) to the closest
     * speed-control zones the vehicle is about to reach. Lazy + bounded: one road fetch per
     * [RoadNetworkClass] batch (not per zone), so Overpass is not hammered on map refresh.
     */
    private suspend fun enrichNearbyWithRoadGeometry(latitude: Double, longitude: Double, limit: Int) {
        val pending = stateLock.withLock {
            cachedZones
                .filter {
                    it.kind == DangerZoneKind.SpeedControlArea &&
                        // Empty or synthetic-only: still eligible for an OSM upgrade.
                        (it.corridorRings.isEmpty() || it.corridorIsSynthetic) &&
                        it.id !in corridorAttemptedIds &&
                        it.distanceMetersFrom(latitude, longitude) <= it.radiusMeters + CORRIDOR_PREFETCH_METERS
                }
                .sortedBy { it.distanceMetersFrom(latitude, longitude) }
                .take(limit)
        }
        corridorLog.i { "enrich at ($latitude,$longitude): pending=${pending.size} cachedZones=${cachedZones.size}" }
        if (pending.isEmpty()) return

        // One radial query per road class covering all pending tips of that class.
        // On Overpass failure, remaining classes get empty roads → synthetic blue ribbons
        // (kept upgradeable until a real OSM snap succeeds).
        val roads = roadGeometryRepository
        val polylinesByClass = linkedMapOf<RoadNetworkClass, List<RoadPolyline>>()
        var fetchFailed = false
        for ((roadClass, zones) in pending.groupBy { it.roadClass }) {
            if (roads == null || fetchFailed) {
                polylinesByClass[roadClass] = emptyList()
                continue
            }
            val coverRadiusM = zones.maxOf { zone ->
                zone.distanceMetersFrom(latitude, longitude) + zone.radiusMeters
            }
            val polylines = try {
                roads.fetchRoadsAround(latitude, longitude, coverRadiusM, roadClass)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                corridorLog.w(e) { "Road corridor batch skipped for $roadClass: ${e.message}" }
                fetchFailed = true
                emptyList()
            }
            polylinesByClass[roadClass] = polylines
        }

        for (zone in pending) {
            val polylines = polylinesByClass[zone.roadClass].orEmpty()
            val fromRoads = RadarRoadCorridorCalculator.corridorRings(
                tipLat = zone.centerLatitude,
                tipLon = zone.centerLongitude,
                radiusMeters = zone.radiusMeters,
                monitoredBearingDegrees = zone.monitoredBearingDegrees,
                bidirectional = zone.monitoredBidirectional,
                roads = polylines,
                allowSynthetic = false,
            )
            val synthetic = fromRoads.isEmpty()
            val rings = fromRoads.ifEmpty {
                RadarRoadCorridorCalculator.syntheticCorridorRings(
                    tipLat = zone.centerLatitude,
                    tipLon = zone.centerLongitude,
                    radiusMeters = zone.radiusMeters,
                    monitoredBearingDegrees = zone.monitoredBearingDegrees,
                    bidirectional = zone.monitoredBidirectional,
                )
            }
            if (rings.isEmpty()) continue
            stateLock.withLock {
                cachedZones = cachedZones.map {
                    if (it.id == zone.id) it.withRoadCorridor(rings, synthetic = synthetic) else it
                }
                // Only freeze enrichment once OSM road geometry is attached.
                if (!synthetic) corridorAttemptedIds.add(zone.id)
            }
            corridorLog.i {
                "zone ${zone.id}: rings=${rings.size} roads=${polylines.size} " +
                    "class=${zone.roadClass} synthetic=$synthetic"
            }
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
                    monitoredBearingDegrees = osc.azimut,
                    bidirectional = osc.flash?.contains("double", ignoreCase = true) == true,
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

    private suspend fun classifyAndEnrich(
        records: List<FranceRadarRecord>,
        latitude: Double,
        longitude: Double,
    ): List<DangerZone> {
        val overrides = classifyRoadOverrides(records, latitude, longitude)
        val enricher = radarOsmEnricher
        return if (enricher != null) {
            enricher.enrichToZones(records, roadClassOverrides = overrides)
        } else {
            records.map { it.toDangerZone(roadClassOverride = overrides[it.id]) }
        }
    }

    /**
     * Road class per radar from OSM, for radars close to the vehicle only: one Overpass query per
     * radar over the whole 100 km zone radius saturates the public instance.
     */
    private suspend fun classifyRoadOverrides(
        records: List<FranceRadarRecord>,
        latitude: Double,
        longitude: Double,
    ): Map<String, RoadNetworkClass> {
        val classifier = roadClassifier ?: return emptyMap()
        val nearby = records.filter {
            haversineKm(latitude, longitude, it.latitude, it.longitude) <= CLASSIFY_RADIUS_KM
        }
        return coroutineScope {
            nearby.chunked(CLASSIFY_PARALLELISM).flatMap { chunk ->
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
        corridorAttemptedIds.clear()
    }

    /** For tests / diagnostics: is the mix not 100% radar-derived? */
    fun hasNonRadarZones(zones: List<DangerZone>): Boolean =
        zones.any { it.kind != DangerZoneKind.SpeedControlArea || it.source == StaticNonRadarDangerZones.SOURCE }

    companion object {
        private const val CLASSIFY_PARALLELISM = 4
        private const val CLASSIFY_RADIUS_KM = 5.0
        private const val CORRIDOR_PREFETCH_METERS = 3_000.0
        private const val CORRIDOR_ENRICH_PER_CALL = 3
        private const val CORRIDOR_ENRICH_PER_MAP_CALL = 6
    }
}
