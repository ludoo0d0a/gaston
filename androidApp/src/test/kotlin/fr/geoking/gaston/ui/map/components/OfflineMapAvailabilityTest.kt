package fr.geoking.gaston.ui.map.components

import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.CarMapMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Plain JVM tests — no Robolectric needed — for the offline-file-availability logic behind Protomaps/Mapsforge. */
class OfflineMapAvailabilityTest {

    @Test
    fun nonOfflineModeIsAlwaysAvailable() {
        val settings = AppSettings(carMapMode = CarMapMode.MapLibreMapSnapshotter, offlinePmtilesPath = null)
        assertTrue(OfflineMapAvailability.isOfflineFileAvailable(settings))
    }

    @Test
    fun mapsforgeWithNullPathIsUnavailable() {
        val settings = AppSettings(carMapMode = CarMapMode.Mapsforge, offlineMapsforgePath = null)
        assertFalse(OfflineMapAvailability.isOfflineFileAvailable(settings))
    }

    @Test
    fun mapsforgeWithRealFileIsAvailable() {
        val file = File.createTempFile("test", ".map").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val settings = AppSettings(carMapMode = CarMapMode.Mapsforge, offlineMapsforgePath = file.absolutePath)
        assertTrue(OfflineMapAvailability.isOfflineFileAvailable(settings))
    }
}
