package fr.geoking.gaston.auto

import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.CarMapMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Plain JVM tests — no Robolectric needed — for the offline-file-availability logic behind Protomaps/Mapsforge. */
class OfflineMapAvailabilityTest {

    @Test
    fun protomapsWithNullPathIsUnavailable() {
        val settings = AppSettings(carMapMode = CarMapMode.Protomaps, offlinePmtilesPath = null)
        assertFalse(OfflineMapAvailability.isOfflineFileAvailable(settings))
    }

    @Test
    fun protomapsWithNonexistentPathIsUnavailable() {
        val settings = AppSettings(carMapMode = CarMapMode.Protomaps, offlinePmtilesPath = "/no/such/file.pmtiles")
        assertFalse(OfflineMapAvailability.isOfflineFileAvailable(settings))
    }

    @Test
    fun protomapsWithRealFileIsAvailable() {
        val file = File.createTempFile("test", ".pmtiles").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }
        val settings = AppSettings(carMapMode = CarMapMode.Protomaps, offlinePmtilesPath = file.absolutePath)
        assertTrue(OfflineMapAvailability.isOfflineFileAvailable(settings))
    }

    @Test
    fun mapsforgeWithNullPathIsUnavailable() {
        val settings = AppSettings(carMapMode = CarMapMode.Mapsforge, offlineMapsforgePath = null)
        assertFalse(OfflineMapAvailability.isOfflineFileAvailable(settings))
    }

    @Test
    fun mapsforgeWithNonexistentPathIsUnavailable() {
        val settings = AppSettings(carMapMode = CarMapMode.Mapsforge, offlineMapsforgePath = "/no/such/file.map")
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

    @Test
    fun nonOfflineModeIsAlwaysAvailable() {
        val settings = AppSettings(carMapMode = CarMapMode.MapLibre, offlinePmtilesPath = null, offlineMapsforgePath = null)
        assertTrue(OfflineMapAvailability.isOfflineFileAvailable(settings))
    }
}
