package fr.geoking.gaston.radar

import android.location.Location
import fr.geoking.gaston.SettingsManager
import fr.geoking.gaston.aac.DangerZoneAlertCopy
import fr.geoking.gaston.aac.DangerZoneFactory
import fr.geoking.gaston.aac.RoadNetworkClass
import fr.geoking.gaston.shared.logging.DebugLogStore
import fr.geoking.gaston.shared.logging.RadarDetectionLog
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
        DebugLogStore.clearAll()
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
        var lastEntryLat: Double? = null
        var lastEntryLon: Double? = null
        var lastNearLat: Double? = null
        var lastNearLon: Double? = null

        override fun showDangerZoneNotification(
            speedLimitKmH: Int?,
            latitude: Double?,
            longitude: Double?,
        ) {
            entryNotificationCount++
            lastEntrySpeedLimit = speedLimitKmH
            lastEntryLat = latitude
            lastEntryLon = longitude
        }

        override fun showNearRadarNotification(
            speedLimitKmH: Int?,
            latitude: Double?,
            longitude: Double?,
        ) {
            nearNotificationCount++
            lastNearSpeedLimit = speedLimitKmH
            lastNearLat = latitude
            lastNearLon = longitude
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
        kotlin.test.assertEquals(48.8566, mockNotification.lastEntryLat)
        kotlin.test.assertEquals(2.3522, mockNotification.lastEntryLon)
        kotlin.test.assertEquals(0, mockNotification.nearNotificationCount)
    }

    @Test
    fun processWidePathPostsHunAndAudioOnZoneEntry() {
        // Coordinator wiring: notificationHelper set → HUN + beep + TTS + HUD (like border HUN).
        val context = RuntimeEnvironment.getApplication()
        val mockNotification = MockNotificationHelper(context)
        val manager = DangerZoneAlertManager(settingsManager, audioNotifier, mockNotification)
        val zone = DangerZoneFactory.fromSpeedControlPoint(
            id = "z-phone",
            latitude = 48.8566,
            longitude = 2.3522,
            speedLimitKmH = 50,
            source = "test",
            roadClassOverride = RoadNetworkClass.Urban,
        )
        // Inside urban zone (~200 m) but outside near-radar (100 m) → entry HUN + audio.
        val loc = Location("test").apply {
            latitude = 48.8566 + 0.0018
            longitude = 2.3522
            time = System.currentTimeMillis()
        }
        manager.evaluateAndAlert(loc, listOf(zone))
        assertTrue(manager.hudState.value.active)
        kotlin.test.assertEquals(50, manager.hudState.value.speedLimitKmH)
        assertTrue(audioNotifier.spokenPhrases.isNotEmpty())
        kotlin.test.assertEquals(1, audioNotifier.okBeepsCount)
        kotlin.test.assertEquals(1, mockNotification.entryNotificationCount)
        kotlin.test.assertEquals(50, mockNotification.lastEntrySpeedLimit)
    }

    @Test
    fun triggerTestAlertPlaysAudioShowsHudAndHun() {
        val context = RuntimeEnvironment.getApplication()
        val mockNotification = MockNotificationHelper(context)
        val manager = DangerZoneAlertManager(settingsManager, audioNotifier, mockNotification)
        manager.triggerTestAlert(100)
        assertTrue(manager.hudState.value.active)
        kotlin.test.assertEquals(100, manager.hudState.value.speedLimitKmH)
        kotlin.test.assertEquals(1, audioNotifier.okBeepsCount)
        assertTrue(audioNotifier.spokenPhrases.isNotEmpty())
        kotlin.test.assertEquals(1, mockNotification.entryNotificationCount)
        kotlin.test.assertEquals(100, mockNotification.lastEntrySpeedLimit)
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
        kotlin.test.assertEquals(48.8566, mockNotification.lastNearLat)
        kotlin.test.assertEquals(2.3522, mockNotification.lastNearLon)
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

    @Test
    fun logsDetectionConditionsWhenDebugBarEnabled() {
        settingsManager.saveSettings(
            settingsManager.settings.value.copy(debugBarEnabled = true),
        )
        val zone = DangerZoneFactory.fromSpeedControlPoint(
            id = "z-log",
            latitude = 48.8566,
            longitude = 2.3522,
            speedLimitKmH = 50,
            source = "test",
            roadClassOverride = RoadNetworkClass.Urban,
        )
        val loc = Location("test").apply {
            latitude = 48.8566 + 0.0018
            longitude = 2.3522
            time = System.currentTimeMillis()
        }
        alertManager.evaluateAndAlert(loc, listOf(zone))

        val radarLogs = DebugLogStore.logs.value.filter { it.host == RadarDetectionLog.HOST }
        assertTrue(radarLogs.isNotEmpty())
        val entry = radarLogs.first { it.method == "ENTRY" }
        assertEquals("ENTRY", entry.method)
        assertTrue(entry.url.contains("vma=50"))
        assertTrue(entry.url.contains("radius=300m"))
        assertTrue(entry.url.contains("road=Urban"))
        assertTrue(entry.url.contains("dist="))
    }

    @Test
    fun doesNotLogDetectionWhenDebugDisabled() {
        settingsManager.saveSettings(
            settingsManager.settings.value.copy(
                debugBarEnabled = false,
                debugLoggingEnabled = false,
            ),
        )
        val zone = DangerZoneFactory.fromSpeedControlPoint(
            id = "z-silent",
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
        assertTrue(DebugLogStore.logs.value.none { it.host == RadarDetectionLog.HOST })
    }
}
