package fr.geoking.gaston.auto

import android.graphics.Rect

/**
 * Screen-space focal point for Android Auto custom maps when following the car.
 *
 * In [MapOrientationMode.HeadingUp], the user cursor sits at 1/3 from the bottom of the
 * visible map area (more road ahead). Horizontally it stays centered in the visible
 * portion so a left/right list pane is respected.
 */
object AutoMapFollowFocalPoint {

    data class FocalPoint(val x: Double, val y: Double)

    /** MapLibre [org.maplibre.android.maps.MapLibreMap.setPadding] insets (px). */
    data class MapPadding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /**
     * Pixel position where the camera/user location should appear on the surface.
     *
     * @param visibleArea host-reported free map rect; null falls back to full surface
     * @param surfaceWidth surface width in px
     * @param surfaceHeight surface height in px
     * @param headingUp true when orientation is [MapOrientationMode.HeadingUp]
     */
    fun focalPointPx(
        visibleArea: Rect?,
        surfaceWidth: Int,
        surfaceHeight: Int,
        headingUp: Boolean,
    ): FocalPoint {
        val left = visibleArea?.left?.toDouble() ?: 0.0
        val top = visibleArea?.top?.toDouble() ?: 0.0
        val right = visibleArea?.right?.toDouble() ?: surfaceWidth.toDouble()
        val bottom = visibleArea?.bottom?.toDouble() ?: surfaceHeight.toDouble()
        val x = (left + right) / 2.0
        val y = if (headingUp) {
            top + (bottom - top) * 2.0 / 3.0
        } else {
            (top + bottom) / 2.0
        }
        return FocalPoint(x, y)
    }

    /**
     * Padding that places the MapLibre camera target at the same focal point as [focalPointPx].
     *
     * Horizontal insets match the visible area. In HeadingUp, extra top padding of
     * `visibleHeight / 3` shifts the target to 1/3 from the bottom.
     */
    fun mapLibrePadding(
        visibleArea: Rect?,
        surfaceWidth: Int,
        surfaceHeight: Int,
        headingUp: Boolean,
    ): MapPadding {
        val left = visibleArea?.left ?: 0
        val top = visibleArea?.top ?: 0
        val right = visibleArea?.right ?: surfaceWidth
        val bottom = visibleArea?.bottom ?: surfaceHeight
        val visibleHeight = (bottom - top).coerceAtLeast(0)
        val padTop = if (headingUp) top + visibleHeight / 3 else top
        val padRight = (surfaceWidth - right).coerceAtLeast(0)
        val padBottom = (surfaceHeight - bottom).coerceAtLeast(0)
        return MapPadding(
            left = left.coerceAtLeast(0),
            top = padTop.coerceAtLeast(0),
            right = padRight,
            bottom = padBottom,
        )
    }
}
