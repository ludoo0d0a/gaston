package fr.geoking.gaston.parked

import fr.geoking.gaston.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AaCarModeReceiverTest {

    @Test
    fun appSettings_defaultAutoParkOnAaDisconnectEnabled() {
        val settings = AppSettings()
        assertTrue(settings.autoParkOnAaDisconnectEnabled)
    }

    @Test
    fun appSettings_toggleAutoParkOnAaDisconnectEnabled() {
        val settings = AppSettings(autoParkOnAaDisconnectEnabled = true)
        val updated = settings.copy(autoParkOnAaDisconnectEnabled = false)
        assertFalse(updated.autoParkOnAaDisconnectEnabled)
    }
}
