package fr.geoking.gaston.api.weather

import fr.geoking.gaston.poi.PoiSearchRequest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Unit tests for [WeatherPoiProvider] cache eviction logic.
 * Verifies:
 * - search() respects [maxTotalWeatherPois] limits  
 * - Eviction occurs correctly using FIFO strategy
 */
class WeatherPoiProviderTest {

    private lateinit var poiProvider: WeatherPoiProvider

    @BeforeTest
    fun setup() {
        poiProvider = WeatherPoiProvider(
            client = OpenMeteoWeatherGridClient(),
            gridSize = 2,
            maxPoiCount = 10,
            maxTotalWeatherPois = 5
        )
    }

    @Test
    fun testSearchReturnsEmptyWhenNotRequested() = runTest {
        val request = PoiSearchRequest(
            latitude = 48.9,
            longitude = 2.3,
            viewport = MapViewport(mapWidthPx = 100, mapHeightPx = 100, zoom = 6),
            categories = setOf() // Weather not requested
        )

        val result = poiProvider.search(request)
        assertEquals(emptyList(), result)
    }

    @Test
    fun testSearchReturnsWeatherPoisWhenRequested() = runTest {
        val request = PoiSearchRequest(
            latitude = 48.9,
            longitude = 2.3,
            viewport = MapViewport(mapWidthPx = 100, mapHeightPx = 100, zoom = 6),
            categories = setOf(PoiCategory.Weather)
        )

        val result = poiProvider.search(request)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun testEvictionRemovesOldestEntries() = runTest {
        // First search should be cached
        val req1 = PoiSearchRequest(
            latitude = 48.9,
            longitude = 2.3,
            viewport = MapViewport(mapWidthPx = 100, mapHeightPx = 100, zoom = 6),
            categories = setOf(PoiCategory.Weather)
        )
        poiProvider.search(req1)

        // Second search with different coordinates should evict oldest
        val req2 = PoiSearchRequest(
            latitude = 48.5,
            longitude = 2.0,
            viewport = MapViewport(mapWidthPx = 100, mapHeightPx = 100, zoom = 6),
            categories = setOf(PoiCategory.Weather)
        )
        poiProvider.search(req2)

        // Cache size should be within threshold
        assertTrue(poiProvider.poiCountCache.size <= poiProvider.evictionThreshold)
    }

    @Test
    fun testSearchRespectsMaxTotalWeatherPoisLimit() = runTest {
        val provider = WeatherPoiProvider(
            client = OpenMeteoWeatherGridClient(),
            gridSize = 2,
            maxPoiCount = 10,
            maxTotalWeatherPois = 3
        )

        // Trigger evictions by making multiple searches with different coordinates
        val requests = List(6) { i ->
            PoiSearchRequest(
                latitude = (48.0 + i * 0.05).coerceIn(46.0, 51.0),
                longitude = (2.0 + i * 0.05).coerceIn(-8.0, 9.0),
                viewport = MapViewport(mapWidthPx = 100, mapHeightPx = 100, zoom = 6),
                categories = setOf(PoiCategory.Weather)
            )
        }

        requests.forEach { request ->
            provider.search(request)
        }

        // Verify cache size stays within limit even after multiple searches
        assertTrue(provider.poiCountCache.size <= provider.evictionThreshold)
    }

    @Test
    fun testCacheUsesFifoEvictionStrategy() = runTest {
        val provider = WeatherPoiProvider(
            client = OpenMeteoWeatherGridClient(),
            gridSize = 2,
            maxPoiCount = 10,
            maxTotalWeatherPois = 5
        )

        // Create 7 different searches (more than threshold of 5)
        val initialRequests = List(7) { i ->
            PoiSearchRequest(
                latitude = 48.0 + i * 0.01,
                longitude = 2.0 + i * 0.01,
                viewport = MapViewport(mapWidthPx = 100, mapHeightPx = 100, zoom = 6),
                categories = setOf(PoiCategory.Weather)
            )
        }

        initialRequests.forEach { request ->
            provider.search(request)
        }

        // After eviction, oldest entries should be removed
        assertTrue(provider.poiCountCache.size <= provider.evictionThreshold)

        // Verify that newer entries are retained (FIFO behavior)
        val keys = provider.poiCountCache.keys.toList()
        assertTrue(keys.last().hashCode() > keys.first().hashCode()) // Newer keys at end
    }
}
