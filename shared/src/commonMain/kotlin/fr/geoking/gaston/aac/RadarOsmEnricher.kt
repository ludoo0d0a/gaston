package fr.geoking.gaston.aac

import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.api.overpass.OverpassElement
import fr.geoking.gaston.api.overpass.OverpassEnforcementRelation
import fr.geoking.gaston.api.overpass.OverpassWayGeom
import fr.geoking.gaston.api.overpass.SpeedCameraOverpassBundle
import fr.geoking.gaston.api.radars.FranceRadarRecord
import fr.geoking.gaston.api.radars.toDangerZone
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.shared.location.haversineKm
import kotlin.math.cos
import kotlin.math.PI

/**
 * Matches Lufop / Luxembourg / data.gouv radar anchors to nearby OSM speed_camera nodes and
 * resolves monitored traffic direction (enforcement / forward-backward / both).
 */
class RadarOsmEnricher(
    private val overpass: OverpassClient,
    private val matchRadiusMeters: Double = DEFAULT_MATCH_RADIUS_METERS,
) {
    companion object {
        const val DEFAULT_MATCH_RADIUS_METERS = 40.0
        private const val BBOX_PAD_KM = 0.05

        /** Anchor sources that lack native direction and need OSM enrich for map triangles. */
        val ANCHOR_SOURCES: Set<String> = setOf(
            "LufopOpenSpeedCam",
            "LuxembourgRadars",
            "FranceRadars",
        )

        fun alreadyHasDirection(poi: Poi): Boolean {
            val raw = poi.rawSourceData ?: return false
            return !raw[DangerZoneTriangle.RAW_DIRECTION].isNullOrBlank() ||
                !raw[DangerZoneTriangle.RAW_MONITORED_BEARING].isNullOrBlank() ||
                raw[DangerZoneTriangle.RAW_BIDIRECTIONAL]?.equals("true", ignoreCase = true) == true
        }

        fun applyDirectionToPoi(poi: Poi, info: OsmDirectionInfo): Poi {
            if (info.confidence == DirectionConfidence.None) return poi
            val raw = (poi.rawSourceData ?: emptyMap()).toMutableMap()
            info.osmNodeId?.let { raw["osm_node_id"] = it.toString() }
            info.matchedDistanceMeters?.let { raw["osm_match_m"] = it.toString() }
            if (info.bidirectional) {
                raw[DangerZoneTriangle.RAW_BIDIRECTIONAL] = "true"
                raw[DangerZoneTriangle.RAW_DIRECTION] = "both"
            } else {
                info.monitoredBearingDegrees?.let { bearing ->
                    raw[DangerZoneTriangle.RAW_MONITORED_BEARING] = bearing.toString()
                }
                // Keep a tag for diagnostics; triangles prefer monitored_bearing.
                when {
                    info.monitoredBearingDegrees != null ->
                        raw.putIfAbsent(
                            DangerZoneTriangle.RAW_DIRECTION,
                            info.monitoredBearingDegrees.toString(),
                        )
                }
            }
            return poi.copy(rawSourceData = raw)
        }
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
            fetchBundleForPoints(records.map { it.latitude to it.longitude })
        } catch (_: Exception) {
            return records.map { rec ->
                rec.toDangerZone(roadClassOverride = roadClassOverrides[rec.id])
            }
        }
        return records.map { record ->
            val base = record.toDangerZone(roadClassOverride = roadClassOverrides[record.id])
            val info = matchAndResolve(record.latitude, record.longitude, record.vma, bundle)
                ?: return@map base
            base.withOsmDirection(info)
        }
    }

    /**
     * Enrich Lufop / Luxembourg / FranceRadars map pins with OSM direction in [Poi.rawSourceData]
     * so [DangerZoneTriangle] can draw approach corridors. Skips pins that already have direction.
     * Overpass failures leave POIs unchanged.
     */
    suspend fun enrichPois(pois: List<Poi>): List<Poi> {
        val indices = pois.mapIndexedNotNull { index, poi ->
            if (poi.poiCategory != PoiCategory.Radar) return@mapIndexedNotNull null
            if (poi.source !in ANCHOR_SOURCES) return@mapIndexedNotNull null
            if (alreadyHasDirection(poi)) return@mapIndexedNotNull null
            index
        }
        if (indices.isEmpty()) return pois

        val points = indices.map { i -> pois[i].latitude to pois[i].longitude }
        val bundle = try {
            fetchBundleForPoints(points)
        } catch (_: Exception) {
            return pois
        }

        val out = pois.toMutableList()
        for (i in indices) {
            val poi = out[i]
            val vma = poi.rawSourceData?.get("vma")?.toIntOrNull()
            val info = matchAndResolve(poi.latitude, poi.longitude, vma, bundle) ?: continue
            out[i] = applyDirectionToPoi(poi, info)
        }
        return out
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
        latitude: Double,
        longitude: Double,
        vma: Int?,
        bundle: SpeedCameraOverpassBundle,
    ): OsmDirectionInfo? {
        val match = bestMatch(latitude, longitude, vma, bundle.cameras) ?: return null
        val (camera, distM) = match
        return resolveDirectionForCamera(camera, distM, bundle)
    }

    private suspend fun fetchBundleForPoints(
        points: List<Pair<Double, Double>>,
    ): SpeedCameraOverpassBundle {
        require(points.isNotEmpty())
        val lats = points.map { it.first }
        val lons = points.map { it.second }
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
