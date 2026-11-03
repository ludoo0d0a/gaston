package fr.geoking.gaston.auto.map.mapsforge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fr.geoking.gaston.MapTheme
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Parses every bundled theme with Mapsforge's own RenderThemeHandler (schema + semantic errors fail here). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MapsforgeThemeParseTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyThemeParsesWithMapsforge() {
        AndroidGraphicFactory.createInstance(context.applicationContext as android.app.Application)
        for (theme in MapTheme.entries) {
            val xml = MapsforgeMapTheme.resolveXmlRenderTheme(context, theme)
            val future = RenderThemeFuture(AndroidGraphicFactory.INSTANCE, xml, DisplayModel())
            future.run()
            assertNotNull("theme=${theme.name} failed to parse", future.get())
        }
    }
}
