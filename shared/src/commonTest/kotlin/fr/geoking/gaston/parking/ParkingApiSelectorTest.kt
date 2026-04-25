package fr.geoking.gaston.parking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParkingApiSelectorTest {

    private fun makeProvider(
        id: String,
        serves: Set<ParkingRegion>,
        fallbackOnly: Boolean = false,
        coversLat: Double? = null,
        coversLon: Double? = null
    ): ParkingProvider = object : ParkingProvider {
        override val id: String = id
        override fun covers(lat: Double, lon: Double): Boolean =
            if (coversLat != null && coversLon != null) lat == coversLat && lon == coversLon
            else true
        override fun servedRegions(): Set<ParkingRegion> = serves
        override fun isFallbackOnly(): Boolean = fallbackOnly
        override suspend fun getParkingNearby(lat: Double, lon: Double, radiusMeters: Int): List<ParkingPoi> = emptyList()
    }

    @Test
    fun select_emptyContext_returnsEmpty() {
        val p = makeProvider("p", setOf(ParkingRegion.Germany))
        val selector = ParkingApiSelector(listOf(p))
        val selected = selector.select(ParkingQueryContext())
        assertTrue(selected.isEmpty())
    }

    @Test
    fun select_userInGermany_selectsRegionSpecificNotFallback() {
        val live = makeProvider("liveparking", setOf(ParkingRegion.Germany), fallbackOnly = false, coversLat = 52.52, coversLon = 13.405)
        val osm = makeProvider("osm", emptySet(), fallbackOnly = true)
        val selector = ParkingApiSelector(listOf(live, osm))
        val context = ParkingQueryContext(userLat = 52.52, userLon = 13.405)
        val selected = selector.select(context)
        assertEquals(1, selected.size)
        assertEquals("liveparking", selected.single().id)
    }

    @Test
    fun select_userInFrance_selectsFallbackOnly() {
        val live = makeProvider("liveparking", setOf(ParkingRegion.Germany))
        val osm = makeProvider("osm", emptySet(), fallbackOnly = true)
        val selector = ParkingApiSelector(listOf(live, osm))
        val context = ParkingQueryContext(userLat = 48.8566, userLon = 2.3522)
        val selected = selector.select(context)
        assertEquals(1, selected.size)
        assertEquals("osm", selected.single().id)
    }

    @Test
    fun select_userInSwitzerland_selectsRegionSpecificNotFallback() {
        val parkApi = makeProvider("parkapi", setOf(ParkingRegion.Germany, ParkingRegion.Switzerland), coversLat = 47.3769, coversLon = 8.5417)
        val osm = makeProvider("osm", emptySet(), fallbackOnly = true)
        val selector = ParkingApiSelector(listOf(parkApi, osm))
        val context = ParkingQueryContext(userLat = 47.3769, userLon = 8.5417)
        val selected = selector.select(context)
        assertEquals(1, selected.size)
        assertEquals("parkapi", selected.single().id)
    }
}
