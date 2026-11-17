package fr.geoking.gaston.poi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PoiRefreshDiffTest {

    private fun basePoi(
        fuelPrices: List<FuelPrice>? = null,
        irveDetails: IrveDetails? = null,
        isClosed: Boolean = false,
    ) = Poi(
        id = "1",
        name = "Station",
        address = "1 rue",
        latitude = 48.0,
        longitude = 2.0,
        fuelPrices = fuelPrices,
        irveDetails = irveDetails,
        isClosed = isClosed,
    )

    @Test
    fun detect_noChanges_whenIdentical() {
        val poi = basePoi(
            fuelPrices = listOf(FuelPrice("SP95", 1.5)),
            irveDetails = IrveDetails(availableConnectors = 2, totalConnectors = 4),
        )
        assertTrue(detectPoiRefreshChanges(poi, poi).isEmpty())
    }

    @Test
    fun detect_ignoresSourceTimestampOnly() {
        val before = basePoi(fuelPrices = listOf(FuelPrice("SP95", 1.5, updatedAt = "2024-01-01")))
        val after = before.copy(
            fuelPrices = listOf(FuelPrice("SP95", 1.5, updatedAt = "2024-02-01")),
            sourceUpdates = mapOf("DataGouv" to "2024-02-01"),
        )
        assertTrue(detectPoiRefreshChanges(before, after).isEmpty())
    }

    @Test
    fun detect_pricesAndStock() {
        val before = basePoi(fuelPrices = listOf(FuelPrice("SP95", 1.5)))
        val afterPrice = before.copy(fuelPrices = listOf(FuelPrice("SP95", 1.6)))
        assertEquals(listOf(PoiRefreshChange.Prices), detectPoiRefreshChanges(before, afterPrice))

        val afterStock = before.copy(fuelPrices = listOf(FuelPrice("SP95", 1.5, outOfStock = true)))
        assertEquals(listOf(PoiRefreshChange.Prices), detectPoiRefreshChanges(before, afterStock))
    }

    @Test
    fun detect_availabilityAndStatus() {
        val before = basePoi(
            irveDetails = IrveDetails(availableConnectors = 1, totalConnectors = 4),
            isClosed = false,
        )
        val after = before.copy(
            irveDetails = IrveDetails(availableConnectors = 0, totalConnectors = 4),
            isClosed = true,
        )
        assertEquals(
            listOf(PoiRefreshChange.Availability, PoiRefreshChange.Status),
            detectPoiRefreshChanges(before, after),
        )
    }
}
