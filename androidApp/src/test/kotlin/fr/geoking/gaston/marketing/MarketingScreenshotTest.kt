package fr.geoking.gaston.marketing

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import com.google.android.gms.maps.model.LatLng
import fr.geoking.gaston.ui.map.loadStaticGoogleMapBitmap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Exports 1080×1920 PNGs (brand backdrop + rounded phone chassis) into playstore-assets/ and website/.
 *
 * Forces [LocalInspectionMode] so [fr.geoking.gaston.ui.map.InspectableGoogleMap] skips the Maps SDK.
 * Map screenshot preloads a Maps Static API image (network) with bundled asset fallback.
 *
 * Run: ./scripts/regenerate_screenshots.sh
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE],
    qualifiers = "fr-rFR-w360dp-h640dp-normal-long-port-xxhdpi",
    application = android.app.Application::class,
)
class MarketingScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repoRoot: File
        get() {
            val cwd = File(checkNotNull(System.getProperty("user.dir")))
            return if (File(cwd, "playstore-assets").isDirectory) cwd else checkNotNull(cwd.parentFile)
        }

    private fun export(name: String, content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = true
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                MarketingScreenshotFrame { content() }
            }
        }
        composeRule.waitForIdle()
        Thread.sleep(1200)
        val decor = composeRule.activity.window.decorView
        decor.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(TARGET_W, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(TARGET_H, android.view.View.MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, TARGET_W, TARGET_H)
        var bitmap = decor.drawToBitmap()
        if (bitmap.width != TARGET_W || bitmap.height != TARGET_H) {
            bitmap = Bitmap.createScaledBitmap(bitmap, TARGET_W, TARGET_H, true)
        }
        val playstore = File(repoRoot, "playstore-assets")
        val website = File(repoRoot, "website/assets/screenshots")
        playstore.mkdirs()
        website.mkdirs()
        FileOutputStream(File(playstore, name)).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        File(playstore, name).copyTo(File(website, name), overwrite = true)
    }

    @Test
    fun screenshot_1_map() {
        // Prefer live Static Maps tiles; falls back to assets/marketing/map-paris-static.png.
        val staticMap = loadStaticGoogleMapBitmap(
            context = composeRule.activity,
            center = LatLng(48.86735, 2.35300),
            zoom = 16f,
            allowNetwork = System.getenv("GASTON_STATIC_MAP_OFFLINE") != "1",
        )
        export("screenshot-1-map.png") { MarketingMapScreen(staticMapBitmap = staticMap) }
    }

    @Test
    fun screenshot_2_fuel_prices() {
        export("screenshot-2-fuel-prices.png") { MarketingFuelDetailScreen() }
    }

    @Test
    fun screenshot_3_ev_charging() {
        export("screenshot-3-ev-charging.png") { MarketingEvDetailScreen() }
    }

    @Test
    fun screenshot_4_filters() {
        export("screenshot-4-filters.png") { MarketingFiltersScreen() }
    }

    @Test
    fun screenshot_5_android_auto() {
        export("screenshot-5-android-auto.png") { MarketingAndroidAutoListScreen() }
    }

    private companion object {
        const val TARGET_W = 1080
        const val TARGET_H = 1920
    }
}
