package fr.geoking.gaston.radar

import android.location.Location
import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.poi.Poi
import fr.geoking.gaston.poi.PoiCategory
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RadarAlertTest {

    private class MockAudioNotifier : RadarAudioNotifier {
        var okBeepsCount = 0
        var overSpeedCount = 0
        var lastSpokenSpeedLimit: Int? = null

        override fun playOkSpeedBeeps() {
            okBeepsCount++
        }

        override fun playOverSpeedBeepsAndSpeak(speedLimitKmH: Int?) {
            overSpeedCount++
            lastSpokenSpeedLimit = speedLimitKmH
        }

        override fun shutdown() {}
    }

    private lateinit var settingsManager: SettingsManager
    private lateinit var audioNotifier: MockAudioNotifier
    private lateinit var alertManager: RadarAlertManager

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        settingsManager = SettingsManager(context)
        settingsManager.setRadarWarningEnabled(true)
        settingsManager.setRadarWarningDistanceMeters(1000)

        audioNotifier = MockAudioNotifier()
        alertManager = RadarAlertManager(settingsManager, audioNotifier)
    }

    @Test
    fun testBearingAndAngleDifference() {
        // Paris to North point
        val bearingNorth = RadarTrajectoryHelper.calculateBearing(48.8566, 2.3522, 48.9566, 2.3522)
        assertEquals(0.0, bearingNorth, 1.0)

        // Angle difference between North (0°) and East (90°) is 90°
        val diff1 = RadarTrajectoryHelper.angleDifference(0.0, 90.0)
        assertEquals(90.0, diff1, 0.001)

        // Angle difference between 10° and 350° is 20°
        val diff2 = RadarTrajectoryHelper.angleDifference(10.0, 350.0)
        assertEquals(20.0, diff2, 0.001)
    }

    @Test
    fun testExtractSpeedLimitKmH() {
        val poi1 = Poi(
            id = "r1",
            name = "Radar 130 km/h",
            address = "France",
            latitude = 48.8566,
            longitude = 2.3522,
            poiCategory = PoiCategory.Radar,
            rawSourceData = mapOf("vma" to "130")
        )
        assertEquals(130, RadarTrajectoryHelper.extractSpeedLimitKmH(poi1))

        val poi2 = Poi(
            id = "r2",
            name = "Radar (ETD)",
            address = "France",
            latitude = 48.8566,
            longitude = 2.3522,
            poiCategory = PoiCategory.Radar,
            rawSourceData = mapOf("vma" to "NA")
        )
        assertEquals(null, RadarTrajectoryHelper.extractSpeedLimitKmH(poi2))
    }

    @Test
    fun testEvaluateRadarOverspeedAndNormalSpeed() {
        val radarPoi = Poi(
            id = "r_paris",
            name = "Radar 80 km/h",
            address = "Paris",
            latitude = 48.8600,
            longitude = 2.3522,
            poiCategory = PoiCategory.Radar,
            rawSourceData = mapOf("vma" to "80")
        )

        // Vehicle heading North (0° bearing) towards radar at 48.8500 (radar is at 48.8600)
        val vehLat = 48.8550
        val vehLon = 2.3522
        val vehBearing = 0.0

        // Driving at 70 km/h (speed OK <= 80)
        val evalOk = RadarTrajectoryHelper.evaluateRadar(
            vehLat = vehLat,
            vehLon = vehLon,
            vehSpeedKmH = 70.0,
            vehBearing = vehBearing,
            radar = radarPoi,
            warningDistanceMeters = 1000
        )

        assertTrue(evalOk.isAhead)
        assertTrue(evalOk.isWithinWarningDistance)
        assertFalse(evalOk.isOverspeed)

        // Driving at 95 km/h (overspeed > 80)
        val evalOverspeed = RadarTrajectoryHelper.evaluateRadar(
            vehLat = vehLat,
            vehLon = vehLon,
            vehSpeedKmH = 95.0,
            vehBearing = vehBearing,
            radar = radarPoi,
            warningDistanceMeters = 1000
        )

        assertTrue(evalOverspeed.isAhead)
        assertTrue(evalOverspeed.isWithinWarningDistance)
        assertTrue(evalOverspeed.isOverspeed)
    }

    @Test
    fun testRadarAlertManagerDeduplication() {
        val radarPoi = Poi(
            id = "r_100",
            name = "Radar 90 km/h",
            address = "A1",
            latitude = 48.8600,
            longitude = 2.3522,
            poiCategory = PoiCategory.Radar,
            rawSourceData = mapOf("vma" to "90")
        )

        val loc = Location("test_provider").apply {
            latitude = 48.8550
            longitude = 2.3522
            speed = 30.0f // 108 km/h -> overspeed for 90 km/h
            bearing = 0.0f
            time = System.currentTimeMillis()
        }

        // First pass: triggers overspeed alert
        alertManager.evaluateAndAlert(loc, listOf(radarPoi))
        assertEquals(1, audioNotifier.overSpeedCount)
        assertEquals(90, audioNotifier.lastSpokenSpeedLimit)
        assertTrue("r_100" in alertManager.getAlertedRadarIds())

        // Second pass at same location: should NOT trigger again (deduplicated)
        alertManager.evaluateAndAlert(loc, listOf(radarPoi))
        assertEquals(1, audioNotifier.overSpeedCount)
    }
}
