package fr.geoking.gaston.api.qualicharge

import fr.geoking.gaston.poi.PoiCategory
import fr.geoking.gaston.poi.PoiSearchRequest
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QualiChargeProviderTest {

    private val client = QualiChargeDynamiqueClient(HttpClient())
    private val provider = QualiChargeProvider(client)

    @Test
    fun shouldQuery_returnsTrueInFrance_andFalseOutside() {
        // Paris (France)
        assertTrue(provider.shouldQuery(48.8566, 2.3522))
        // Berlin (Germany)
        assertFalse(provider.shouldQuery(52.5200, 13.4050))
        // Madrid (Spain)
        assertFalse(provider.shouldQuery(40.4168, -3.7038))
    }

    @Test
    fun supportedCategories_returnsIrve() {
        assertEquals(setOf(PoiCategory.Irve), provider.supportedCategories())
    }

    @Test
    fun search_returnsEmptyListOutsideFrance() = runBlocking {
        val result = provider.search(
            PoiSearchRequest(
                latitude = 52.5200,
                longitude = 13.4050,
                categories = setOf(PoiCategory.Irve)
            )
        )
        assertTrue(result.isEmpty())
    }
}
