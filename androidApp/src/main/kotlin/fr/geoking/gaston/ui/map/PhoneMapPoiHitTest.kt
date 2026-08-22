package fr.geoking.gaston.ui.map

import fr.geoking.gaston.poi.Poi

/**
 * Screen-space hit testing for POI markers on phone map surfaces (MapLibre).
 * Markers use a bottom-center anchor (pin tip on the coordinate).
 */
internal object PhoneMapPoiHitTest {

    /** Minimum tap radius in px — comfortable finger target on phone. */
    const val MIN_HIT_RADIUS_PX = 56f

    fun hitRadiusPx(markerWidthPx: Int): Float =
        maxOf(markerWidthPx / 2f, MIN_HIT_RADIUS_PX)

    /**
     * POI whose marker bounds contain [screenX]/[screenY], or null if none match.
     * [poiToScreen] returns the screen position of each POI anchor (pin tip).
     */
    fun findNearestPoiAtScreenPoint(
        screenX: Float,
        screenY: Float,
        pois: List<Poi>,
        markerWidthPx: Int,
        poiToScreen: (Poi) -> Pair<Float, Float>,
    ): Poi? {
        if (pois.isEmpty()) return null
        val hitRadius = hitRadiusPx(markerWidthPx)
        val hitRadiusSq = hitRadius * hitRadius
        return pois.mapNotNull { poi ->
            val (px, py) = poiToScreen(poi)
            val dx = screenX - px
            // Bottom-center anchor: bias hit circle toward the icon body above the tip.
            val dy = screenY - (py - hitRadius)
            val distSq = dx * dx + dy * dy
            if (distSq <= hitRadiusSq) poi to distSq else null
        }
            .minByOrNull { it.second }
            ?.first
    }
}
