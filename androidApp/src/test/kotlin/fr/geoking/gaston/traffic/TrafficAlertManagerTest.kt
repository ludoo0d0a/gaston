package fr.geoking.gaston.traffic

import android.location.Location
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.api.traffic.TrafficEvent
import fr.geoking.gaston.api.traffic.TrafficSeverity
import fr.geoking.gaston.radar.RadarAudioNotifier
import fr.geoking.gaston.shared.location.haversineKm
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrafficAlertManagerTest {

    private class MockAudioNotifier : RadarAudioNotifier {
        var okBeepsCount = 0
        val spokenPhrases = mutableListOf<String>()

        override fun playOkSpeedBeeps(distanceMeters: Double?) {
            okBeepsCount++
        }

        override fun playOverSpeedBeepsAndSpeak(
            speedLimitKmH: Int?,
            distanceMeters: Double?,
            isDevMode: Boolean,
        ) = Unit

        override fun speakDangerZone(
            speedLimitKmH: Int?,
            distanceMeters: Double?,
            isDevMode: Boolean,
        ) = Unit

        override fun speakPhrase(phrase: String) {
            spokenPhrases.add(phrase)
        }

        override fun shutdown() {}
    }

    private lateinit var settingsManager: SettingsManager
    private lateinit var audioNotifier: MockAudioNotifier
    private lateinit var alertManager: TrafficAlertManager

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        settingsManager = SettingsManager(context)
        settingsManager.setTrafficAlertEnabled(true)
        audioNotifier = MockAudioNotifier()
        alertManager = TrafficAlertManager(settingsManager, audioNotifier)
    }

    private fun loc(lat: Double, lon: Double): Location =
        Location("test").apply {
            latitude = lat
            longitude = lon
            time = System.currentTimeMillis()
        }

    private fun eventAt(
        lat: Double,
        lon: Double,
        severity: TrafficSeverity = TrafficSeverity.Accident,
        id: String = "evt-1",
    ): TrafficEvent = TrafficEvent(
        roadRef = "A6",
        severity = severity,
        sourceId = id,
        latitude = lat,
        longitude = lon,
    )

    /** Point ~[meters] north of [fromLat]/[fromLon]. */
    private fun pointNorthOf(fromLat: Double, fromLon: Double, meters: Double): Pair<Double, Double> {
        val degLat = meters / 111_320.0
        return (fromLat + degLat) to fromLon
    }

    @Test
    fun gradualApproachFiresEachThresholdOnce() {
        val baseLat = 48.85
        val baseLon = 2.35
        val (evtLat, evtLon) = pointNorthOf(baseLat, baseLon, 2500.0)
        val event = eventAt(evtLat, evtLon, TrafficSeverity.Congestion)

        // Farther than 2 km — no alert.
        alertManager.evaluateAndAlert(loc(baseLat, baseLon), listOf(event))
        assertEquals(0, audioNotifier.spokenPhrases.size)

        // Cross 2 km.
        val (lat2k, lon2k) = pointNorthOf(evtLat, evtLon, -1900.0)
        alertManager.evaluateAndAlert(loc(lat2k, lon2k), listOf(event))
        assertEquals(1, audioNotifier.spokenPhrases.size)
        assertTrue(audioNotifier.spokenPhrases.last().contains("bouchon"))
        assertTrue(audioNotifier.spokenPhrases.last().contains("2km"))

        // Same band — no re-fire.
        alertManager.evaluateAndAlert(loc(lat2k, lon2k), listOf(event))
        assertEquals(1, audioNotifier.spokenPhrases.size)

        // Cross 1 km.
        val (lat1k, lon1k) = pointNorthOf(evtLat, evtLon, -900.0)
        alertManager.evaluateAndAlert(loc(lat1k, lon1k), listOf(event))
        assertEquals(2, audioNotifier.spokenPhrases.size)
        assertTrue(audioNotifier.spokenPhrases.last().contains("1km"))

        // Cross 500 m.
        val (lat500, lon500) = pointNorthOf(evtLat, evtLon, -400.0)
        alertManager.evaluateAndAlert(loc(lat500, lon500), listOf(event))
        assertEquals(3, audioNotifier.spokenPhrases.size)
        assertTrue(audioNotifier.spokenPhrases.last().contains("500m"))

        // Cross 200 m.
        val (lat200, lon200) = pointNorthOf(evtLat, evtLon, -150.0)
        alertManager.evaluateAndAlert(loc(lat200, lon200), listOf(event))
        assertEquals(4, audioNotifier.spokenPhrases.size)
        assertTrue(audioNotifier.spokenPhrases.last().contains("200m"))
        assertEquals(4, audioNotifier.okBeepsCount)
    }

    @Test
    fun coldStartMidApproachSpeaksOnlyClosestReachedThreshold() {
        val baseLat = 48.85
        val baseLon = 2.35
        val (evtLat, evtLon) = pointNorthOf(baseLat, baseLon, 400.0)
        val event = eventAt(evtLat, evtLon, TrafficSeverity.Accident)

        alertManager.evaluateAndAlert(loc(baseLat, baseLon), listOf(event))
        assertEquals(1, audioNotifier.spokenPhrases.size)
        assertTrue(
            audioNotifier.spokenPhrases.single().endsWith("accident dans 500m"),
            "got: ${audioNotifier.spokenPhrases.single()}",
        )
        // Farther bands marked fired without speaking.
        assertEquals(
            setOf(2000.0, 1000.0, 500.0),
            alertManager.getFiredThresholds("evt-1"),
        )
    }

    @Test
    fun disabledSettingClearsAndSuppressesAlerts() {
        val baseLat = 48.85
        val baseLon = 2.35
        val (evtLat, evtLon) = pointNorthOf(baseLat, baseLon, 400.0)
        val event = eventAt(evtLat, evtLon, TrafficSeverity.Closure)

        settingsManager.setTrafficAlertEnabled(false)
        alertManager.evaluateAndAlert(loc(baseLat, baseLon), listOf(event))
        assertEquals(0, audioNotifier.spokenPhrases.size)
        assertTrue(alertManager.getKnownEventIds().isEmpty())
    }

    @Test
    fun skipsNonAlertableSeverity() {
        val baseLat = 48.85
        val baseLon = 2.35
        val (evtLat, evtLon) = pointNorthOf(baseLat, baseLon, 400.0)
        val event = eventAt(evtLat, evtLon, TrafficSeverity.Normal)

        alertManager.evaluateAndAlert(loc(baseLat, baseLon), listOf(event))
        assertEquals(0, audioNotifier.spokenPhrases.size)
    }

    @Test
    fun distanceHelperMatchesHaversine() {
        val baseLat = 48.85
        val baseLon = 2.35
        val (evtLat, evtLon) = pointNorthOf(baseLat, baseLon, 500.0)
        val meters = haversineKm(baseLat, baseLon, evtLat, evtLon) * 1000.0
        assertTrue(meters in 480.0..520.0)
    }
}
