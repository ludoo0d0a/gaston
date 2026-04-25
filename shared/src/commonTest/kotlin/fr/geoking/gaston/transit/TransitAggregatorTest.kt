package fr.geoking.gaston.transit

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransitAggregatorTest {

    private fun makeProvider(region: TransitRegion, id: String, stops: List<TransitStop>): TransitProvider =
        object : TransitProvider {
            override val id: String = id
            override val region: TransitRegion = region
            override suspend fun getStopsNearby(lat: Double, lon: Double, radiusMeters: Int): List<TransitStop> = stops
            override suspend fun getDepartures(stopId: String): List<TransitDeparture> = emptyList()
        }

    @Test
    fun getStopsNearby_emptyContext_returnsEmpty() = runBlocking {
        val fr = makeProvider(TransitRegion.France, "fr", listOf(
            TransitStop("s1", "Stop 1", 48.85, 2.35, TransitMode.Bus, providerId = null)
        ))
        val selector = TransitApiSelector(listOf(fr))
        val aggregator = TransitAggregator(listOf(fr), selector)
        val result = aggregator.getStopsNearby(TransitQueryContext(), 500)
        assertTrue(result.isEmpty())
    }

    @Test
    fun getStopsNearby_userInFrance_returnsStopsFromFranceProvider() = runBlocking {
        val stop = TransitStop("s1", "Gare du Nord", 48.88, 2.36, TransitMode.Metro, providerId = null)
        val fr = makeProvider(TransitRegion.France, "fr_ratp", listOf(stop))
        val selector = TransitApiSelector(listOf(fr))
        val aggregator = TransitAggregator(listOf(fr), selector)
        val context = TransitQueryContext(userLat = 48.8566, userLon = 2.3522)
        val result = aggregator.getStopsNearby(context, 5000)
        assertEquals(1, result.size)
        assertEquals("s1", result.single().id)
        assertEquals("fr_ratp", result.single().providerId)
    }

    @Test
    fun getStopsNearby_twoRegions_mergesResults() = runBlocking {
        val fr = makeProvider(TransitRegion.France, "fr", listOf(
            TransitStop("s1", "Paris Stop", 48.85, 2.35, TransitMode.Bus, providerId = null)
        ))
        val be = makeProvider(TransitRegion.Belgium, "be", listOf(
            TransitStop("s2", "Brussels Stop", 50.85, 4.35, TransitMode.Bus, providerId = null)
        ))
        val selector = TransitApiSelector(listOf(fr, be))
        val aggregator = TransitAggregator(listOf(fr, be), selector)
        val context = TransitQueryContext(
            userLat = 48.8566, userLon = 2.3522,
            fromLat = 50.8503, fromLon = 4.3517
        )
        val result = aggregator.getStopsNearby(context, 10000)
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "s1" && it.providerId == "fr" })
        assertTrue(result.any { it.id == "s2" && it.providerId == "be" })
    }
}
