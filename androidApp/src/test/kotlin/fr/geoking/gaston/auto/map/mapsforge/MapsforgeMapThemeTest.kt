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
    fun assetPath_groupsByOpenFreeMapStyleUrl() {
        assertEquals("mapsforge/themes/dark.xml", MapsforgeMapTheme.assetPathFor(MapTheme.Dark))
        assertEquals("mapsforge/themes/fiord.xml", MapsforgeMapTheme.assetPathFor(MapTheme.Fiord))
        assertEquals("mapsforge/themes/positron.xml", MapsforgeMapTheme.assetPathFor(MapTheme.Positron))

        assertEquals("mapsforge/themes/bright.xml", MapsforgeMapTheme.assetPathFor(MapTheme.Voyager))
        assertEquals("mapsforge/themes/bright.xml", MapsforgeMapTheme.assetPathFor(MapTheme.Bright))
        assertEquals("mapsforge/themes/bright.xml", MapsforgeMapTheme.assetPathFor(MapTheme.OsmFr))
        assertEquals("mapsforge/themes/bright.xml", MapsforgeMapTheme.assetPathFor(MapTheme.Hot))

        assertEquals("mapsforge/themes/liberty.xml", MapsforgeMapTheme.assetPathFor(MapTheme.Standard))
        assertEquals("mapsforge/themes/liberty.xml", MapsforgeMapTheme.assetPathFor(MapTheme.Liberty))
    }

    @Test
    fun uniqueStyleUrls_mapToFiveAssets() {
        val paths = MapTheme.entries.map { MapsforgeMapTheme.assetPathFor(it) }.toSet()
        assertEquals(
            setOf(
                "mapsforge/themes/dark.xml",
                "mapsforge/themes/bright.xml",
                "mapsforge/themes/liberty.xml",
                "mapsforge/themes/positron.xml",
                "mapsforge/themes/fiord.xml",
            ),
            paths,
        )
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
