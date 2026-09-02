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
import java.io.FileOutputStream
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
        assertTrue(
            "Preset map URLs should be downloadable .pmtiles or .zip archives",
            presets.all {
                val path = it.url.substringBefore('?').lowercase()
                path.endsWith(".pmtiles") || path.endsWith(".zip")
            },
        )
        // Regional build.protomaps.com/<date>/<region>.pmtiles paths 404; presets must not use them.
        assertTrue(
            "Presets must not use non-existent Protomaps regional URLs",
            presets.none {
                it.url.matches(Regex("""https://build\.protomaps\.com/\d{8}/[^/]+\.pmtiles"""))
            },
        )
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

    @Test
    fun testExtractPmtilesFromZipArchive() {
        val pmtilesDir = File(context.getExternalFilesDir(null), "pmtiles")
        if (!pmtilesDir.exists()) pmtilesDir.mkdirs()

        val zipFile = File(pmtilesDir, "sample.zip")
        val target = File(pmtilesDir, "Sample Region.pmtiles")
        val payload = "PMTiles-dummy-bytes".toByteArray()
        java.util.zip.ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("region-pack/README.txt"))
            zos.write("ignore".toByteArray())
            zos.closeEntry()
            zos.putNextEntry(java.util.zip.ZipEntry("region-pack/sample.pmtiles"))
            zos.write(payload)
            zos.closeEntry()
        }

        assertTrue(mapManager.looksLikeZip(zipFile))
        mapManager.extractPmtilesFromZip(zipFile, target)
        assertTrue(target.isFile)
        assertEquals(payload.toString(Charsets.UTF_8), target.readText())

        target.delete()
        zipFile.delete()
    }
}
