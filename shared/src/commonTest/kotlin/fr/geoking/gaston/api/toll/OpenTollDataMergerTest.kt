package fr.geoking.gaston.api.toll

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenTollDataMergerTest {

    @Test
    fun merge_empty_returnsEmpty() {
        val merged = OpenTollDataMerger.merge(emptyList())
        assertTrue(merged.networks.isEmpty())
        assertTrue(merged.tollDescription.isEmpty())
    }

    @Test
    fun merge_concatenatesNetworksAndBooths() {
        val a = OpenTollDataModel(
            networks = listOf(OpenTollNetwork(networkName = "n1", tolls = listOf("A"))),
            tollDescription = mapOf("A" to TollBoothDescription(lat = "1", lon = "2", type = "close")),
            openTollPrice = emptyMap()
        )
        val b = OpenTollDataModel(
            networks = listOf(OpenTollNetwork(networkName = "n2", tolls = listOf("B"))),
            tollDescription = mapOf("B" to TollBoothDescription(lat = "3", lon = "4", type = "open")),
            openTollPrice = mapOf("B" to OpenTollPriceEntry(price = mapOf("class_1" to "1.0")))
        )
        val merged = OpenTollDataMerger.merge(listOf(a, b))
        assertEquals(2, merged.networks.size)
        assertEquals(2, merged.tollDescription.size)
        assertTrue(merged.openTollPrice.containsKey("B"))
    }
}
