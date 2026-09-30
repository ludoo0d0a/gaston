package fr.geoking.gaston.aac

import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.api.overpass.OverpassElement
import fr.geoking.gaston.api.overpass.OverpassEnforcementRelation
import fr.geoking.gaston.api.overpass.OverpassWayGeom
import fr.geoking.gaston.api.overpass.SpeedCameraOverpassBundle
import fr.geoking.gaston.api.radars.FranceRadarRecord
import fr.geoking.gaston.api.radars.toDangerZone
import fr.geoking.gaston.shared.location.haversineKm
import kotlin.math.cos
import kotlin.math.PI

/**
 * Matches data.gouv France radar records to nearby OSM speed_camera nodes and
 * resolves monitored traffic direction (enforcement / forward-backward / both).
 */
class RadarOsmEnricher(
    private val overpass: OverpassClient,
    private val matchRadiusMeters: Double = DEFAULT_MATCH_RADIUS_METERS,
) {
    companion object {
        const val DEFAULT_MATCH_RADIUS_METERS = 40.0
        private const val BBOX_PAD_KM = 0.05
    }

    /**
     * Enrich [records] with OSM direction when a spatial match exists.
     * Failures against Overpass return unenriched zones (data.gouv coverage preserved).
     */
    suspend fun enrichToZones(
        records: List<FranceRadarRecord>,
        roadClassOverrides: Map<String, RoadNetworkClass> = emptyMap(),
    ): List<DangerZone> {
        if (records.isEmpty()) return emptyList()
        val bundle = try {
            fetchBundle(records)
        } catch (_: Exception) {
            return records.map { rec ->
                rec.toDangerZone(roadClassOverride = roadClassOverrides[rec.id])
            }
        }
        return records.map { record ->
            val base = record.toDangerZone(roadClassOverride = roadClassOverrides[record.id])
            val info = matchAndResolve(record, bundle) ?: return@map base
            base.withOsmDirection(info)
        }
    }

    /**
     * Pure match helper for tests: pick best OSM camera for a CSV point.
     */
    fun bestMatch(
        latitude: Double,
        longitude: Double,
        vma: Int?,
        cameras: List<OverpassElement>,
    ): Pair<OverpassElement, Double>? {
        var best: Pair<OverpassElement, Double>? = null
        for (cam in cameras) {
            val distM = haversineKm(latitude, longitude, cam.lat, cam.lon) * 1000.0
            if (distM > matchRadiusMeters) continue
            val osmVma = OsmSpeedCameraDirection.parseMaxspeedKmH(cam.tags["maxspeed"])
            val score = distM -
                (if (vma != null && osmVma != null && vma == osmVma) 5.0 else 0.0) -
                (if (!cam.tags["ref"].isNullOrBlank()) 2.0 else 0.0)
            val current = best
            if (current == null || score < current.second) {
                best = cam to score
            }
        }
        return best?.let { (cam, _) ->
            cam to haversineKm(latitude, longitude, cam.lat, cam.lon) * 1000.0
        }
    }

    fun resolveDirectionForCamera(
        camera: OverpassElement,
        matchedDistanceMeters: Double,
        bundle: SpeedCameraOverpassBundle,
    ): OsmDirectionInfo {
        val enforcementBearing = enforcementBearingForCamera(camera.id, bundle)
        val wayBearing = nearestWayBearing(camera.lat, camera.lon, bundle.ways)
        return OsmSpeedCameraDirection.resolve(
            directionTag = camera.tags["direction"],
            wayBearingDegrees = wayBearing,
            enforcementBearingDegrees = enforcementBearing,
            osmNodeId = camera.id,
            matchedDistanceMeters = matchedDistanceMeters,
            osmMaxspeedKmH = OsmSpeedCameraDirection.parseMaxspeedKmH(camera.tags["maxspeed"]),
        )
    }

    private fun matchAndResolve(
        record: FranceRadarRecord,
        bundle: SpeedCameraOverpassBundle,
    ): OsmDirectionInfo? {
        val match = bestMatch(record.latitude, record.longitude, record.vma, bundle.cameras)
            ?: return null
        val (camera, distM) = match
        return resolveDirectionForCamera(camera, distM, bundle)
    }

    private suspend fun fetchBundle(records: List<FranceRadarRecord>): SpeedCameraOverpassBundle {
        val lats = records.map { it.latitude }
        val lons = records.map { it.longitude }
        val padLat = BBOX_PAD_KM / 111.0
        val midLat = (lats.minOrNull()!! + lats.maxOrNull()!!) / 2.0
        val padLon = BBOX_PAD_KM / (111.0 * cos(midLat * PI / 180.0)).coerceAtLeast(0.01)
        return overpass.querySpeedCamerasInBbox(
            minLat = lats.minOrNull()!! - padLat,
            minLon = lons.minOrNull()!! - padLon,
            maxLat = lats.maxOrNull()!! + padLat,
            maxLon = lons.maxOrNull()!! + padLon,
        )
    }

    private fun enforcementBearingForCamera(
        cameraId: Long,
        bundle: SpeedCameraOverpassBundle,
    ): Double? {
        for (rel in bundle.enforcementRelations) {
            val involvesCamera = rel.members.any { m ->
                m.type == "node" && m.ref == cameraId &&
                    (m.role.isEmpty() || m.role == "device" || m.role == "camera")
            } || rel.members.any { it.type == "node" && it.ref == cameraId }
            if (!involvesCamera) continue
            val from = rel.members.firstOrNull { it.role == "from" } ?: continue
            val to = rel.members.firstOrNull { it.role == "to" } ?: continue
            val fromNode = memberCoords(from, bundle) ?: continue
            val toNode = memberCoords(to, bundle) ?: continue
            return DangerZoneEvaluator.calculateBearing(
                fromNode.first, fromNode.second, toNode.first, toNode.second
            )
        }
        return null
    }

    private fun memberCoords(
        member: fr.geoking.gaston.api.overpass.OverpassRelationMember,
        bundle: SpeedCameraOverpassBundle,
    ): Pair<Double, Double>? {
        if (member.type != "node") return null
        val n = bundle.nodesById[member.ref] ?: return null
        return n.lat to n.lon
    }

    private fun nearestWayBearing(
        lat: Double,
        lon: Double,
        ways: List<OverpassWayGeom>,
    ): Double? {
        var best: Pair<Double, Double>? = null // distM to bearing
        for (way in ways) {
            val (clat, clon) = way.center()
            val distM = haversineKm(lat, lon, clat, clon) * 1000.0
            if (distM > matchRadiusMeters * 1.5) continue
            val bearing = way.bearingDegrees() ?: continue
            if (best == null || distM < best.first) {
                best = distM to bearing
            }
        }
        return best?.second
    }
}
