package fr.geoking.gaston

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsManagerDebugBarTest {

    @Test
    fun appSettings_defaultDebugBarDisabled() {
        val settings = AppSettings()
        assertFalse(settings.debugBarEnabled)
    }

    @Test
    fun appSettings_toggleDebugBarEnabled() {
        val settings = AppSettings(debugBarEnabled = false)
        val updated = settings.copy(debugBarEnabled = true)
        assertTrue(updated.debugBarEnabled)
    }
}
