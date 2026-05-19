package fr.geoking.gaston.poi

import fr.geoking.gaston.AppSettings
import fr.geoking.gaston.PoiProviderSelectionMode
import fr.geoking.gaston.poi.EnergyFilterMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PoiFetchCacheTest {

    @Test
    fun buildPoiFetchKey_ignoresCategoryOrder() {
        val providers = setOf(PoiProviderType.DataGouv, PoiProviderType.Overpass)
        assertEquals(
            buildPoiFetchKey(providers),
            buildPoiFetchKey(setOf(PoiProviderType.Overpass, PoiProviderType.DataGouv)),
        )
    }

    @Test
    fun resolveCategoriesToFetch_includesGasAndIrveInFuelMode() {
        val settings = AppSettings(mapEnergyMode = EnergyFilterMode.Fuel)
        val categories = resolveCategoriesToFetch(settings)
        assertTrue(PoiCategory.Gas in categories)
        assertTrue(PoiCategory.Irve in categories)
    }

    @Test
    fun resolveCategoriesToFetch_includesWarmParkingAfterOtherMode() {
        val settings = AppSettings(
            mapEnergyMode = EnergyFilterMode.Fuel,
            cacheWarmAmenityTypes = setOf("parking"),
        )
        val categories = resolveCategoriesToFetch(settings)
        assertTrue(PoiCategory.Parking in categories)
    }

    @Test
    fun resolveCategoriesToFetch_otherModeOnlySelectedAmenities() {
        val settings = AppSettings(
            poiProviderSelectionMode = PoiProviderSelectionMode.Manual,
            selectedPoiProviders = setOf(PoiProviderType.Overpass),
            selectedOverpassAmenityTypes = setOf("parking"),
        )
        val categories = resolveCategoriesToFetch(settings)
        assertEquals(setOf(PoiCategory.Parking), categories)
        assertFalse(PoiCategory.Gas in categories)
    }

    @Test
    fun computePoiCoverage_fullyCoveredWhenProvidersAndCategoriesLoaded() {
        val region = LoadedPoiRegion(
            centerLat = 48.85,
            centerLng = 2.35,
            maxRadiusKmLoaded = 10,
            loadedAtMs = System.currentTimeMillis(),
            loadedProviders = setOf(PoiProviderType.DataGouv, PoiProviderType.Overpass),
            loadedCategories = setOf(PoiCategory.Gas, PoiCategory.Irve, PoiCategory.Parking),
        )
        val nowMs = System.currentTimeMillis()
        val coverage = computePoiCoverage(
            regions = listOf(region),
            centerLat = 48.85,
            centerLng = 2.35,
            requiredRadiusKm = 10,
            providers = setOf(PoiProviderType.DataGouv),
            categoriesToFetch = setOf(PoiCategory.Gas, PoiCategory.Irve),
            nowMs = nowMs,
        )
        assertTrue(coverage.fullyCovered)
        assertTrue(coverage.missingProviders.isEmpty())
        assertTrue(coverage.missingCategories.isEmpty())
    }

    @Test
    fun computePoiCoverage_missingGasRequiresIncrementalFetch() {
        val region = LoadedPoiRegion(
            centerLat = 48.85,
            centerLng = 2.35,
            maxRadiusKmLoaded = 10,
            loadedAtMs = System.currentTimeMillis(),
            loadedProviders = setOf(PoiProviderType.Overpass),
            loadedCategories = setOf(PoiCategory.Parking),
        )
        val coverage = computePoiCoverage(
            regions = listOf(region),
            centerLat = 48.85,
            centerLng = 2.35,
            requiredRadiusKm = 10,
            providers = setOf(PoiProviderType.DataGouv, PoiProviderType.Overpass),
            categoriesToFetch = setOf(PoiCategory.Gas, PoiCategory.Irve, PoiCategory.Parking),
            nowMs = System.currentTimeMillis(),
        )
        assertFalse(coverage.fullyCovered)
        assertTrue(PoiCategory.Gas in coverage.missingCategories)
        assertTrue(PoiProviderType.DataGouv in coverage.missingProviders)
    }

    @Test
    fun providersForIncrementalFetch_reQueriesOverpassForMissingAmenities() {
        val providers = providersForIncrementalFetch(
            allProviders = setOf(PoiProviderType.DataGouv, PoiProviderType.Overpass),
            missingProviders = emptySet(),
            missingCategories = setOf(PoiCategory.Parking),
        )
        assertEquals(setOf(PoiProviderType.Overpass), providers)
    }

    @Test
    fun buildPoiFetchKey_stableAcrossEnergyMode() {
        val fuelProviders = setOf(PoiProviderType.DataGouv, PoiProviderType.DataGouvElec)
        assertEquals(buildPoiFetchKey(fuelProviders), buildPoiFetchKey(fuelProviders))
    }

    @Test
    fun invalidateRegionCoverageOnProviderSetChange_clearsRegionsWhenProvidersChange() {
        val regions = mutableListOf(
            LoadedPoiRegion(
                centerLat = 48.85,
                centerLng = 2.35,
                maxRadiusKmLoaded = 10,
                loadedAtMs = 1L,
                loadedProviders = setOf(PoiProviderType.Routex, PoiProviderType.Overpass),
            ),
        )
        val routexKey = buildPoiFetchKey(setOf(PoiProviderType.Routex, PoiProviderType.Overpass))
        val dataGouvKey = buildPoiFetchKey(setOf(PoiProviderType.DataGouv, PoiProviderType.Overpass))

        val afterRoutex = invalidateRegionCoverageOnProviderSetChange(
            providers = setOf(PoiProviderType.Routex, PoiProviderType.Overpass),
            lastKey = null,
            loadedRegions = regions,
        )
        assertEquals(routexKey, afterRoutex)
        assertTrue(regions.isEmpty())

        regions += LoadedPoiRegion(
            centerLat = 48.85,
            centerLng = 2.35,
            maxRadiusKmLoaded = 10,
            loadedAtMs = 2L,
            loadedProviders = setOf(PoiProviderType.Routex, PoiProviderType.Overpass),
        )
        val afterSwitchToFuel = invalidateRegionCoverageOnProviderSetChange(
            providers = setOf(PoiProviderType.DataGouv, PoiProviderType.Overpass),
            lastKey = afterRoutex,
            loadedRegions = regions,
        )
        assertEquals(dataGouvKey, afterSwitchToFuel)
        assertTrue(regions.isEmpty())
    }

    @Test
    fun invalidateRegionCoverageOnProviderSetChange_keepsRegionsWhenProvidersUnchanged() {
        val regions = mutableListOf(
            LoadedPoiRegion(
                centerLat = 48.85,
                centerLng = 2.35,
                maxRadiusKmLoaded = 10,
                loadedAtMs = 1L,
                loadedProviders = setOf(PoiProviderType.DataGouv),
            ),
        )
        val key = buildPoiFetchKey(setOf(PoiProviderType.DataGouv, PoiProviderType.Overpass))
        val result = invalidateRegionCoverageOnProviderSetChange(
            providers = setOf(PoiProviderType.DataGouv, PoiProviderType.Overpass),
            lastKey = key,
            loadedRegions = regions,
        )
        assertEquals(key, result)
        assertEquals(1, regions.size)
    }

    @Test
    fun cacheTtl_parkingLastsLongerThanFuel() {
        assertTrue(POI_CACHE_TTL_AMENITY_MS > POI_CACHE_TTL_ENERGY_MS)
        assertEquals(3L * 24 * 60 * 60 * 1000L, POI_CACHE_TTL_AMENITY_MS)
    }

    @Test
    fun categoryCacheStillFresh_parkingWithinThreeDays() {
        val nowMs = 1_000_000_000_000L
        val region = LoadedPoiRegion(
            centerLat = 48.0,
            centerLng = 2.0,
            maxRadiusKmLoaded = 10,
            loadedAtMs = nowMs - POI_CACHE_TTL_AMENITY_MS + 60_000L,
            loadedCategories = setOf(PoiCategory.Parking),
            categoryLoadedAtMs = mapOf(PoiCategory.Parking to nowMs - POI_CACHE_TTL_AMENITY_MS + 60_000L),
        )
        assertTrue(categoryCacheStillFresh(PoiCategory.Parking, region, nowMs))
    }

    @Test
    fun categoryCacheStillFresh_gasExpiredAfterTwelveHours() {
        val nowMs = 1_000_000_000_000L
        val loadedAt = nowMs - POI_CACHE_TTL_ENERGY_MS - 1L
        val region = LoadedPoiRegion(
            centerLat = 48.0,
            centerLng = 2.0,
            maxRadiusKmLoaded = 10,
            loadedAtMs = loadedAt,
            loadedCategories = setOf(PoiCategory.Gas),
            categoryLoadedAtMs = mapOf(PoiCategory.Gas to loadedAt),
        )
        assertFalse(categoryCacheStillFresh(PoiCategory.Gas, region, nowMs))
    }

    @Test
    fun computePoiCoverage_staleGasRequiresRefetchWhileParkingFresh() {
        val nowMs = 1_000_000_000_000_000L
        val gasLoadedAt = nowMs - POI_CACHE_TTL_ENERGY_MS - 1L
        val parkingLoadedAt = nowMs - 60_000L
        val region = LoadedPoiRegion(
            centerLat = 48.85,
            centerLng = 2.35,
            maxRadiusKmLoaded = 10,
            loadedAtMs = parkingLoadedAt,
            loadedProviders = setOf(PoiProviderType.DataGouv, PoiProviderType.Overpass),
            loadedCategories = setOf(PoiCategory.Gas, PoiCategory.Parking),
            categoryLoadedAtMs = mapOf(
                PoiCategory.Gas to gasLoadedAt,
                PoiCategory.Parking to parkingLoadedAt,
            ),
        )
        val coverage = computePoiCoverage(
            regions = listOf(region),
            centerLat = 48.85,
            centerLng = 2.35,
            requiredRadiusKm = 10,
            providers = setOf(PoiProviderType.DataGouv, PoiProviderType.Overpass),
            categoriesToFetch = setOf(PoiCategory.Gas, PoiCategory.Parking),
            nowMs = nowMs,
        )
        assertFalse(coverage.fullyCovered)
        assertTrue(PoiCategory.Gas in coverage.missingCategories)
        assertFalse(PoiCategory.Parking in coverage.missingCategories)
    }

    @Test
    fun mergeLoadedRegion_unionsProvidersAndCategories() {
        val existing = LoadedPoiRegion(
            centerLat = 48.0,
            centerLng = 2.0,
            maxRadiusKmLoaded = 10,
            loadedAtMs = 1L,
            loadedProviders = setOf(PoiProviderType.Overpass),
            loadedCategories = setOf(PoiCategory.Parking),
        )
        val merged = mergeLoadedRegion(
            existing = existing,
            centerLat = 48.0,
            centerLng = 2.0,
            requiredRadiusKm = 12,
            loadedAtMs = 2L,
            fetchedProviders = setOf(PoiProviderType.DataGouv),
            fetchedCategories = setOf(PoiCategory.Gas),
        )
        assertEquals(12, merged.maxRadiusKmLoaded)
        assertTrue(PoiProviderType.Overpass in merged.loadedProviders)
        assertTrue(PoiProviderType.DataGouv in merged.loadedProviders)
        assertTrue(PoiCategory.Parking in merged.loadedCategories)
        assertTrue(PoiCategory.Gas in merged.loadedCategories)
        assertEquals(2L, merged.categoryLoadedAtMs[PoiCategory.Gas])
        assertEquals(1L, merged.categoryLoadedAtMs[PoiCategory.Parking])
    }
}
