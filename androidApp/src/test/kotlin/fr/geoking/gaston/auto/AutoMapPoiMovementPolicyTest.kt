package fr.geoking.gaston.auto

import fr.geoking.gaston.poi.LoadedPoiRegion
import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiProviderType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoMapPoiMovementPolicyTest {

    private val paris = 48.8566 to 2.3522

    private fun region(
        centerLat: Double = paris.first,
        centerLng: Double = paris.second,
        maxRadiusKm: Int = 10,
    ) = LoadedPoiRegion(
        centerLat = centerLat,
        centerLng = centerLng,
        maxRadiusKmLoaded = maxRadiusKm,
        loadedAtMs = 0L,
        loadedProviders = setOf(PoiProviderType.DataGouv),
        loadedCategories = setOf(PoiCategory.Gas),
    )

    @Test
    fun shouldAddTrailPoint_firstPoint() {
        assertTrue(shouldAddTrailPoint(null, paris))
    }

    @Test
    fun shouldAddTrailPoint_49m_noTrail() {
        val start = paris
        // ~49 m north
        val nearby = start.first + 0.00044 to start.second
        assertFalse(shouldAddTrailPoint(start, nearby))
    }

    @Test
    fun shouldAddTrailPoint_50m_addsTrail() {
        val start = paris
        // ~56 m north (> 50 m)
        val moved = start.first + 0.0005 to start.second
        assertTrue(shouldAddTrailPoint(start, moved))
    }

    @Test
    fun shouldRequeryPois_49m_neverRequeries() {
        val coverage = region()
        val nearby = paris.first + 0.00044 to paris.second
        assertFalse(shouldRequeryPois(coverage, nearby.first, nearby.second, requiredRadiusKm = 10))
    }

    @Test
    fun shouldRequeryPois_500m_inside10kmDisk_redrawOnly() {
        val coverage = region(maxRadiusKm = 10)
        // ~500 m north — still inside a 10 km disk centered at origin
        val moved = paris.first + 0.0045 to paris.second
        assertFalse(shouldRequeryPois(coverage, moved.first, moved.second, requiredRadiusKm = 10))
    }

    @Test
    fun shouldRequeryPois_500m_escapesCoverage_requeries() {
        val coverage = region(maxRadiusKm = 10)
        // ~9.6 km north — 500 m+ from center and new 10 km disk extends beyond old coverage
        val moved = paris.first + 0.086 to paris.second
        assertTrue(shouldRequeryPois(coverage, moved.first, moved.second, requiredRadiusKm = 10))
    }

    @Test
    fun shouldRequeryPois_noPriorCoverage_requeries() {
        assertTrue(shouldRequeryPois(null, paris.first, paris.second, requiredRadiusKm = 10))
    }

    @Test
    fun shouldRequeryForViewportChange_widerRadiusEscapesCoverage() {
        val coverage = region(maxRadiusKm = 10)
        assertTrue(
            shouldRequeryForViewportChange(
                lastCoverage = coverage,
                newLat = paris.first,
                newLon = paris.second,
                requiredRadiusKm = 20,
            )
        )
    }

    @Test
    fun shouldRequeryForViewportChange_stillCovered_noRequery() {
        val coverage = region(maxRadiusKm = 20)
        assertFalse(
            shouldRequeryForViewportChange(
                lastCoverage = coverage,
                newLat = paris.first,
                newLon = paris.second,
                requiredRadiusKm = 10,
            )
        )
    }

    @Test
    fun isViewportCoveredBy_matchesFindCoveringRegion() {
        val coverage = region(maxRadiusKm = 10)
        assertTrue(isViewportCoveredBy(coverage, paris.first, paris.second, requiredRadiusKm = 10))
        assertFalse(isViewportCoveredBy(coverage, paris.first, paris.second, requiredRadiusKm = 20))
        assertFalse(isViewportCoveredBy(null, paris.first, paris.second, requiredRadiusKm = 10))
    }
}
