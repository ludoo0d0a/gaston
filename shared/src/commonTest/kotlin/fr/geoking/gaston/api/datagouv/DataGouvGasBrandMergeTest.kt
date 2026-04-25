package fr.geoking.gaston.api.datagouv

import fr.geoking.gaston.api.gas.GasApiStation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataGouvGasBrandMergeTest {

    @Test
    fun merge_fillsBrandWhenNearby() {
        val dg = DataGouvStation(
            id = "1",
            name = "S",
            address = "",
            latitude = 48.862,
            longitude = 2.4165,
            brand = null,
            prices = listOf(DataGouvPrice("E10", 1.99))
        )
        val gas = GasApiStation(
            id = "g1",
            name = "G",
            address = "",
            latitude = 48.86205,
            longitude = 2.4165,
            brand = "Total",
            prices = emptyList()
        )
        val out = DataGouvGasBrandMerge.mergeGasApiBrands(listOf(dg), listOf(gas))
        assertEquals("Total", out[0].brand)
    }

    @Test
    fun merge_skipsWhenTooFar() {
        val dg = DataGouvStation(
            id = "1",
            name = "S",
            address = "",
            latitude = 48.0,
            longitude = 2.0,
            brand = null,
            prices = emptyList()
        )
        val gas = GasApiStation(
            id = "g1",
            name = "G",
            address = "",
            latitude = 49.0,
            longitude = 3.0,
            brand = "Shell",
            prices = emptyList()
        )
        val out = DataGouvGasBrandMerge.mergeGasApiBrands(listOf(dg), listOf(gas))
        assertEquals(null, out[0].brand)
    }

    @Test
    fun haversine_parisShortDistance() {
        val m = DataGouvGasBrandMerge.haversineM(48.862, 2.4165, 48.86205, 2.4165)
        assertTrue(m < 20.0, "expected <20m got $m")
    }
}
