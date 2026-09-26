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
        // Alert phrasing helpers must never embed lat/lon or "radar + distance"
        val phrase = DangerZoneAlertCopy.frZoneEntry(speedLimitKmH = 110)
        assertFalse(phrase.contains("radar", ignoreCase = true))
        assertFalse(phrase.contains("contrôle", ignoreCase = true))
        assertTrue(phrase.contains("zone de danger", ignoreCase = true))
        assertTrue(phrase.contains("110"))
    }

    @Test
    fun csvTypeMapping() {
        assertEquals(DangerZoneKind.HeightenedVigilance, DangerZoneFactory.mapCsvTypeToKind("FEU ROUGE"))
        assertEquals(DangerZoneKind.SpeedControlArea, DangerZoneFactory.mapCsvTypeToKind("FIXE"))
        assertEquals(DangerZoneKind.SpeedControlArea, DangerZoneFactory.mapCsvTypeToKind("ETD"))
    }
}
