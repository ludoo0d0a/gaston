package fr.geoking.gaston.transit

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransitApiSelectorTest {

    private fun makeProvider(region: TransitRegion, id: String): TransitProvider = object : TransitProvider {
        override val id: String = id
        override val region: TransitRegion = region
        override suspend fun getStopsNearby(lat: Double, lon: Double, radiusMeters: Int): List<TransitStop> = emptyList()
        override suspend fun getDepartures(stopId: String): List<TransitDeparture> = emptyList()
    }

    @Test
    fun select_emptyContext_returnsEmpty() {
        val fr = makeProvider(TransitRegion.France, "fr")
        val selector = TransitApiSelector(listOf(fr))
        val selected = selector.select(TransitQueryContext())
        assertTrue(selected.isEmpty())
    }

    @Test
    fun select_userInFrance_returnsOnlyFranceProvider() {
        val fr = makeProvider(TransitRegion.France, "fr")
        val be = makeProvider(TransitRegion.Belgium, "be")
        val lu = makeProvider(TransitRegion.Luxembourg, "lu")
        val selector = TransitApiSelector(listOf(fr, be, lu))
        val context = TransitQueryContext(userLat = 48.8566, userLon = 2.3522)
        val selected = selector.select(context)
        assertEquals(1, selected.size)
        assertEquals("fr", selected.single().id)
    }

    @Test
    fun select_fromParisToBrussels_returnsFranceAndBelgium() {
        val fr = makeProvider(TransitRegion.France, "fr")
        val be = makeProvider(TransitRegion.Belgium, "be")
        val lu = makeProvider(TransitRegion.Luxembourg, "lu")
        val selector = TransitApiSelector(listOf(fr, be, lu))
        val context = TransitQueryContext(
            fromLat = 48.8566, fromLon = 2.3522,
            toLat = 50.8503, toLon = 4.3517
        )
        val selected = selector.select(context)
        assertEquals(2, selected.size)
        assertTrue(selected.any { it.id == "fr" })
        assertTrue(selected.any { it.id == "be" })
    }

    @Test
    fun select_userInLuxembourg_returnsOnlyLuxembourgProvider() {
        val fr = makeProvider(TransitRegion.France, "fr")
        val lu = makeProvider(TransitRegion.Luxembourg, "lu")
        val selector = TransitApiSelector(listOf(fr, lu))
        val context = TransitQueryContext(userLat = 49.6116, userLon = 6.1319)
        val selected = selector.select(context)
        assertEquals(1, selected.size)
        assertEquals("lu", selected.single().id)
    }
}
