package fr.geoking.gaston.aac

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.road.RoadPolyline
import fr.geoking.gaston.shared.location.haversineKm
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.PI

data class RadarRoadCorridorPolygon(
    val poiId: String,
    val zoneRadiusMeters: Double,
    /** Fill ARGB (including alpha). */
    val fillArgb: Int = 0x330000FF.toInt(), // translucent blue for test layer
    /** Stroke ARGB. */
    val strokeArgb: Int = 0xFF0000FF.toInt(), // solid blue border
    /** Closed ring of (lat, lon) defining the corridor polygon around the road. */
    val points: List<Pair<Double, Double>>,
)

object RadarRoadCorridorCalculator {

    // ~70 m total: tolerates GPS error (10-20 m) and lane / carriageway offset from the OSM centerline.
    private const val BUFFER_HALF_WIDTH_METERS = 35.0

    /**
     * Calculates an upstream road corridor polygon along the drivable road geometries
     * in front of a radar POI.
     *
     * @param poi Radar POI
     * @param roadPolylines Nearby vector road polylines (from RoadGeometryRepository)
     * @return RadarRoadCorridorPolygon or null if not a radar / no suitable road polylines
     */
    fun calculateCorridor(
        poi: Poi,
        roadPolylines: List<RoadPolyline>,
    ): RadarRoadCorridorPolygon? {
        if (poi.poiCategory != PoiCategory.Radar) return null
        val zoneRadiusM = DangerZoneDistances.radiusMetersForRadarPoiVma(poi.rawSourceData?.get("vma"))
        if (zoneRadiusM <= 0.0) return null

        val monitoredBearing = DangerZoneTriangle.axisBearingDegrees(poi)
        val isBidi = DangerZoneTriangle.isBidirectional(poi)

        // Select best matching road polyline
        var bestRoad: RoadPolyline? = null
        var minRadarDistM = Double.MAX_VALUE

        for (road in roadPolylines) {
            if (road.points.size < 2) continue
            for (pt in road.points) {
                val dM = haversineKm(poi.latitude, poi.longitude, pt.first, pt.second) * 1000.0
                if (dM < minRadarDistM) {
                    minRadarDistM = dM
                    bestRoad = road
                }
            }
        }

        val orderedPoints = mutableListOf<Pair<Double, Double>>()

        if (bestRoad != null && minRadarDistM <= 150.0) {
            // Filter points along the polyline preserve natural vertex order
            var points = bestRoad.points
            if (points.size >= 2) {
                // Direction check: ensure points flow from radar upstream or along travel direction
                val pStart = points.first()
                val pEnd = points.last()
                val dStart = haversineKm(poi.latitude, poi.longitude, pStart.first, pStart.second)
                val dEnd = haversineKm(poi.latitude, poi.longitude, pEnd.first, pEnd.second)
                if (dStart > dEnd) {
                    points = points.reversed()
                }
                for (pt in points) {
                    val distM = haversineKm(poi.latitude, poi.longitude, pt.first, pt.second) * 1000.0
                    if (distM <= zoneRadiusM) {
                        if (orderedPoints.isEmpty() || haversineKm(orderedPoints.last().first, orderedPoints.last().second, pt.first, pt.second) > 0.002) {
                            orderedPoints.add(pt)
                        }
                    }
                }
            }
        }

        if (orderedPoints.size < 2) {
            // Fallback: synthetic line along approach bearing
            val fallbackBearing = monitoredBearing ?: 0.0
            val approachFrom = OsmSpeedCameraDirection.normalizeBearing(fallbackBearing + 180.0)
            val pEnd = DangerZoneTriangle.offsetMeters(poi.latitude, poi.longitude, approachFrom, zoneRadiusM)
            orderedPoints.add(poi.latitude to poi.longitude)
            orderedPoints.add(pEnd)
        }

        // Build left and right offset lines to construct the corridor polygon
        val leftLine = mutableListOf<Pair<Double, Double>>()
        val rightLine = mutableListOf<Pair<Double, Double>>()

        for (i in 0 until orderedPoints.size - 1) {
            val p1 = orderedPoints[i]
            val p2 = orderedPoints[i + 1]
            val segmentBearing = DangerZoneEvaluator.calculateBearing(p1.first, p1.second, p2.first, p2.second)
            val leftBearing = OsmSpeedCameraDirection.normalizeBearing(segmentBearing - 90.0)
            val rightBearing = OsmSpeedCameraDirection.normalizeBearing(segmentBearing + 90.0)

            val leftP1 = DangerZoneTriangle.offsetMeters(p1.first, p1.second, leftBearing, BUFFER_HALF_WIDTH_METERS)
            val rightP1 = DangerZoneTriangle.offsetMeters(p1.first, p1.second, rightBearing, BUFFER_HALF_WIDTH_METERS)
            val leftP2 = DangerZoneTriangle.offsetMeters(p2.first, p2.second, leftBearing, BUFFER_HALF_WIDTH_METERS)
            val rightP2 = DangerZoneTriangle.offsetMeters(p2.first, p2.second, rightBearing, BUFFER_HALF_WIDTH_METERS)

            if (leftLine.isEmpty()) leftLine.add(leftP1)
            leftLine.add(leftP2)

            if (rightLine.isEmpty()) rightLine.add(rightP1)
            rightLine.add(rightP2)
        }

        // Polygon ring = leftLine forward + rightLine backward + close
        val polygonPoints = mutableListOf<Pair<Double, Double>>()
        polygonPoints.addAll(leftLine)
        polygonPoints.addAll(rightLine.reversed())
        if (polygonPoints.first() != polygonPoints.last()) {
            polygonPoints.add(polygonPoints.first())
        }

        return RadarRoadCorridorPolygon(
            poiId = poi.id,
            zoneRadiusMeters = zoneRadiusM,
            fillArgb = 0x442563EB.toInt(), // translucent vivid blue (AA/Tel concept validation)
            strokeArgb = 0xFF1D4ED8.toInt(), // strong border blue
            points = polygonPoints,
        )
    }

    /** Max distance between the radar and the road it is snapped on. */
    const val SNAP_MAX_METERS = 80.0

    /** Max gap between two OSM ways for them to be chained into one continuous road. */
    private const val CHAIN_GAP_METERS = 8.0

    /** Corridor half-width (m) used for the OSM road-following zone geometry. */
    const val ROAD_CORRIDOR_HALF_WIDTH_METERS = BUFFER_HALF_WIDTH_METERS

    /**
     * Road-following polygons (one per approach direction) for a radar [Poi]; empty when no OSM
     * road is close enough. Same geometry as [DangerZone.corridorRings].
     */
    fun calculateCorridors(poi: Poi, roadPolylines: List<RoadPolyline>): List<RadarRoadCorridorPolygon> {
        if (poi.poiCategory != PoiCategory.Radar) return emptyList()
        val zoneRadiusM = DangerZoneDistances.radiusMetersForRadarPoiVma(poi.rawSourceData?.get("vma"))
        return corridorRings(
            tipLat = poi.latitude,
            tipLon = poi.longitude,
            radiusMeters = zoneRadiusM,
            monitoredBearingDegrees = DangerZoneTriangle.axisBearingDegrees(poi),
            bidirectional = DangerZoneTriangle.isBidirectional(poi),
            roads = roadPolylines,
        ).map {
            RadarRoadCorridorPolygon(
                poiId = poi.id,
                zoneRadiusMeters = zoneRadiusM,
                fillArgb = 0x442563EB.toInt(),
                strokeArgb = 0xFF1D4ED8.toInt(),
                points = it,
            )
        }
    }

    /**
     * Rings following the real OSM road from the radar over [radiusMeters] **measured along the
     * road**, on the approach side(s): `monitoredBearing + 180°` (both road directions when the
     * direction is unknown). Ways are chained across junction splits.
     */
    fun corridorRings(
        tipLat: Double,
        tipLon: Double,
        radiusMeters: Double,
        monitoredBearingDegrees: Double?,
        bidirectional: Boolean,
        roads: List<RoadPolyline>,
        /**
         * When true (default), falls back to a straight approach ribbon if OSM roads are missing
         * or cannot be snapped — so phone / AA still show a blue corridor near the radar.
         */
        allowSynthetic: Boolean = true,
    ): List<List<Pair<Double, Double>>> {
        if (radiusMeters <= 0.0) return emptyList()

        if (roads.isNotEmpty()) {
            val snap = snapToRoad(tipLat, tipLon, roads)
            if (snap != null) {
                val forward = walk(snap, forward = true, radiusMeters, roads)
                val backward = walk(snap, forward = false, radiusMeters, roads)
                val candidates = listOf(forward, backward).filter { it.size >= 2 }
                if (candidates.isNotEmpty()) {
                    val bearings = DangerZoneTriangle.approachBearings(monitoredBearingDegrees, bidirectional)
                    val chosen = LinkedHashSet<List<Pair<Double, Double>>>()
                    for (b in bearings) {
                        if (b == null) {
                            chosen.addAll(candidates)
                            continue
                        }
                        val approachFrom = OsmSpeedCameraDirection.normalizeBearing(b + 180.0)
                        val best = candidates.minByOrNull { angleTo(it, approachFrom) } ?: continue
                        if (angleTo(best, approachFrom) <= 90.0) chosen.add(best)
                    }
                    if (chosen.isNotEmpty()) {
                        return chosen.map { bufferPath(it, BUFFER_HALF_WIDTH_METERS) }
                    }
                }
            }
        }

        if (!allowSynthetic) return emptyList()
        return syntheticCorridorRings(
            tipLat = tipLat,
            tipLon = tipLon,
            radiusMeters = radiusMeters,
            monitoredBearingDegrees = monitoredBearingDegrees,
            bidirectional = bidirectional,
        )
    }

    /**
     * Straight ±[BUFFER_HALF_WIDTH_METERS] ribbon along the approach bearing(s). Used when OSM
     * road geometry is unavailable so phone / AA still show a blue corridor near the radar.
     */
    fun syntheticCorridorRings(
        tipLat: Double,
        tipLon: Double,
        radiusMeters: Double,
        monitoredBearingDegrees: Double?,
        bidirectional: Boolean,
    ): List<List<Pair<Double, Double>>> {
        if (radiusMeters <= 0.0) return emptyList()
        val bearings = DangerZoneTriangle.approachBearings(monitoredBearingDegrees, bidirectional)
        return bearings.map { bearing ->
            val axis = bearing ?: 0.0
            val approachFrom = OsmSpeedCameraDirection.normalizeBearing(axis + 180.0)
            val end = DangerZoneTriangle.offsetMeters(tipLat, tipLon, approachFrom, radiusMeters)
            bufferPath(listOf(tipLat to tipLon, end), BUFFER_HALF_WIDTH_METERS)
        }
    }

    private data class Snap(
        val road: RoadPolyline,
        val segIndex: Int, // segment [segIndex, segIndex + 1]
        val point: Pair<Double, Double>,
    )

    private fun angleTo(path: List<Pair<Double, Double>>, bearing: Double): Double {
        val start = path.first()
        // Heading of the first ~40 m of the path (stable against tiny first segments).
        val ref = path.firstOrNull {
            haversineKm(start.first, start.second, it.first, it.second) * 1000.0 >= 40.0
        } ?: path.last()
        val h = DangerZoneEvaluator.calculateBearing(start.first, start.second, ref.first, ref.second)
        return DangerZoneEvaluator.angleDifference(h, bearing)
    }

    private fun snapToRoad(lat: Double, lon: Double, roads: List<RoadPolyline>): Snap? {
        val kx = 111_320.0 * cos(lat * PI / 180.0)
        val ky = 110_540.0
        var best: Snap? = null
        var bestDist = Double.MAX_VALUE
        for (road in roads) {
            for (i in 0 until road.points.size - 1) {
                val a = road.points[i]
                val b = road.points[i + 1]
                val ax = (a.second - lon) * kx
                val ay = (a.first - lat) * ky
                val bx = (b.second - lon) * kx
                val by = (b.first - lat) * ky
                val dx = bx - ax
                val dy = by - ay
                val len2 = dx * dx + dy * dy
                val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
                val d = hypot(ax + t * dx - 0.0, ay + t * dy - 0.0)
                if (d < bestDist) {
                    bestDist = d
                    best = Snap(
                        road,
                        i,
                        (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t),
                    )
                }
            }
        }
        return best?.takeIf { bestDist <= SNAP_MAX_METERS }
    }

    /** Walks [radiusMeters] along the road from [snap], chaining connected ways. */
    private fun walk(
        snap: Snap,
        forward: Boolean,
        radiusMeters: Double,
        roads: List<RoadPolyline>,
    ): List<Pair<Double, Double>> {
        val out = mutableListOf(snap.point)
        var remaining = radiusMeters
        val used = mutableSetOf(snap.road.id)
        var vertices = if (forward) {
            snap.road.points.drop(snap.segIndex + 1)
        } else {
            snap.road.points.take(snap.segIndex + 1).reversed()
        }
        while (true) {
            for (v in vertices) {
                val last = out.last()
                val seg = haversineKm(last.first, last.second, v.first, v.second) * 1000.0
                if (seg < 0.5) continue
                if (seg >= remaining) {
                    val f = remaining / seg
                    out.add(
                        (last.first + (v.first - last.first) * f) to
                            (last.second + (v.second - last.second) * f),
                    )
                    return out
                }
                out.add(v)
                remaining -= seg
            }
            // Way exhausted: continue on an unused way starting/ending at this point.
            val end = out.last()
            var next: List<Pair<Double, Double>>? = null
            var nextId = 0L
            var nextGap = Double.MAX_VALUE
            for (r in roads) {
                if (r.id in used || r.points.size < 2) continue
                for (reversed in listOf(false, true)) {
                    val pts = if (reversed) r.points.reversed() else r.points
                    val gap = haversineKm(end.first, end.second, pts.first().first, pts.first().second) * 1000.0
                    if (gap <= CHAIN_GAP_METERS && gap < nextGap) {
                        next = pts
                        nextId = r.id
                        nextGap = gap
                    }
                }
            }
            val n = next ?: return out
            used.add(nextId)
            vertices = n
        }
    }

    /** Closed ribbon of ±[halfWidth] m around [path]. */
    private fun bufferPath(
        path: List<Pair<Double, Double>>,
        halfWidth: Double,
    ): List<Pair<Double, Double>> {
        val left = mutableListOf<Pair<Double, Double>>()
        val right = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until path.size - 1) {
            val p1 = path[i]
            val p2 = path[i + 1]
            val seg = DangerZoneEvaluator.calculateBearing(p1.first, p1.second, p2.first, p2.second)
            val lb = OsmSpeedCameraDirection.normalizeBearing(seg - 90.0)
            val rb = OsmSpeedCameraDirection.normalizeBearing(seg + 90.0)
            if (left.isEmpty()) {
                left.add(DangerZoneTriangle.offsetMeters(p1.first, p1.second, lb, halfWidth))
                right.add(DangerZoneTriangle.offsetMeters(p1.first, p1.second, rb, halfWidth))
            }
            left.add(DangerZoneTriangle.offsetMeters(p2.first, p2.second, lb, halfWidth))
            right.add(DangerZoneTriangle.offsetMeters(p2.first, p2.second, rb, halfWidth))
        }
        val ring = left + right.reversed()
        return ring + ring.first()
    }
}
