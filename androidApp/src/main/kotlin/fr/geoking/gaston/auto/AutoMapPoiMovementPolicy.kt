package fr.geoking.gaston.auto

import fr.geoking.gaston.poi.LoadedPoiRegion
import fr.geoking.gaston.poi.findCoveringRegion
import fr.geoking.gaston.shared.location.approxDistanceKm

/** Minimum movement before adding a trail point or redrawing UI from cached POIs. */
const val TRAIL_AND_REDRAW_MIN_KM = 0.05

/** Minimum movement from the last query center before a re-query is considered. */
const val REQUERY_MIN_KM = 0.5

fun movedKm(from: Pair<Double, Double>, to: Pair<Double, Double>): Double =
    approxDistanceKm(from.first, from.second, to.first, to.second)

fun shouldAddTrailPoint(last: Pair<Double, Double>?, new: Pair<Double, Double>): Boolean =
    last == null || movedKm(last, new) >= TRAIL_AND_REDRAW_MIN_KM

fun shouldRedrawFromMovement(last: Pair<Double, Double>?, new: Pair<Double, Double>): Boolean =
    shouldAddTrailPoint(last, new)

fun isViewportCoveredBy(
    lastCoverage: LoadedPoiRegion?,
    newLat: Double,
    newLon: Double,
    requiredRadiusKm: Int,
): Boolean {
    if (lastCoverage == null) return false
    return findCoveringRegion(
        regions = listOf(lastCoverage),
        centerLat = newLat,
        centerLng = newLon,
        requiredRadiusKm = requiredRadiusKm,
    ) != null
}

/**
 * Whether a POI search should run after the vehicle moves (follow mode).
 * Requires at least [REQUERY_MIN_KM] from the last query center and an uncovered viewport.
 */
fun shouldRequeryPois(
    lastCoverage: LoadedPoiRegion?,
    newLat: Double,
    newLon: Double,
    requiredRadiusKm: Int,
): Boolean {
    if (lastCoverage == null) return true
    val movedFromQueryCenter = movedKm(lastCoverage.centerLat to lastCoverage.centerLng, newLat to newLon)
    if (movedFromQueryCenter < REQUERY_MIN_KM) return false
    return !isViewportCoveredBy(lastCoverage, newLat, newLon, requiredRadiusKm)
}

/** Whether a POI search should run when the visible map grows (e.g. zoom out) without a distance threshold. */
fun shouldRequeryForViewportChange(
    lastCoverage: LoadedPoiRegion?,
    newLat: Double,
    newLon: Double,
    requiredRadiusKm: Int,
): Boolean = !isViewportCoveredBy(lastCoverage, newLat, newLon, requiredRadiusKm)
