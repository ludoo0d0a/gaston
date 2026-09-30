package fr.geoking.gaston.aac

import fr.geoking.gaston.api.overpass.OverpassClient
import fr.geoking.gaston.api.overpass.OverpassElement
import fr.geoking.gaston.api.overpass.OverpassEnforcementRelation
import fr.geoking.gaston.api.overpass.OverpassRelationMember
import fr.geoking.gaston.api.overpass.SpeedCameraOverpassBundle
import fr.geoking.gaston.api.radars.FranceRadarRecord
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OsmSpeedCameraDirectionTest {

    @Test
    fun enforcementIsHighConfidence() {
        val info = OsmSpeedCameraDirection.resolve(
            directionTag = null,
            enforcementBearingDegrees = 90.0,
            osmNodeId = 1L,
            matchedDistanceMeters = 12.0,
        )
        assertEquals(DirectionConfidence.High, info.confidence)
        assertEquals(90.0, info.monitoredBearingDegrees!!, 0.01)
    }

    @Test
    fun forwardBackwardAlongWayIsHigh() {
        val forward = OsmSpeedCameraDirection.resolve(
            directionTag = "forward",
            wayBearingDegrees = 10.0,
        )
        assertEquals(DirectionConfidence.High, forward.confidence)
        assertEquals(10.0, forward.monitoredBearingDegrees!!, 0.01)

        val backward = OsmSpeedCameraDirection.resolve(
            directionTag = "backward",
            wayBearingDegrees = 10.0,
        )
        assertEquals(DirectionConfidence.High, backward.confidence)
        assertEquals(190.0, backward.monitoredBearingDegrees!!, 0.01)
    }

    @Test
    fun bothIsBidirectionalHigh() {
        val info = OsmSpeedCameraDirection.resolve(directionTag = "both")
        assertEquals(DirectionConfidence.High, info.confidence)
        assertTrue(info.bidirectional)
        assertNull(info.monitoredBearingDegrees)
    }

    @Test
    fun degreesAreLowConfidence() {
        val info = OsmSpeedCameraDirection.resolve(directionTag = "270")
        assertEquals(DirectionConfidence.Low, info.confidence)
        assertEquals(270.0, info.monitoredBearingDegrees!!, 0.01)
    }

    @Test
    fun lowConfidenceDoesNotFilterVehicle() {
        val low = OsmSpeedCameraDirection.resolve(directionTag = "N")
        assertEquals(DirectionConfidence.Low, low.confidence)
        assertTrue(OsmSpeedCameraDirection.isVehicleCompatible(180.0, low))
    }

    @Test
    fun highConfidenceFiltersOppositeSense() {
        val high = OsmSpeedCameraDirection.resolve(
            directionTag = null,
            enforcementBearingDegrees = 0.0,
        )
        assertTrue(OsmSpeedCameraDirection.isVehicleCompatible(10.0, high))
        assertFalse(OsmSpeedCameraDirection.isVehicleCompatible(180.0, high))
    }
}

class RadarOsmEnricherTest {

    @Test
    fun matchWithin40m() {
        val enricher = RadarOsmEnricher(FakeSpeedCameraOverpassClient(SpeedCameraOverpassBundle()))
        val cameras = listOf(
            OverpassElement(10, 48.8566, 2.3522, mapOf("highway" to "speed_camera", "maxspeed" to "130")),
            OverpassElement(11, 48.8700, 2.3522, mapOf("highway" to "speed_camera")),
        )
        val match = enricher.bestMatch(48.8566, 2.3522, vma = 130, cameras = cameras)
        assertNotNull(match)
        assertEquals(10L, match.first.id)
        assertTrue(match.second <= RadarOsmEnricher.DEFAULT_MATCH_RADIUS_METERS)
    }

    @Test
    fun noMatchBeyond40m() {
        val enricher = RadarOsmEnricher(FakeSpeedCameraOverpassClient(SpeedCameraOverpassBundle()))
        val cameras = listOf(
            OverpassElement(10, 48.8600, 2.3522, mapOf("highway" to "speed_camera")),
        )
        // ~380 m north of Paris center sample
        val match = enricher.bestMatch(48.8566, 2.3522, vma = null, cameras = cameras)
        assertNull(match)
    }

    @Test
    fun enrichUsesEnforcementBearing() = runBlocking {
        val camera = OverpassElement(100, 48.8566, 2.3522, mapOf("highway" to "speed_camera"))
        val from = OverpassElement(1, 48.8500, 2.3522, emptyMap())
        val to = OverpassElement(2, 48.8600, 2.3522, emptyMap())
        val bundle = SpeedCameraOverpassBundle(
            cameras = listOf(camera),
            nodesById = mapOf(100L to camera, 1L to from, 2L to to),
            enforcementRelations = listOf(
                OverpassEnforcementRelation(
                    id = 9,
                    tags = mapOf("type" to "enforcement"),
                    members = listOf(
                        OverpassRelationMember("node", 100, "device"),
                        OverpassRelationMember("node", 1, "from"),
                        OverpassRelationMember("node", 2, "to"),
                    ),
                )
            ),
        )
        val enricher = RadarOsmEnricher(FakeSpeedCameraOverpassClient(bundle))
        val zones = enricher.enrichToZones(
            listOf(FranceRadarRecord("r1", "FIXE", 110, 48.8566, 2.3522))
        )
        assertEquals(1, zones.size)
        assertEquals(DirectionConfidence.High, zones[0].directionConfidence)
        assertNotNull(zones[0].monitoredBearingDegrees)
        // from south to north ≈ 0°
        assertEquals(0.0, zones[0].monitoredBearingDegrees!!, 5.0)
    }

    @Test
    fun evaluatorDropsOppositeHighDirection() {
        val zone = DangerZoneFactory.fromSpeedControlPoint(
            id = "z",
            latitude = 48.8566,
            longitude = 2.3522,
            speedLimitKmH = 90,
            source = "test",
        ).withOsmDirection(
            OsmDirectionInfo(
                confidence = DirectionConfidence.High,
                monitoredBearingDegrees = 0.0, // northbound traffic controlled
            )
        )
        // Inside zone from the north, heading south into center — ahead, but opposite to monitored sense
        val evalOpposite = DangerZoneEvaluator.evaluate(
            vehLat = 48.8620,
            vehLon = 2.3522,
            vehSpeedKmH = 80.0,
            vehBearing = 180.0,
            zone = zone,
        )
        assertFalse(evalOpposite.isInside)

        // From the south, heading north — ahead and compatible with monitored sense
        val evalOk = DangerZoneEvaluator.evaluate(
            vehLat = 48.8500,
            vehLon = 2.3522,
            vehSpeedKmH = 80.0,
            vehBearing = 0.0,
            zone = zone,
        )
        assertTrue(evalOk.isInside)
    }

    @Test
    fun lowDirectionDoesNotDropAlert() {
        val zone = DangerZoneFactory.fromSpeedControlPoint(
            id = "z",
            latitude = 48.8566,
            longitude = 2.3522,
            speedLimitKmH = 90,
            source = "test",
        ).withOsmDirection(
            OsmDirectionInfo(
                confidence = DirectionConfidence.Low,
                monitoredBearingDegrees = 0.0,
            )
        )
        // Same opposite approach as High case — Low must NOT filter
        val eval = DangerZoneEvaluator.evaluate(
            vehLat = 48.8620,
            vehLon = 2.3522,
            vehSpeedKmH = 80.0,
            vehBearing = 180.0,
            zone = zone,
        )
        assertTrue(eval.isInside)
    }

    private class FakeSpeedCameraOverpassClient(
        private val bundle: SpeedCameraOverpassBundle,
    ) : OverpassClient(HttpClient()) {
        override suspend fun querySpeedCamerasInBbox(
            minLat: Double,
            minLon: Double,
            maxLat: Double,
            maxLon: Double,
            limit: Int,
        ): SpeedCameraOverpassBundle = bundle
    }
}

class RadarPoiMergerTest {

    @Test
    fun dropsOsmNearFranceRadars() {
        val france = Poi(
            id = "fr_radar_1",
            name = "Zone 90 km/h",
            address = "France",
            latitude = 48.8566,
            longitude = 2.3522,
            poiCategory = PoiCategory.Radar,
            source = "FranceRadars",
        )
        val osm = Poi(
            id = "osm:99",
            name = "Zone de danger",
            address = "",
            latitude = 48.8567,
            longitude = 2.3522,
            poiCategory = PoiCategory.Radar,
            source = "OpenStreetMap",
        )
        val parking = Poi(
            id = "osm:1",
            name = "P",
            address = "",
            latitude = 48.85,
            longitude = 2.35,
            poiCategory = PoiCategory.Parking,
            source = "OpenStreetMap",
        )
        val out = RadarPoiMerger.dedupeRadarAmenities(listOf(france, osm, parking))
        assertEquals(2, out.size)
        assertTrue(out.any { it.id == "fr_radar_1" })
        assertTrue(out.any { it.id == "osm:1" })
        assertFalse(out.any { it.id == "osm:99" })
    }
}
