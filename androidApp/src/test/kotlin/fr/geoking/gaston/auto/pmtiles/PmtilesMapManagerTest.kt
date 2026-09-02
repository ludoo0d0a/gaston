package fr.geoking.gaston.auto.pmtiles

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.geoking.gaston.auto.mapsforge.DownloadProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PmtilesMapManagerTest {

    private lateinit var context: Context
    private lateinit var mapManager: PmtilesMapManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mapManager = PmtilesMapManager(context)
    }

    @Test
    fun testPmtilesPresetServersListNotEmpty() {
        val presets = PmtilesPresetServers.PRESET_MAPS
        assertTrue("Preset map list should not be empty", presets.isNotEmpty())
        assertTrue("Preset map URLs should end with .pmtiles", presets.all { it.url.endsWith(".pmtiles") })
    }

    @Test
    fun testActiveMapFileSelection() {
        val pmtilesDir = File(context.getExternalFilesDir(null), "pmtiles")
        if (!pmtilesDir.exists()) pmtilesDir.mkdirs()

        val map1 = File(pmtilesDir, "france.pmtiles").apply { writeText("dummy pmtiles data 1") }
        val map2 = File(pmtilesDir, "monaco.pmtiles").apply { writeText("dummy pmtiles data 2") }

        mapManager.refreshInstalledMaps()

        val installed = mapManager.installedMaps.value
        assertEquals(2, installed.size)

        mapManager.setActiveMapFile(map2)
        val active = mapManager.getActiveMapFile()
        assertNotNull(active)
        assertEquals("monaco.pmtiles", active?.name)

        // Cleanup
        map1.delete()
        map2.delete()
    }

    @Test
    fun testGetRecommendedPresetForLocation() {
        // Paris coordinates (Île-de-France)
        val parisPreset = PmtilesPresetServers.getRecommendedPreset(48.8566, 2.3522)
        assertEquals("Île-de-France (Paris)", parisPreset.name)

        // Berlin coordinates
        val berlinPreset = PmtilesPresetServers.getRecommendedPreset(52.5200, 13.4050)
        assertEquals("Germany (Berlin)", berlinPreset.name)

        // Unknown location outside preset bounds should fallback to default (Île-de-France)
        val fallbackPreset = PmtilesPresetServers.getRecommendedPreset(0.0, 0.0)
        assertEquals("Île-de-France (Paris)", fallbackPreset.name)
    }

    @Test
    fun testDownloadProgressPercent() {
        val progress = DownloadProgress(
            fileName = "france.pmtiles",
            bytesDownloaded = 500,
            totalBytes = 1000
        )
        assertEquals(50, progress.progressPercent)
    }
}
