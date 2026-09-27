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
