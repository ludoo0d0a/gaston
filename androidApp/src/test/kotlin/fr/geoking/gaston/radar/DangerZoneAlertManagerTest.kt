package fr.geoking.gaston.radar

import android.location.Location
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.aac.DangerZoneAlertCopy
import fr.geoking.gaston.aac.DangerZoneFactory
import fr.geoking.gaston.aac.RoadNetworkClass
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DangerZoneAlertManagerTest {

    private class MockAudioNotifier : RadarAudioNotifier {
        var okBeepsCount = 0
        var overSpeedCount = 0
        var spokenPhrases = mutableListOf<String>()
        var lastSpokenSpeedLimit: Int? = null

        override fun playOkSpeedBeeps() {
            okBeepsCount++
        }

        override fun playOverSpeedBeepsAndSpeak(speedLimitKmH: Int?) {
            overSpeedCount++
            lastSpokenSpeedLimit = speedLimitKmH
            spokenPhrases.add(DangerZoneAlertCopy.frZoneEntry(speedLimitKmH))
        }

        override fun speakDangerZone(speedLimitKmH: Int?) {
            lastSpokenSpeedLimit = speedLimitKmH
            spokenPhrases.add(DangerZoneAlertCopy.frZoneEntry(speedLimitKmH))
        }

        override fun shutdown() {}
    }

    private lateinit var settingsManager: SettingsManager
    private lateinit var audioNotifier: MockAudioNotifier
    private lateinit var alertManager: DangerZoneAlertManager

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        settingsManager = SettingsManager(context)
        settingsManager.setRadarWarningEnabled(true)
        audioNotifier = MockAudioNotifier()
        alertManager = DangerZoneAlertManager(settingsManager, audioNotifier)
    }

    private class MockNotificationHelper(context: android.content.Context) : fr.geoking.gaston.feature.notification.NotificationHelper(context) {
        var entryNotificationCount = 0
        var nearNotificationCount = 0
        var lastEntrySpeedLimit: Int? = null
        var lastNearSpeedLimit: Int? = null

        override fun showDangerZoneNotification(speedLimitKmH: Int?) {
            entryNotificationCount++
            lastEntrySpeedLimit = speedLimitKmH
        }

        override fun showNearRadarNotification(speedLimitKmH: Int?) {
            nearNotificationCount++
            lastNearSpeedLimit = speedLimitKmH
        }
    }

    @Test
    fun triggersNotificationOnZoneEntry() {
        val context = RuntimeEnvironment.getApplication()
        val mockNotification = MockNotificationHelper(context)
        val manager = DangerZoneAlertManager(settingsManager, audioNotifier, mockNotification)

        val zone = DangerZoneFactory.fromSpeedControlPoint(
            id = "z2",
            latitude = 48.8566,
            longitude = 2.3522,
            speedLimitKmH = 80,
            source = "test",
            roadClassOverride = RoadNetworkClass.Urban,
        )
        // ~200 m from center: inside urban zone (300 m) but outside near-radar (100 m)
        val loc = Location("test").apply {
            latitude = 48.8566 + 0.0018
            longitude = 2.3522
            time = System.currentTimeMillis()
        }
        manager.evaluateAndAlert(loc, listOf(zone))

        kotlin.test.assertEquals(1, mockNotification.entryNotificationCount)
        kotlin.test.assertEquals(80, mockNotification.lastEntrySpeedLimit)
        kotlin.test.assertEquals(0, mockNotification.nearNotificationCount)
    }

    @Test
    fun triggersNearRadarNotificationWithin100m() {
        val context = RuntimeEnvironment.getApplication()
        val mockNotification = MockNotificationHelper(context)
        val manager = DangerZoneAlertManager(settingsManager, audioNotifier, mockNotification)

        val zone = DangerZoneFactory.fromSpeedControlPoint(
            id = "z3",
            latitude = 48.8566,
            longitude = 2.3522,
            speedLimitKmH = 100,
            source = "test",
            roadClassOverride = RoadNetworkClass.Urban,
        )
        val loc = Location("test").apply {
            latitude = 48.8566
            longitude = 2.3522
            time = System.currentTimeMillis()
        }
        manager.evaluateAndAlert(loc, listOf(zone))

        kotlin.test.assertEquals(1, mockNotification.entryNotificationCount)
        kotlin.test.assertEquals(1, mockNotification.nearNotificationCount)
        kotlin.test.assertEquals(100, mockNotification.lastNearSpeedLimit)
        assertTrue(manager.getNearAlertedZoneIds().contains("z3"))
        // Entry + near each get beep + TTS on the same tick when already at the pin.
        kotlin.test.assertEquals(2, audioNotifier.okBeepsCount)
        kotlin.test.assertEquals(2, audioNotifier.spokenPhrases.size)

        // Dedup: second tick must not re-fire HUN or audio
        manager.evaluateAndAlert(loc, listOf(zone))
        kotlin.test.assertEquals(1, mockNotification.entryNotificationCount)
        kotlin.test.assertEquals(1, mockNotification.nearNotificationCount)
        kotlin.test.assertEquals(2, audioNotifier.okBeepsCount)
        kotlin.test.assertEquals(2, audioNotifier.spokenPhrases.size)
    }

    @Test
    fun nearRadarPlaysAudioAfterEntryAlreadyAlerted() {
        val zone = DangerZoneFactory.fromSpeedControlPoint(
            id = "z4",
            latitude = 48.8566,
            longitude = 2.3522,
            speedLimitKmH = 50,
            source = "test",
            roadClassOverride = RoadNetworkClass.Urban,
        )
        // Inside urban zone (~200 m) but outside near-radar (100 m)
        val far = Location("test").apply {
            latitude = 48.8566 + 0.0018
            longitude = 2.3522
            time = System.currentTimeMillis()
        }
        alertManager.evaluateAndAlert(far, listOf(zone))
        kotlin.test.assertEquals(1, audioNotifier.okBeepsCount)
        kotlin.test.assertEquals(1, audioNotifier.spokenPhrases.size)
        assertTrue(alertManager.getAlertedZoneIds().contains("z4"))
        assertFalse(alertManager.getNearAlertedZoneIds().contains("z4"))

        // Move closer (~50 m), stopped so trajectory filter does not drop the pin.
        val near = Location("test").apply {
            latitude = 48.8566 + 0.00045
            longitude = 2.3522
            time = System.currentTimeMillis() + 1_000
            speed = 0f
        }
        alertManager.evaluateAndAlert(near, listOf(zone))
        assertTrue(alertManager.getNearAlertedZoneIds().contains("z4"))
        kotlin.test.assertEquals(2, audioNotifier.okBeepsCount)
        kotlin.test.assertEquals(2, audioNotifier.spokenPhrases.size)
    }

    @Test
    fun alertsOnZoneEntryWithVma() {
        val zone = DangerZoneFactory.fromSpeedControlPoint(
            id = "z1",
            latitude = 48.8566,
            longitude = 2.3522,
            speedLimitKmH = 50,
            source = "test",
            roadClassOverride = RoadNetworkClass.Urban,
        )
        val loc = Location("test").apply {
            latitude = 48.8566
            longitude = 2.3522
            time = System.currentTimeMillis()
        }
        alertManager.evaluateAndAlert(loc, listOf(zone))
        assertTrue(alertManager.getAlertedZoneIds().contains("z1"))
        assertTrue(audioNotifier.spokenPhrases.isNotEmpty())
        assertTrue(alertManager.hudState.value.active)
    }

    @Test
    fun frAlertCopyNeverContainsRadarPlusDistance() {
        val phrases = listOf(
            DangerZoneAlertCopy.frZoneEntry(130),
            DangerZoneAlertCopy.frZoneEntry(null),
            DangerZoneAlertCopy.frZoneEntryShort(90),
        )
        val banned = Regex("""radar.{0,20}\d+\s*m""", RegexOption.IGNORE_CASE)
        phrases.forEach { phrase ->
            assertFalse(banned.containsMatchIn(phrase), "Banned pattern in: $phrase")
            assertFalse(phrase.contains("Attention, radar", ignoreCase = true))
        }
    }
}
