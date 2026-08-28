package fr.geoking.gaston.auto.maplibre

import fr.geoking.gaston.MapTheme
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoMapStyleUrlTest {

    @Test
    fun allMapThemes_useOpenFreeMapVectorStyleUrls() {
        for (theme in MapTheme.entries) {
            assertTrue(
                "theme=${theme.name} styleUrl=${theme.styleUrl}",
                theme.styleUrl.startsWith("https://tiles.openfreemap.org/styles/"),
            )
        }
    }
}
