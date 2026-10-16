package fr.geoking.gaston.auto

import android.graphics.Rect
import androidx.car.app.SurfaceContainer

/** A screen that owns the Android Auto map Surface and must give it back before another map screen replaces it. */
interface MapSurfaceOwner {
    /** Idempotent: stops rendering and releases everything bound to the Surface (threads, bitmaps, VirtualDisplay). */
    fun releaseSurface()
}

/**
 * Last Surface and visible area delivered by the AA host. When the map mode is swapped the host does not
 * necessarily re-deliver the Surface to the new screen, so the new screen re-attaches from this cache
 * instead of waiting for a camera change (e.g. a zoom) to trigger the first frame.
 */
object AaSurfaceSession {
    @Volatile var container: SurfaceContainer? = null
    @Volatile var visibleArea: Rect? = null

    fun usableContainer(): SurfaceContainer? =
        container?.takeIf { it.surface?.isValid == true }
}
