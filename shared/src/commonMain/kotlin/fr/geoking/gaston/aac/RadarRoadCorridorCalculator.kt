package fr.geoking.gaston.aac

import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.road.RoadPolyline
import fr.geoking.gaston.shared.location.haversineKm
import kotlin.math.abs
import kotlin.math.atan2

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

    private const val BUFFER_HALF_WIDTH_METERS = 18.0 // ~36m total corridor width

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
}
