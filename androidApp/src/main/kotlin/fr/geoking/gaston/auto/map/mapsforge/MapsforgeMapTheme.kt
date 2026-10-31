package fr.geoking.gaston.auto.map.mapsforge

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import fr.geoking.gaston.MapTheme
import org.mapsforge.map.rendertheme.InternalRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderTheme
import org.mapsforge.map.rendertheme.XmlRenderThemeMenuCallback
import org.mapsforge.map.rendertheme.XmlThemeResourceProvider
import java.io.InputStream

/**
 * Maps each [MapTheme] (OpenFreeMap style list) to a bundled Mapsforge rendertheme XML.
 *
 * OpenFreeMap JSON cannot be converted to Mapsforge XML; these are approximate colour palettes
 * sharing the same names / grouping as [MapTheme.styleUrl].
 */
object MapsforgeMapTheme {
    private const val TAG = "MapsforgeMapTheme"
    private const val THEME_DIR = "mapsforge/themes"

    /** Asset path under `androidApp/src/main/assets/` for the given theme. */
    fun assetPathFor(theme: MapTheme): String = when (theme.styleUrl.substringAfterLast('/')) {
        "dark" -> "$THEME_DIR/dark.xml"
        "liberty" -> "$THEME_DIR/liberty.xml"
        "positron" -> "$THEME_DIR/positron.xml"
        "fiord" -> "$THEME_DIR/fiord.xml"
        else -> "$THEME_DIR/bright.xml" // bright (Voyager, Bright, OsmFr, Hot)
    }

    /**
     * Resolves a Mapsforge [XmlRenderTheme] for [theme].
     * Falls back to [InternalRenderTheme.DEFAULT] if the asset is missing or unreadable.
     */
    fun resolveXmlRenderTheme(context: Context, theme: MapTheme): XmlRenderTheme =
        resolveXmlRenderTheme(context.assets, theme)

    /**
     * Testable overload using an [AssetManager] directly.
     * @param assetPathOverride when non-null, used instead of [assetPathFor] (for missing-asset tests).
     */
    fun resolveXmlRenderTheme(
        assets: AssetManager,
        theme: MapTheme,
        assetPathOverride: String? = null,
    ): XmlRenderTheme {
        val path = assetPathOverride ?: assetPathFor(theme)
        return try {
            assets.open(path).close()
            AssetsXmlRenderTheme(
                assets = assets,
                assetPath = path,
                relativePathPrefix = "/assets/",
            )
        } catch (e: Exception) {
            Log.w(TAG, "Mapsforge theme asset missing or unreadable: $path — using DEFAULT", e)
            InternalRenderTheme.DEFAULT
        }
    }
}

/**
 * Loads a Mapsforge rendertheme from the APK assets, reopening the stream on each parse
 * (same pattern as [InternalRenderTheme], unlike one-shot [org.mapsforge.map.rendertheme.StreamRenderTheme]).
 */
class AssetsXmlRenderTheme(
    private val assets: AssetManager,
    private val assetPath: String,
    private val relativePathPrefix: String,
) : XmlRenderTheme {
    private var menuCallback: XmlRenderThemeMenuCallback? = null
    private var resourceProvider: XmlThemeResourceProvider? = null

    override fun getMenuCallback(): XmlRenderThemeMenuCallback? = menuCallback

    override fun getRelativePathPrefix(): String = relativePathPrefix

    override fun getRenderThemeAsStream(): InputStream = assets.open(assetPath)

    override fun getResourceProvider(): XmlThemeResourceProvider? = resourceProvider

    override fun setMenuCallback(menuCallback: XmlRenderThemeMenuCallback?) {
        this.menuCallback = menuCallback
    }

    override fun setResourceProvider(resourceProvider: XmlThemeResourceProvider?) {
        this.resourceProvider = resourceProvider
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AssetsXmlRenderTheme) return false
        return assetPath == other.assetPath && relativePathPrefix == other.relativePathPrefix
    }

    override fun hashCode(): Int = 31 * assetPath.hashCode() + relativePathPrefix.hashCode()
}
