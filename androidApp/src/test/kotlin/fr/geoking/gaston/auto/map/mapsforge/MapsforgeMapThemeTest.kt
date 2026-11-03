package fr.geoking.gaston.auto.map.mapsforge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.geoking.gaston.MapTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mapsforge.map.rendertheme.InternalRenderTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapsforgeMapThemeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun assetPath_oneFilePerSettingsTheme() {
        val expected = mapOf(
            MapTheme.Dark to "dark_matter", MapTheme.Voyager to "voyager", MapTheme.Standard to "osm",
            MapTheme.Positron to "positron", MapTheme.Fiord to "fiord", MapTheme.OsmFr to "osm_fr",
            MapTheme.Hot to "hot", MapTheme.Bright to "bright", MapTheme.Liberty to "liberty",
        )
        assertEquals(MapTheme.entries.toSet(), expected.keys)
        for ((theme, name) in expected) {
            assertEquals("mapsforge/themes/$name.xml", MapsforgeMapTheme.assetPathFor(theme))
        }
        assertEquals(MapTheme.entries.size, MapTheme.entries.map { MapsforgeMapTheme.assetPathFor(it) }.toSet().size)
    }

    @Test
    fun resolveXmlRenderTheme_loadsBundledAssets() {
        for (theme in MapTheme.entries) {
            val xml = MapsforgeMapTheme.resolveXmlRenderTheme(context, theme)
            assertTrue(
                "theme=${theme.name} should resolve to AssetsXmlRenderTheme, got ${xml.javaClass.name}",
                xml is AssetsXmlRenderTheme,
            )
            assertNotEquals(InternalRenderTheme.DEFAULT, xml)
            xml.renderThemeAsStream.use { stream ->
                assertTrue(stream.readBytes().isNotEmpty())
            }
        }
    }

    @Test
    fun assetsXmlRenderTheme_reopensStream() {
        val theme = MapsforgeMapTheme.resolveXmlRenderTheme(context, MapTheme.Dark) as AssetsXmlRenderTheme
        val first = theme.renderThemeAsStream.use { it.readBytes() }
        val second = theme.renderThemeAsStream.use { it.readBytes() }
        assertTrue(first.isNotEmpty())
        assertEquals(first.size, second.size)
    }

    @Test
    fun resolveXmlRenderTheme_fallsBackToDefaultWhenAssetMissing() {
        val resolved = MapsforgeMapTheme.resolveXmlRenderTheme(
            assets = context.assets,
            theme = MapTheme.Dark,
            assetPathOverride = "mapsforge/themes/does-not-exist.xml",
        )
        assertEquals(InternalRenderTheme.DEFAULT, resolved)
    }
}
