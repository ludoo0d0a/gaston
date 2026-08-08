package fr.geoking.gaston.poi

import fr.geoking.gaston.shared.location.haversineKm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CircularGeofencedPoiProviderTest {

    private class MockPoiProvider : PoiProvider {
        var searchCalled = false
        var searchResultCalled = false
        var getGasStationsCalled = false
        var clearCacheCalled = false

        override fun supportedCategories(): Set<PoiCategory> = setOf(PoiCategory.CaravanSite)

        override suspend fun search(request: PoiSearchRequest): List<Poi> {
            searchCalled = true
            return listOf(
                Poi(
                    id = "mock:1",
                    name = "Mock Station",
                    address = "Mock Address",
                    latitude = request.latitude,
                    longitude = request.longitude,
                    poiCategory = PoiCategory.CaravanSite
                )
            )
        }

        override suspend fun searchResult(request: PoiSearchRequest): PoiSearchResult {
            searchResultCalled = true
            return PoiSearchResult(pois = search(request))
        }

        override suspend fun getGasStations(
            latitude: Double,
            longitude: Double,
            viewport: MapViewport?
        ): List<Poi> {
            getGasStationsCalled = true
            return listOf(
                Poi(
                    id = "mock:gas:1",
                    name = "Mock Gas Station",
                    address = "Mock Gas Address",
                    latitude = latitude,
                    longitude = longitude,
                    poiCategory = PoiCategory.Gas
                )
            )
        }

        override suspend fun clearCache() {
            clearCacheCalled = true
        }
    }

    private val centerLat = 43.5795
    private val centerLon = 3.3684
    private val radiusKm = 80.0

    @Test
    fun testDelegatesSupportedCategories() {
        val mockProvider = MockPoiProvider()
        val geofenced = CircularGeofencedPoiProvider(mockProvider, centerLat, centerLon, radiusKm)
        assertEquals(setOf(PoiCategory.CaravanSite), geofenced.supportedCategories())
    }

    @Test
    fun testSearch_insideGeofence_delegatesToUnderlying() = runBlocking {
        val mockProvider = MockPoiProvider()
        val geofenced = CircularGeofencedPoiProvider(mockProvider, centerLat, centerLon, radiusKm)

        // Coordinates inside Montpellier (overlap with geofence)
        val request = PoiSearchRequest(
            latitude = 43.611,
            longitude = 3.877,
            categories = setOf(PoiCategory.CaravanSite)
        )

        val results = geofenced.search(request)

        assertTrue(mockProvider.searchCalled)
        assertEquals(1, results.size)
        assertEquals("mock:1", results[0].id)
    }

    @Test
    fun testSearch_outsideGeofence_shortCircuits() = runBlocking {
        val mockProvider = MockPoiProvider()
        val geofenced = CircularGeofencedPoiProvider(mockProvider, centerLat, centerLon, radiusKm)

        // Coordinates in Paris (far from Hérault)
        val request = PoiSearchRequest(
            latitude = 48.8566,
            longitude = 2.3522,
            categories = setOf(PoiCategory.CaravanSite)
        )

        val results = geofenced.search(request)

        assertTrue(!mockProvider.searchCalled)
        assertTrue(results.isEmpty())
    }

    @Test
    fun testSearchResult_insideGeofence_delegatesToUnderlying() = runBlocking {
        val mockProvider = MockPoiProvider()
        val geofenced = CircularGeofencedPoiProvider(mockProvider, centerLat, centerLon, radiusKm)

        // Coordinates inside Montpellier
        val request = PoiSearchRequest(
            latitude = 43.611,
            longitude = 3.877,
            categories = setOf(PoiCategory.CaravanSite)
        )

        val result = geofenced.searchResult(request)

        assertTrue(mockProvider.searchResultCalled)
        assertEquals(1, result.pois.size)
    }

    @Test
    fun testSearchResult_outsideGeofence_shortCircuits() = runBlocking {
        val mockProvider = MockPoiProvider()
        val geofenced = CircularGeofencedPoiProvider(mockProvider, centerLat, centerLon, radiusKm)

        // Coordinates in Lille (far from Hérault)
        val request = PoiSearchRequest(
            latitude = 50.6292,
            longitude = 3.0573,
            categories = setOf(PoiCategory.CaravanSite)
        )

        val result = geofenced.searchResult(request)

        assertTrue(!mockProvider.searchResultCalled)
        assertTrue(result.pois.isEmpty())
    }

    @Test
    fun testSearchFlow_insideGeofence_delegatesToUnderlying() = runBlocking {
        val mockProvider = MockPoiProvider()
        val geofenced = CircularGeofencedPoiProvider(mockProvider, centerLat, centerLon, radiusKm)

        // Coordinates inside Montpellier
        val request = PoiSearchRequest(
            latitude = 43.611,
            longitude = 3.877,
            categories = setOf(PoiCategory.CaravanSite)
        )

        val result = geofenced.searchFlow(request).first()

        assertTrue(mockProvider.searchResultCalled)
        assertEquals(1, result.pois.size)
    }

    @Test
    fun testSearchFlow_outsideGeofence_shortCircuits() = runBlocking {
        val mockProvider = MockPoiProvider()
        val geofenced = CircularGeofencedPoiProvider(mockProvider, centerLat, centerLon, radiusKm)

        // Coordinates in Strasbourg (far from Hérault)
        val request = PoiSearchRequest(
            latitude = 48.5734,
            longitude = 7.7521,
            categories = setOf(PoiCategory.CaravanSite)
        )

        val result = geofenced.searchFlow(request).first()

        assertTrue(!mockProvider.searchResultCalled)
        assertTrue(result.pois.isEmpty())
    }

    @Test
    fun testGetGasStations_insideGeofence_delegatesToUnderlying() = runBlocking {
        val mockProvider = MockPoiProvider()
        val geofenced = CircularGeofencedPoiProvider(mockProvider, centerLat, centerLon, radiusKm)

        val results = geofenced.getGasStations(43.611, 3.877, null)

        assertTrue(mockProvider.getGasStationsCalled)
        assertEquals(1, results.size)
        assertEquals("mock:gas:1", results[0].id)
    }

    @Test
    fun testGetGasStations_outsideGeofence_shortCircuits() = runBlocking {
        val mockProvider = MockPoiProvider()
        val geofenced = CircularGeofencedPoiProvider(mockProvider, centerLat, centerLon, radiusKm)

        val results = geofenced.getGasStations(48.8566, 2.3522, null)

        assertTrue(!mockProvider.getGasStationsCalled)
        assertTrue(results.isEmpty())
    }

    @Test
    fun testClearCache_delegatesToUnderlying() = runBlocking {
        val mockProvider = MockPoiProvider()
        val geofenced = CircularGeofencedPoiProvider(mockProvider, centerLat, centerLon, radiusKm)

        geofenced.clearCache()

        assertTrue(mockProvider.clearCacheCalled)
    }
}
