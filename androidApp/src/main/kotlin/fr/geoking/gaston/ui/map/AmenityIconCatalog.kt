package fr.geoking.gaston.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.OutdoorGrill
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import fr.geoking.gaston.poi.PoiCategory

/**
 * Single source of truth for amenity icons. The filter chip selector
 * and the map waypoint markers ([PoiMarkerHelper]) read from this catalog so the user sees the
 * same glyph in both places.
 */
object AmenityIconCatalog {

    /** Glyph color used when the icon is rendered on a white circle map marker head. */
    data class Style(
        val icon: ImageVector,
        val glyphArgb: Int
    )

    /**
     * Mapping by OSM amenity id used by the filter chip selector (e.g. "toilets", "parking").
     */
    fun iconForOsmId(id: String): ImageVector = when (id) {
        "parking" -> Icons.Rounded.LocalParking
        "toilets" -> Icons.Rounded.Wc
        "drinking_water" -> Icons.Rounded.WaterDrop
        "truck_stop" -> Icons.Rounded.LocalShipping
        "camp_site" -> Icons.Rounded.Landscape
        // Requested: "Aire CC" should be a tree icon
        "caravan_site" -> Icons.Rounded.Forest
        "picnic_site" -> Icons.Rounded.OutdoorGrill
        "rest_area" -> Icons.Rounded.Forest
        "restaurant" -> Icons.Rounded.Restaurant
        "fast_food" -> Icons.Rounded.Fastfood
        "speed_camera" -> Icons.Rounded.Speed
        // Requested: viewpoint should be "jumelles" (closest Material icon)
        "viewpoint" -> Icons.Rounded.Visibility
        "post_box" -> Icons.Rounded.Mail
        "water" -> Icons.Rounded.Water
        "cafe" -> Icons.Rounded.LocalCafe
        "supermarket" -> Icons.Rounded.LocalGroceryStore
        else -> Icons.Rounded.LocationOn
    }

    /**
     * Mapping by [PoiCategory], used by the map marker renderer.
     * Returns null for categories that should keep their existing custom drawable (e.g. fuel/IRVE).
     */
    fun styleForCategory(category: PoiCategory?): Style? = when (category) {
        PoiCategory.Toilet -> Style(Icons.Rounded.Wc, 0xFF0EA5E9.toInt())
        PoiCategory.DrinkingWater -> Style(Icons.Rounded.WaterDrop, 0xFF06B6D4.toInt())
        PoiCategory.Camping -> Style(Icons.Rounded.Landscape, 0xFF22C55E.toInt())
        PoiCategory.CaravanSite -> Style(Icons.Rounded.Forest, 0xFFF59E0B.toInt())
        PoiCategory.PicnicSite -> Style(Icons.Rounded.OutdoorGrill, 0xFF84CC16.toInt())
        PoiCategory.TruckStop -> Style(Icons.Rounded.LocalShipping, 0xFF475569.toInt())
        PoiCategory.RestArea -> Style(Icons.Rounded.Forest, 0xFF16A34A.toInt())
        PoiCategory.Restaurant -> Style(Icons.Rounded.Restaurant, 0xFFDC2626.toInt())
        PoiCategory.FastFood -> Style(Icons.Rounded.Fastfood, 0xFFEA580C.toInt())
        PoiCategory.Radar -> Style(Icons.Rounded.Speed, 0xFF333333.toInt())
        PoiCategory.Parking -> Style(Icons.Rounded.LocalParking, 0xFF1D4ED8.toInt())
        PoiCategory.Viewpoint -> Style(Icons.Rounded.Visibility, 0xFF6366F1.toInt())
        PoiCategory.BatterySwap -> Style(Icons.Rounded.SwapHoriz, 0xFFF59E0B.toInt())
        PoiCategory.PostBox -> Style(Icons.Rounded.Mail, 0xFFF59E0B.toInt())
        PoiCategory.WaterBody -> Style(Icons.Rounded.Water, 0xFF0284C7.toInt())
        PoiCategory.Cafe -> Style(Icons.Rounded.LocalCafe, 0xFF78350F.toInt())
        PoiCategory.Supermarket -> Style(Icons.Rounded.LocalGroceryStore, 0xFF8B5CF6.toInt())
        else -> null
    }

    /** Marker head bitmap cache (white circle + tinted glyph), keyed by icon identity + bucketed size + color. */
    private val headBitmapCache = LruCache<String, Bitmap>(150)

    /**
     * Renders a circular map marker head: white disc with a thin gray edge, plus the Material
     * [Style.icon] glyph centered and tinted with [Style.glyphArgb]. Bitmaps are cached for reuse.
     */
    fun headBitmap(style: Style, sizePx: Int): Bitmap {
        val bucket = ((sizePx + 7) / 8) * 8
        val key = "${System.identityHashCode(style.icon)}_${style.glyphArgb}_${bucket}"
        synchronized(headBitmapCache) {
            headBitmapCache.get(key)?.let { return it }
        }
        val bitmap = Bitmap.createBitmap(bucket, bucket, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawCircleHead(canvas, bucket)
        // Inset matches the legacy layer-list (4.8dp out of 48dp = 10% on each side).
        val inset = bucket * 0.20f
        val glyphSize = bucket - inset
        canvas.save()
        canvas.translate(inset / 2f, inset / 2f)
        drawImageVector(canvas, style.icon, glyphSize, style.glyphArgb)
        canvas.restore()
        synchronized(headBitmapCache) {
            headBitmapCache.get(key)?.let { return it }
            headBitmapCache.put(key, bitmap)
        }
        return bitmap
    }

    fun clearCache() {
        synchronized(headBitmapCache) { headBitmapCache.evictAll() }
    }

    private fun drawCircleHead(canvas: Canvas, sizePx: Int) {
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val radius = sizePx / 2f - sizePx * 0.021f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFFFFFFF.toInt()
            isDither = true
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xFFDDDDDD.toInt()
            strokeWidth = (sizePx * 0.021f).coerceAtLeast(1f)
            isDither = true
        }
        canvas.drawCircle(cx, cy, radius, fill)
        canvas.drawCircle(cx, cy, radius, stroke)
    }

    /**
     * Walks [imageVector] and draws each [VectorPath] onto [canvas] using a single solid [fillArgb].
     * Group transforms (translate/rotate/scale/pivot) are honored. The vector's viewport is mapped
     * to a square area of [sizePx] starting at the current canvas origin.
     */
    private fun drawImageVector(
        canvas: Canvas,
        imageVector: ImageVector,
        sizePx: Float,
        fillArgb: Int
    ) {
        val sx = sizePx / imageVector.viewportWidth
        val sy = sizePx / imageVector.viewportHeight
        canvas.save()
        canvas.scale(sx, sy)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillArgb
            isDither = true
        }
        drawNode(canvas, imageVector.root, paint)
        canvas.restore()
    }

    private fun drawNode(canvas: Canvas, node: VectorNode, paint: Paint) {
        when (node) {
            is VectorGroup -> {
                canvas.save()
                // Compose VectorGroup applies transforms in this order: pivot translate, scale,
                // rotate, then translation. We mirror that here.
                if (node.translationX != 0f || node.translationY != 0f) {
                    canvas.translate(node.translationX, node.translationY)
                }
                if (node.pivotX != 0f || node.pivotY != 0f) {
                    canvas.translate(node.pivotX, node.pivotY)
                }
                if (node.scaleX != 1f || node.scaleY != 1f) {
                    canvas.scale(node.scaleX, node.scaleY)
                }
                if (node.rotation != 0f) {
                    canvas.rotate(node.rotation)
                }
                if (node.pivotX != 0f || node.pivotY != 0f) {
                    canvas.translate(-node.pivotX, -node.pivotY)
                }
                node.forEach { drawNode(canvas, it, paint) }
                canvas.restore()
            }
            is VectorPath -> {
                if (node.pathData.isEmpty()) return
                val composePath = node.pathData.toPath()
                canvas.drawPath(composePath.asAndroidPath(), paint)
            }
        }
    }
}
