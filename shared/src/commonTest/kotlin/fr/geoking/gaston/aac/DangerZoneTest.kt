package fr.geoking.gaston.aac

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DangerZoneTest {

    @Test
    fun distancesByRoadClass() {
        assertEquals(4_000.0, DangerZoneDistances.radiusMetersFor(RoadNetworkClass.Motorway))
        assertEquals(2_000.0, DangerZoneDistances.radiusMetersFor(RoadNetworkClass.ExtraUrban))
        assertEquals(300.0, DangerZoneDistances.radiusMetersFor(RoadNetworkClass.Urban))
    }

    @Test
    fun radiusMetersForRadarPoiVma() {
        assertEquals(4_000.0, DangerZoneDistances.radiusMetersForRadarPoiVma("130"))
        assertEquals(4_000.0, DangerZoneDistances.radiusMetersForRadarPoiVma("110"))
        assertEquals(2_000.0, DangerZoneDistances.radiusMetersForRadarPoiVma("90"))
        assertEquals(2_000.0, DangerZoneDistances.radiusMetersForRadarPoiVma("70"))
        assertEquals(300.0, DangerZoneDistances.radiusMetersForRadarPoiVma("50"))
        assertEquals(300.0, DangerZoneDistances.radiusMetersForRadarPoiVma("NA"))
        assertEquals(300.0, DangerZoneDistances.radiusMetersForRadarPoiVma(null))
    }

    @Test
    fun classifyFromVmaHeuristic() {
        assertEquals(RoadNetworkClass.Motorway, DangerZoneDistances.classifyFromSpeedLimitKmH(130))
        assertEquals(RoadNetworkClass.Motorway, DangerZoneDistances.classifyFromSpeedLimitKmH(110))
        assertEquals(RoadNetworkClass.ExtraUrban, DangerZoneDistances.classifyFromSpeedLimitKmH(90))
        assertEquals(RoadNetworkClass.ExtraUrban, DangerZoneDistances.classifyFromSpeedLimitKmH(70))
        assertEquals(RoadNetworkClass.Urban, DangerZoneDistances.classifyFromSpeedLimitKmH(50))
        assertEquals(RoadNetworkClass.Urban, DangerZoneDistances.classifyFromSpeedLimitKmH(null))
    }

    @Test
    fun fromOsmHighwayMapping() {
        assertEquals(RoadNetworkClass.Motorway, DangerZoneDistances.fromOsmHighway("motorway"))
        assertEquals(RoadNetworkClass.Motorway, DangerZoneDistances.fromOsmHighway("motorway_link"))
        assertEquals(RoadNetworkClass.ExtraUrban, DangerZoneDistances.fromOsmHighway("trunk"))
        assertEquals(RoadNetworkClass.ExtraUrban, DangerZoneDistances.fromOsmHighway("primary_link"))
        assertEquals(RoadNetworkClass.Urban, DangerZoneDistances.fromOsmHighway("residential"))
        assertEquals(null, DangerZoneDistances.fromOsmHighway(null))
        assertEquals(null, DangerZoneDistances.fromOsmHighway("  "))
        assertTrue(DangerZoneDistances.osmHighwayRank("motorway") > DangerZoneDistances.osmHighwayRank("trunk"))
        assertTrue(DangerZoneDistances.osmHighwayRank("trunk") > DangerZoneDistances.osmHighwayRank("residential"))
    }

    @Test
    fun thoroughfareHeuristic() {
        assertTrue(ThoroughfareHighwayHeuristic.isLikelyHighway("Autoroute A7"))
        assertTrue(ThoroughfareHighwayHeuristic.isLikelyHighway("A104"))
        assertTrue(ThoroughfareHighwayHeuristic.isLikelyHighway("I-95"))
        assertFalse(ThoroughfareHighwayHeuristic.isLikelyHighway("Rue de Rivoli"))
        assertFalse(ThoroughfareHighwayHeuristic.isLikelyHighway(null))
    }

    @Test
    fun speedControlPointBecomesExtendedZoneNotPinAlert() {
        val zone = DangerZoneFactory.fromSpeedControlPoint(
            id = "fr_dz_1",
            latitude = 48.8566,
            longitude = 2.3522,
            speedLimitKmH = 130,
            source = "FranceRadars",
            csvType = "FIXE",
        )
        assertEquals(DangerZoneKind.SpeedControlArea, zone.kind)
        assertEquals(RoadNetworkClass.Motorway, zone.roadClass)
        assertEquals(4_000.0, zone.radiusMeters)
        assertEquals(130, zone.speedLimitKmH)
        // Zone contains points far from the source pin (extended buffer)
        assertTrue(zone.contains(48.8566, 2.3522))
        // ~2 km north still inside motorway zone
        assertTrue(zone.contains(48.8746, 2.3522))
        // ~5 km north outside
        assertFalse(zone.contains(48.9016, 2.3522))
    }

    @Test
    fun urbanZoneIsTight() {
        val zone = DangerZoneFactory.fromSpeedControlPoint(
            id = "u1",
            latitude = 48.8566,
            longitude = 2.3522,
            speedLimitKmH = 50,
            source = "FranceRadars",
            csvType = "FIXE",
        )
        assertEquals(300.0, zone.radiusMeters)
        assertTrue(zone.contains(48.8566, 2.3522))
        // ~500 m away should be outside urban buffer
        assertFalse(zone.contains(48.8611, 2.3522))
    }

    @Test
    fun alertOnPresenceInZoneNotDistanceToPinAlone() {
        val zone = DangerZoneFactory.fromSpeedControlPoint(
            id = "z1",
            latitude = 48.8566,
            longitude = 2.3522,
            speedLimitKmH = 90,
            source = "test",
        )
        // Vehicle well inside ExtraUrban 2 km zone but > user-style 300 m from center
        val evalInside = DangerZoneEvaluator.evaluate(
            vehLat = 48.8650,
            vehLon = 2.3522,
            vehSpeedKmH = 80.0,
            vehBearing = null,
            zone = zone,
        )
        assertTrue(evalInside.isInside)
        assertTrue(evalInside.distanceToCenterMeters > 300.0)

        val evalOutside = DangerZoneEvaluator.evaluate(
            vehLat = 48.9000,
            vehLon = 2.3522,
            vehSpeedKmH = 80.0,
            vehBearing = null,
            zone = zone,
        )
        assertFalse(evalOutside.isInside)
    }

    @Test
    fun noControlCoordinateLeakInPublicAlertCopyHelpers() {
        // Alert phrasing helpers must never embed lat/lon or distance
        val phrase = DangerZoneAlertCopy.frZoneEntry(speedLimitKmH = 110)
        assertTrue(phrase.contains("radar", ignoreCase = true))
        assertFalse(phrase.contains("contrôle", ignoreCase = true))
        assertTrue(phrase.contains("110"))
    }

    @Test
    fun csvTypeMapping() {
        assertEquals(DangerZoneKind.HeightenedVigilance, DangerZoneFactory.mapCsvTypeToKind("FEU ROUGE"))
        assertEquals(DangerZoneKind.SpeedControlArea, DangerZoneFactory.mapCsvTypeToKind("FIXE"))
        assertEquals(DangerZoneKind.SpeedControlArea, DangerZoneFactory.mapCsvTypeToKind("ETD"))
    }

    @Test
    fun withoutBearingUsesCircle() {
        val zone = DangerZoneFactory.fromSpeedControlPoint(
            id = "circle1",
            latitude = 48.8566,
            longitude = 2.3522,
            speedLimitKmH = 90,
            source = "test",
        )
        assertFalse(zone.usesApproachTrapezoid)
        // ~1 km north: inside ExtraUrban circle, no bearing → circle alert
        val eval = DangerZoneEvaluator.evaluate(
            vehLat = 48.8656,
            vehLon = 2.3522,
            vehSpeedKmH = 80.0,
            vehBearing = null,
            zone = zone,
        )
        assertTrue(eval.isInside)
    }

    @Test
    fun withBearingUsesTrapezoidNotCircle() {
        val tipLat = 48.8566
        val tipLon = 2.3522
        val zone = DangerZoneFactory.fromSpeedControlPoint(
            id = "trap1",
            latitude = tipLat,
            longitude = tipLon,
            speedLimitKmH = 90,
            source = "test",
        ).copy(monitoredBearingDegrees = 0.0) // northbound → corridor south
        assertTrue(zone.usesApproachTrapezoid)

        // On approach axis ~1 km south → inside trapezoid
        val onAxis = DangerZoneTriangle.offsetMeters(tipLat, tipLon, 180.0, 1_000.0)
        val evalApproach = DangerZoneEvaluator.evaluate(
            vehLat = onAxis.first,
            vehLon = onAxis.second,
            vehSpeedKmH = 80.0,
            vehBearing = 0.0,
            zone = zone,
        )
        assertTrue(evalApproach.isInside)

        // North of tip (behind radar): inside AFFTAC circle but outside trapezoid
        val behind = DangerZoneTriangle.offsetMeters(tipLat, tipLon, 0.0, 500.0)
        assertTrue(behind.first.let { haversineFromTip(tipLat, tipLon, it, behind.second) } < zone.radiusMeters)
        val evalBehind = DangerZoneEvaluator.evaluate(
            vehLat = behind.first,
            vehLon = behind.second,
            vehSpeedKmH = 80.0,
            vehBearing = 180.0,
            zone = zone,
        )
        assertFalse(evalBehind.isInside)

        // Far lateral at mid-approach: inside circle radius, outside trapezoid
        val mid = DangerZoneTriangle.offsetMeters(tipLat, tipLon, 180.0, 1_000.0)
        val side = DangerZoneTriangle.offsetMeters(mid.first, mid.second, 90.0, 400.0)
        val evalSide = DangerZoneEvaluator.evaluate(
            vehLat = side.first,
            vehLon = side.second,
            vehSpeedKmH = 80.0,
            vehBearing = 0.0,
            zone = zone,
        )
        assertFalse(evalSide.isInside)
    }

    private fun haversineFromTip(tipLat: Double, tipLon: Double, lat: Double, lon: Double): Double {
        return fr.geoking.gaston.shared.location.haversineKm(tipLat, tipLon, lat, lon) * 1000.0
    }
}
