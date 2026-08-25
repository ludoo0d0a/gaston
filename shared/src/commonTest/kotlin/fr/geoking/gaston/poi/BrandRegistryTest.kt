package fr.geoking.gaston.poi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BrandRegistryTest {

    @Test
    fun testBeneluxElectricBrandsCategorization() {
        val beneluxBrands = listOf(
            "enovos", "superchargy", "sudstroum", "electris", "creos",
            "luminus", "eneco", "blue corner", "dats 24", "vandebron",
            "leaseplan", "greenflux", "sparki", "edi", "powerpass",
            "citypower", "strohm", "rebel mobility", "optimile", "opcharge",
            "vattenfall", "orange charging", "equans", "essent"
        )

        for (brandKey in beneluxBrands) {
            assertTrue(
                BrandRegistry.ELECTRIC_BRANDS.contains(brandKey),
                "BrandRegistry.ELECTRIC_BRANDS should contain '$brandKey'"
            )
        }
    }

    @Test
    fun testBeneluxBrandDetection() {
        val enovos = BrandRegistry.findBrand("Station Enovos Luxembourg", null)
        assertNotNull(enovos)
        assertEquals("Enovos", enovos)

        val superchargy = BrandRegistry.findBrand("Superchargy Fast Charging", null)
        assertNotNull(superchargy)
        assertEquals("Superchargy", superchargy)

        val luminus = BrandRegistry.findBrand("Luminus Charging Station", null)
        assertNotNull(luminus)
        assertEquals("Luminus", luminus)

        val eneco = BrandRegistry.findBrand("Eneco eMobility Hub", null)
        assertNotNull(eneco)
        assertEquals("Eneco", eneco)

        val blueCorner = BrandRegistry.findBrand("Blue Corner EV", null)
        assertNotNull(blueCorner)
        assertEquals("Blue Corner", blueCorner)

        val dats24 = BrandRegistry.findBrand("DATS 24 Charging", null)
        assertNotNull(dats24)
        assertEquals("DATS 24", dats24)

        val vattenfall = BrandRegistry.findBrand("Vattenfall InCharge", null)
        assertNotNull(vattenfall)
        assertEquals("Vattenfall", vattenfall)

        val essent = BrandRegistry.findBrand("Essent laadpaal", null)
        assertNotNull(essent)
        assertEquals("Essent", essent)
    }
}
