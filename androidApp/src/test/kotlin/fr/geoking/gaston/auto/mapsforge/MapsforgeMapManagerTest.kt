package fr.geoking.gaston.auto.mapsforge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MapsforgeMapManagerTest {

    private lateinit var context: Context
    private lateinit var mapManager: MapsforgeMapManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mapManager = MapsforgeMapManager(context)
    }

    @Test
    fun testMapsforgePresetServersListNotEmpty() {
        val presets = MapsforgePresetServers.PRESET_MAPS
        assertTrue("Preset map list should not be empty", presets.isNotEmpty())
        assertTrue("Preset map URLs should end with .map", presets.all { it.url.endsWith(".map") })
        assertTrue(
            "France should expose per-region downloads",
            MapsforgePresetServers.FRANCE_REGION_MAPS.size >= 20,
        )
        assertTrue(
            "Every France region URL should live under europe/france/",
            MapsforgePresetServers.FRANCE_REGION_MAPS.all {
                it.url.contains("/europe/france/") && it.url.endsWith(".map")
            },
        )
    }

    @Test
    fun testActiveMapFileSelection() {
        val mapsDir = File(context.getExternalFilesDir(null), "mapsforge")
        if (!mapsDir.exists()) mapsDir.mkdirs()

        val map1 = File(mapsDir, "france.map").apply { writeText("dummy map data 1") }
        val map2 = File(mapsDir, "monaco.map").apply { writeText("dummy map data 2") }

        mapManager.refreshInstalledMaps()

        val installed = mapManager.installedMaps.value
        assertEquals(2, installed.size)

        mapManager.setActiveMapFile(map2)
        val active = mapManager.getActiveMapFile()
        assertNotNull(active)
        assertEquals("monaco.map", active?.name)

        // Cleanup
        map1.delete()
        map2.delete()
    }

    @Test
    fun testGetRecommendedPresetForLocation() {
        // Paris coordinates (Île-de-France)
        val parisPreset = MapsforgePresetServers.getRecommendedPreset(48.8566, 2.3522)
        assertEquals("Île-de-France (Paris)", parisPreset.name)

        // Lyon → Rhône-Alpes
        val lyonPreset = MapsforgePresetServers.getRecommendedPreset(45.7640, 4.8357)
        assertEquals("Rhône-Alpes", lyonPreset.name)

        // Marseille → PACA
        val marseillePreset = MapsforgePresetServers.getRecommendedPreset(43.2965, 5.3698)
        assertEquals("Provence-Alpes-Côte d'Azur", marseillePreset.name)

        // Berlin coordinates
        val berlinPreset = MapsforgePresetServers.getRecommendedPreset(52.5200, 13.4050)
        assertEquals("Germany (Berlin)", berlinPreset.name)

        // Unknown location outside preset bounds should fallback to default (Île-de-France)
        val fallbackPreset = MapsforgePresetServers.getRecommendedPreset(0.0, 0.0)
        assertEquals("Île-de-France (Paris)", fallbackPreset.name)
    }

    @Test
    fun testDownloadProgressPercent() {
        val progress = DownloadProgress(
            fileName = "france.map",
            bytesDownloaded = 500,
            totalBytes = 1000
        )
        assertEquals(50, progress.progressPercent)
    }
}
